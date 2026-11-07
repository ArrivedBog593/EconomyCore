package com.github.arrivedbog593.economycore.platform;

import com.github.arrivedbog593.economycore.engine.BankLevels;
import com.github.arrivedbog593.economycore.platform.menu.AdminBankMenu;
import com.github.arrivedbog593.economycore.platform.menu.BankMenu;
import com.github.arrivedbog593.economycore.platform.network.AdminBankStatePayload;
import com.github.arrivedbog593.economycore.platform.network.BankStatePayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Changing the bank's settings from inside the game, for its settings
 * window.
 * <p>
 * What the window changes is kept with the world, not written to the server
 * config: in single player every world shares that file, and taking every
 * level out of one world must not leave the next with none. Until a world
 * changes a setting it follows the config, which so holds the defaults each
 * new world starts from, and a server owner editing the file still sees it
 * take effect on a world nobody changed from inside.
 * {@code /economycore banksettings useconfig} forgets a world's changes. What dying
 * costs was world data already.
 * <p>
 * Every change refreshes every window that shows any of it: the settings of
 * any other admin looking, and the bank of every player who has it open.
 */
public final class BankSettings {

    /** The most hours apart interest may be paid, as the config allows. */
    public static final int MAX_PERIOD_HOURS = 24 * 365;

    /**
     * What came of a change to the levels.
     *
     * @param done whether it was made
     * @param note why not, or something the admin should know about it; may be null
     */
    public record Outcome(boolean done, @Nullable Component note) {
        static Outcome refused(Component why) {
            return new Outcome(false, why);
        }
    }

    private BankSettings() {
    }

    /** The world running now, or null with none, as on a client at the title screen. */
    private static @Nullable EconomyData world() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : EconomyData.get(server);
    }

    /** Whether this world pays interest: its own choice, or the config's until it made one. */
    public static boolean interestOn() {
        EconomyData world = world();
        Boolean own = world == null ? null : world.interestOn();
        if (own != null) {
            return own;
        }
        return EconomyConfig.SPEC.isLoaded() && EconomyConfig.INTEREST_ENABLED.get();
    }

    /** How many hours apart this world pays interest: its own, or the config's. */
    public static int periodHours() {
        EconomyData world = world();
        int own = world == null ? 0 : world.interestPeriodHours();
        return own > 0 ? own : EconomyConfig.INTEREST_PERIOD_HOURS.get();
    }

    /** This world's bank levels, one to a line: its own, or the config's until it changed any. */
    public static List<String> levelLines() {
        EconomyData world = world();
        List<String> own = world == null ? null : world.bankLevels();
        if (own != null) {
            return own;
        }
        return EconomyConfig.SPEC.isLoaded() ? List.copyOf(EconomyConfig.BANK_LEVELS.get()) : List.of();
    }

    public static void setInterest(MinecraftServer server, boolean on) {
        EconomyData.get(server).setInterestOn(on);
        refresh(server);
    }

    /** @return false if the hours are out of range, and nothing changes */
    public static boolean setPeriodHours(MinecraftServer server, int hours) {
        if (hours < 1 || hours > MAX_PERIOD_HOURS) {
            return false;
        }
        EconomyData.get(server).setInterestPeriodHours(hours);
        refresh(server);
        return true;
    }

    /** Forgets what this world changed, so it follows the server config again. */
    public static void useConfig(MinecraftServer server) {
        EconomyData.get(server).forgetBankSettings();
        refresh(server);
    }

    /** @return false if the percent is out of range, and nothing changes */
    public static boolean setDeathLoss(MinecraftServer server, int percent) {
        if (percent < 0 || percent > DeathLoss.MAX_PERCENT) {
            return false;
        }
        EconomyData.get(server).setDeathLossPercent(percent);
        refresh(server);
        return true;
    }

    public static void setDeathLossCombatOnly(MinecraftServer server, boolean combatOnly) {
        EconomyData.get(server).setDeathLossCombatOnly(combatOnly);
        refresh(server);
    }

    /**
     * Replaces a level with the one a line describes, or adds it; either way
     * it goes where its cap puts it among the rest.
     * <p>
     * Checked as the whole list it would make, so it cannot leave the config
     * unreadable: two levels with one name, or two that hold the same, are
     * refused. Players on a level that was renamed are moved to its new name,
     * so the rename leaves them where they were.
     *
     * @param index the level to replace, or -1 to add a new one
     * @return whether it was saved; if not, why, and if so, anything the admin should know
     */
    public static Outcome saveLevel(MinecraftServer server, int index, String line) {
        Optional<BankLevels> current = currentLevels();
        if (current.isEmpty()) {
            return Outcome.refused(Component.translatable("economycore.admin.bank.unreadable"));
        }
        List<String> lines = new ArrayList<>(current.get().levels().stream().map(BankLevels.Level::toLine).toList());
        if (index < -1 || index >= lines.size()) {
            return Outcome.refused(Component.translatable("economycore.admin.bank.no_such_level"));
        }
        // Alone first, so a level that is wrong in itself says so rather than
        // being blamed on its neighbours. Read alone it is the first, whose
        // cost is dropped, but only after every part of it was read.
        Optional<BankLevels> alone = BankLevels.parse(List.of(line), new ArrayList<>());
        if (alone.isEmpty()) {
            return Outcome.refused(Component.translatable("economycore.admin.bank.bad_level"));
        }
        String oldName = index >= 0 ? current.get().level(index).name() : null;
        List<Long> caps = new ArrayList<>(current.get().levels().stream().map(BankLevels.Level::cap).toList());
        if (index >= 0) {
            lines.remove(index);
            caps.remove(index);
        }
        // Put where its cap belongs, so a level can go between two others,
        // as silver between starter and gold, and not only on top.
        long cap = alone.get().level(0).cap();
        int at = 0;
        while (at < caps.size() && caps.get(at) < cap) {
            at++;
        }
        lines.add(at, line);
        List<String> warnings = new ArrayList<>();
        Optional<BankLevels> read = BankLevels.parse(lines, warnings);
        if (read.isEmpty()) {
            return Outcome.refused(Component.translatable("economycore.admin.bank.levels_clash"));
        }
        for (BankLevels.Level level : read.get().levels()) {
            for (BankLevels.ItemCost cost : level.items()) {
                if (SavingsLevels.item(cost).isEmpty()) {
                    return Outcome.refused(Component.translatable("economycore.admin.bank.no_such_item", cost.item()));
                }
            }
        }
        write(server, read.get());
        if (oldName != null) {
            CreditStorage.get(server).renameBankLevel(oldName, read.get().reached(at));
        }
        refresh(server);
        // A cost brought down to what the level before holds, said to whoever set it.
        return new Outcome(true, warnings.isEmpty() ? null : Component.translatable("economycore.admin.bank.cost_lowered"));
    }

    /**
     * Takes a level out. Players on it drop to the highest level left that
     * holds no more than it did; nobody is lifted by a level going away.
     *
     * @return whether it was removed, and if not, why
     */
    public static Outcome removeLevel(MinecraftServer server, int index) {
        Optional<BankLevels> current = currentLevels();
        if (current.isEmpty()) {
            return Outcome.refused(Component.translatable("economycore.admin.bank.unreadable"));
        }
        List<BankLevels.Level> levels = new ArrayList<>(current.get().levels());
        if (index < 0 || index >= levels.size()) {
            return Outcome.refused(Component.translatable("economycore.admin.bank.no_such_level"));
        }
        levels.remove(index);
        List<String> lines = levels.stream().map(BankLevels.Level::toLine).toList();
        // Read again, not just kept: the level now first becomes free.
        Optional<BankLevels> read = lines.isEmpty()
                ? Optional.of(new BankLevels(List.of()))
                : BankLevels.parse(lines, new ArrayList<>());
        if (read.isEmpty()) {
            return Outcome.refused(Component.translatable("economycore.admin.bank.levels_clash"));
        }
        write(server, read.get());
        refresh(server);
        return new Outcome(true, null);
    }

    /** The levels as this world has them, empty included; nothing if they cannot be read. */
    private static Optional<BankLevels> currentLevels() {
        return levelLines().isEmpty() ? Optional.of(new BankLevels(List.of())) : SavingsLevels.levels();
    }

    private static void write(MinecraftServer server, BankLevels levels) {
        EconomyData.get(server).setBankLevels(levels.levels().stream().map(BankLevels.Level::toLine).toList());
    }

    /** Sends the new settings to every admin looking at them, and the bank to every player who has it open. */
    public static void refresh(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.containerMenu instanceof AdminBankMenu) {
                PacketDistributor.sendToPlayer(player, AdminBankStatePayload.current());
            } else if (player.containerMenu instanceof BankMenu) {
                BankStatePayload.send(player);
            }
        }
    }
}
