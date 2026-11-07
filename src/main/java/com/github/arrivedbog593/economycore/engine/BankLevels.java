package com.github.arrivedbog593.economycore.engine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The levels a player's bank account climbs, as SkyBlock's: each holds more
 * than the one before, may pay interest on more of it, and costs something
 * to reach.
 * <p>
 * Every player starts at the first level, which costs nothing. Reaching the
 * next is paid from the bank itself, in credits, and with items from the
 * player's inventory; either may be left out, so a server can ask for
 * credits only, items only, or both. A level's credits never cost more than
 * the level before it holds, or nobody could ever afford it from the bank.
 * <p>
 * Items are kept as their ids, written as text, so this stays free of
 * Minecraft; the platform looks them up.
 *
 * @param levels from the first, each holding more than the last
 */
public record BankLevels(List<Level> levels) {

    /**
     * Some of one item.
     *
     * @param item  its id, such as {@code minecraft:gold_block}
     * @param count how many, at least one
     */
    public record ItemCost(String item, int count) {
        public ItemCost {
            Objects.requireNonNull(item, "item");
            if (count <= 0) {
                throw new IllegalArgumentException("Bad count for " + item + ": " + count);
            }
        }
    }

    /**
     * One level.
     *
     * @param name     lowercase, for its translation key
     * @param cap      the most its bank may hold
     * @param credits  what reaching it costs, from the bank; zero for nothing
     * @param items    what else reaching it costs, from the inventory
     * @param tranches the interest it pays, or null for the server's own tranches
     */
    public record Level(String name, long cap, long credits, List<ItemCost> items, InterestTranches tranches) {
        public Level {
            Objects.requireNonNull(name, "name");
            items = List.copyOf(Objects.requireNonNull(items, "items"));
            if (cap <= 0 || cap > CreditAccount.MAX_BALANCE || credits < 0) {
                throw new IllegalArgumentException("Bad level " + name + ": holds " + cap + ", costs " + credits);
            }
        }

        /** The line {@link #parse} reads it back from. */
        public String toLine() {
            return line(name, cap, credits, items, tranches == null ? "" : tranches.toText());
        }
    }

    /**
     * A level as a config line, from its parts, leaving out what is not
     * asked for: no cost, no items, the server's own tranches.
     *
     * @param tranches as {@link InterestTranches#toText}, or empty for the server's own
     */
    public static String line(String name, long cap, long credits, List<ItemCost> items, String tranches) {
        StringBuilder line = new StringBuilder(name).append("; cap=").append(cap);
        if (credits > 0) {
            line.append("; cost=").append(credits);
        }
        if (!items.isEmpty()) {
            line.append("; items=");
            for (int i = 0; i < items.size(); i++) {
                line.append(i == 0 ? "" : ",").append(items.get(i).item()).append('*').append(items.get(i).count());
            }
        }
        if (!tranches.isBlank()) {
            line.append("; tranches=").append(tranches.trim());
        }
        return line.toString();
    }

    /**
     * The level a player's account reached, as it is saved with them: by
     * name, so levels added, reordered or inserted around it leave them where
     * they were, and with what it held then, for if it is gone.
     *
     * @param name the level's name
     * @param cap  what it held when they reached it
     */
    public record Reached(String name, long cap) {
        public Reached {
            Objects.requireNonNull(name, "name");
        }
    }

    public BankLevels {
        levels = List.copyOf(Objects.requireNonNull(levels, "levels"));
        for (int i = 1; i < levels.size(); i++) {
            if (levels.get(i).cap() <= levels.get(i - 1).cap()) {
                throw new IllegalArgumentException("Each level must hold more than the last: " + levels);
            }
        }
    }

    /** A player's level by its place, kept in range if the list was shortened since. */
    public Level level(int index) {
        return levels.get(Math.clamp(index, 0, levels.size() - 1));
    }

    /** The place of a level kept in range, the same way {@link #level} keeps it. */
    public int clamp(int index) {
        return Math.clamp(index, 0, levels.size() - 1);
    }

    /** The level after this one, or empty at the top. */
    public Optional<Level> next(int index) {
        int next = clamp(index) + 1;
        return next < levels.size() ? Optional.of(levels.get(next)) : Optional.empty();
    }

    /**
     * Where a player's account stands: on the level they reached, by name;
     * if that level is gone, on the highest that holds no more than it did,
     * so taking a level away never lifts anybody; and on the first if they
     * never reached any, or nothing is that small.
     */
    public int placeOf(Reached reached) {
        if (reached == null) {
            return 0;
        }
        int named = indexOf(reached.name());
        if (named >= 0) {
            return named;
        }
        int place = 0;
        for (int i = 0; i < levels.size(); i++) {
            if (levels.get(i).cap() <= reached.cap()) {
                place = i;
            }
        }
        return place;
    }

    /** What to save for a player on this level. */
    public Reached reached(int index) {
        Level level = level(index);
        return new Reached(level.name(), level.cap());
    }

    /** The place of the level with this name, or -1. */
    public int indexOf(String name) {
        for (int i = 0; i < levels.size(); i++) {
            if (levels.get(i).name().equals(name)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Reads levels written one to a line, as
     * {@code "gold; cap=100000000; cost=5000000; items=minecraft:gold_block*1; tranches=10000000:2,20000000:1"}.
     * <p>
     * Only the name and {@code cap} are needed. {@code cost} and {@code items}
     * are what reaching the level costs, and are ignored on the first; items
     * go apart with commas, each with {@code *count} or one of it.
     * {@code tranches} are written as {@code interest.tranches} are, apart
     * with commas, and left out mean the server's own.
     * <p>
     * A cost above what the level before holds is brought down to it, and
     * said so in {@code warnings}.
     *
     * @return empty if any line cannot be read, or two share a name, or a
     *         level holds no more than the one before
     */
    public static Optional<BankLevels> parse(List<String> lines, List<String> warnings) {
        List<Level> read = new ArrayList<>();
        Set<String> names = new HashSet<>();
        try {
            for (String line : lines) {
                String[] parts = line.split(";");
                String name = parts[0].trim();
                if (!name.matches("[a-z0-9_]+") || !names.add(name)) {
                    return Optional.empty();
                }
                long cap = -1;
                long credits = 0;
                List<ItemCost> items = new ArrayList<>();
                InterestTranches tranches = null;
                for (int i = 1; i < parts.length; i++) {
                    String part = parts[i].trim();
                    if (part.isEmpty()) {
                        continue;
                    }
                    int equals = part.indexOf('=');
                    if (equals < 0) {
                        return Optional.empty();
                    }
                    String key = part.substring(0, equals).trim();
                    String value = part.substring(equals + 1).trim();
                    switch (key) {
                        case "cap" -> cap = number(value);
                        case "cost" -> credits = number(value);
                        case "items" -> {
                            for (String each : value.split(",")) {
                                if (!each.isBlank()) {
                                    items.add(item(each.trim()));
                                }
                            }
                        }
                        case "tranches" -> {
                            Optional<InterestTranches> parsed = InterestTranches.parse(List.of(value.split(",")));
                            if (parsed.isEmpty()) {
                                return Optional.empty();
                            }
                            tranches = parsed.get();
                        }
                        default -> {
                            return Optional.empty();
                        }
                    }
                }
                if (read.isEmpty()) {
                    // Everybody starts here: there is nothing to pay.
                    credits = 0;
                    items = List.of();
                } else {
                    long before = read.getLast().cap();
                    if (credits > before) {
                        warnings.add("Level " + name + " cost " + credits + " credits, more than "
                                + read.getLast().name() + " holds; it costs " + before + " instead.");
                        credits = before;
                    }
                }
                read.add(new Level(name, cap, credits, items, tranches));
            }
            return Optional.of(new BankLevels(read));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static long number(String value) {
        return Long.parseLong(value.replace("_", "").replace(",", ""));
    }

    private static ItemCost item(String text) {
        int star = text.lastIndexOf('*');
        if (star < 0) {
            return new ItemCost(text, 1);
        }
        return new ItemCost(text.substring(0, star).trim(), Integer.parseInt(text.substring(star + 1).trim()));
    }
}
