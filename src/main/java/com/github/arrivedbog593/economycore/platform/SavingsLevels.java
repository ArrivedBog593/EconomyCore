package com.github.arrivedbog593.economycore.platform;

import com.github.arrivedbog593.economycore.api.CreditFormat;
import com.github.arrivedbog593.economycore.api.Pocket;
import com.github.arrivedbog593.economycore.api.TransactionType;
import com.github.arrivedbog593.economycore.engine.BankLevels;
import com.github.arrivedbog593.economycore.engine.BankMovement;
import com.github.arrivedbog593.economycore.engine.CreditAccount;
import com.github.arrivedbog593.economycore.engine.InterestTranches;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The levels of players' bank accounts, as the server config sets them, and
 * moving up one.
 * <p>
 * A level is what caps a bank: deposits stop at it, and so does interest.
 * Credits staff put in with {@code /credits give ... bank} do not, since
 * staff mean them. With no levels configured there is no cap but the
 * balance cap every account has.
 * <p>
 * The config is read again whenever asked, so a change takes effect at once;
 * each reading is cheap, and a line that cannot be read is said in the log
 * once, not every time.
 */
public final class SavingsLevels {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** The config as last read, so its problems are logged once per change rather than per deposit. */
    private static List<String> lastLines;
    private static Optional<BankLevels> lastRead = Optional.empty();

    /**
     * What happened, as a message for the player.
     *
     * @param done whether the account moved up
     */
    public record Result(boolean done, Component message) {
    }

    private SavingsLevels() {
    }

    /** The levels the config sets, or empty for a bank with no limit. */
    public static synchronized Optional<BankLevels> levels() {
        if (!EconomyConfig.SPEC.isLoaded()) {
            return Optional.empty();
        }
        List<String> lines = BankSettings.levelLines();
        if (lines.equals(lastLines)) {
            return lastRead;
        }
        lastLines = lines;
        List<String> warnings = new ArrayList<>();
        lastRead = lines.isEmpty() ? Optional.empty() : BankLevels.parse(lines, warnings);
        if (!lines.isEmpty() && lastRead.isEmpty()) {
            LOGGER.error("[Economy] The bank levels (bankLevels.levels in the server config, or this world's "
                    + "own) cannot be read, so banks have "
                    + "no limit. Each line must look like \"gold; cap=100000000; cost=5000000; "
                    + "items=minecraft:gold_block*1\", with lowercase names, each level holding more than the last.");
            // Read alone, a line that is wrong in itself shows; if none is,
            // the fault is between lines: a name twice, or a cap that does not rise.
            for (String line : lines) {
                if (BankLevels.parse(List.of(line), new ArrayList<>()).isEmpty()) {
                    LOGGER.error("[Economy] bankLevels: this line cannot be read: {}", line);
                }
            }
        }
        warnings.forEach(warning -> LOGGER.warn("[Economy] bankLevels: {}", warning));
        lastRead.ifPresent(read -> {
            for (BankLevels.Level level : read.levels()) {
                for (BankLevels.ItemCost cost : level.items()) {
                    if (item(cost).isEmpty()) {
                        LOGGER.warn("[Economy] bankLevels: level {} asks for {}, which is not an item. "
                                + "Nobody can upgrade to it until that is fixed.", level.name(), cost.item());
                    }
                }
            }
        });
        return lastRead;
    }

    /** The level this player's account is at, or empty with no levels configured. */
    public static Optional<BankLevels.Level> levelOf(CreditStorage storage, UUID player) {
        return levels().map(read -> read.level(read.placeOf(storage.bankLevel(player))));
    }

    /** The most this player's bank may hold. */
    public static long capOf(CreditStorage storage, UUID player) {
        return levelOf(storage, player).map(BankLevels.Level::cap).orElse(CreditAccount.MAX_BALANCE);
    }

    /** How much more this player may put in their bank. */
    public static long roomIn(CreditStorage storage, UUID player) {
        return Math.max(0, capOf(storage, player) - storage.balanceOf(player, Pocket.SAVINGS));
    }

    /**
     * The interest this player's bank pays: its level's, or the server's
     * own tranches for a level that sets none, or with no levels at all.
     */
    public static Optional<InterestTranches> tranchesOf(CreditStorage storage, UUID player) {
        Optional<InterestTranches> own = levelOf(storage, player).map(BankLevels.Level::tranches);
        return own.isPresent() ? own : SavingsInterest.tranches();
    }

    /**
     * A level's name for players: its translation if there is one, else the
     * name as written, with spaces and capitals: "super_deluxe" reads
     * "Super Deluxe". A server wanting another wording for a level of its own
     * gives it one in a resource pack, under {@code economycore.my_bank.level.<name>}.
     */
    public static MutableComponent nameOf(String level) {
        StringBuilder words = new StringBuilder();
        for (String word : level.split("_")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!words.isEmpty()) {
                words.append(' ');
            }
            words.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return Component.translatableWithFallback("economycore.my_bank.level." + level, words.toString());
    }

    public static Optional<Item> item(BankLevels.ItemCost cost) {
        ResourceLocation id = ResourceLocation.tryParse(cost.item());
        return id == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(id);
    }

    /** How many of an item a player carries. */
    public static int countOf(Inventory inventory, Item item) {
        int count = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /**
     * Moves a player's account up one level, if they can pay for it: the
     * credits from their bank, the items from their inventory.
     * <p>
     * Everything is checked before anything is taken, on the server thread,
     * so nothing can change between the check and the taking. The credits
     * are the server's income, like the shop's sales.
     */
    public static Result upgrade(ServerPlayer player) {
        Optional<BankLevels> read = levels();
        if (read.isEmpty()) {
            return refused(Component.translatable("economycore.my_bank.upgrade.none"));
        }
        BankLevels levels = read.get();
        CreditStorage storage = CreditStorage.get(player.server);
        UUID id = player.getUUID();
        int at = levels.placeOf(storage.bankLevel(id));
        Optional<BankLevels.Level> next = levels.next(at);
        if (next.isEmpty()) {
            return refused(Component.translatable("economycore.my_bank.upgrade.top"));
        }
        BankLevels.Level level = next.get();

        long saved = storage.balanceOf(id, Pocket.SAVINGS);
        if (saved < level.credits()) {
            return refused(Component.translatable("economycore.my_bank.upgrade.short",
                    CreditFormat.of(level.credits()), CreditFormat.of(saved)));
        }
        List<Item> items = new ArrayList<>();
        for (BankLevels.ItemCost cost : level.items()) {
            Optional<Item> item = item(cost);
            if (item.isEmpty()) {
                return refused(Component.translatable("economycore.my_bank.upgrade.broken"));
            }
            if (countOf(player.getInventory(), item.get()) < cost.count()) {
                return refused(Component.translatable("economycore.my_bank.upgrade.missing",
                        cost.count(), item.get().getDescription()));
            }
            items.add(item.get());
        }

        String detail = "bank upgrade to " + level.name() + " by " + player.getGameProfile().getName();
        if (level.credits() > 0) {
            if (!storage.withdraw(id, Pocket.SAVINGS, level.credits())) {
                return refused(Component.translatable("economycore.my_bank.upgrade.short",
                        CreditFormat.of(level.credits()), CreditFormat.of(saved)));
            }
            EconomyEvents.record(storage, TransactionType.BANK_UPGRADE, id, Pocket.SAVINGS, -level.credits(),
                    storage.balanceOf(id, Pocket.SAVINGS), detail);
            ServerIncome.collect(storage, TransactionType.BANK_UPGRADE, level.credits(), detail);
        }
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            player.getInventory().clearOrCountMatchingItems(stack -> stack.is(item),
                    level.items().get(i).count(), player.inventoryMenu.getCraftSlots());
        }
        storage.setBankLevel(id, levels.reached(at + 1));
        storage.recordBankMovement(id, new BankMovement(System.currentTimeMillis(), BankMovement.Kind.UPGRADE,
                level.credits()));
        return new Result(true, Component.translatable("economycore.my_bank.upgrade.done",
                nameOf(level.name()), CreditFormat.of(level.cap())));
    }

    private static Result refused(Component message) {
        return new Result(false, message);
    }
}
