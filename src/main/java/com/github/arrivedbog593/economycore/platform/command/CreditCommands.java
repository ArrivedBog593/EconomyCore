package com.github.arrivedbog593.economycore.platform.command;

import com.github.arrivedbog593.economycore.api.CreditFormat;
import com.github.arrivedbog593.economycore.api.Pocket;
import com.github.arrivedbog593.economycore.api.TransactionType;
import com.github.arrivedbog593.economycore.engine.BankLevels;
import com.github.arrivedbog593.economycore.engine.CreditAccount;
import com.github.arrivedbog593.economycore.platform.Commitments;
import com.github.arrivedbog593.economycore.platform.CreditStorage;
import com.github.arrivedbog593.economycore.platform.EconomyEvents;
import com.github.arrivedbog593.economycore.platform.PersonalBank;
import com.github.arrivedbog593.economycore.platform.SavingsLevels;
import com.github.arrivedbog593.economycore.platform.menu.BankMenu;
import com.github.arrivedbog593.economycore.platform.menu.Counter;
import com.github.arrivedbog593.economycore.platform.network.BankStatePayload;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code /credits}: a balance, a payment between players, putting credits
 * away in savings and taking them back out, and staff moving money that is
 * not their own.
 * <p>
 * Everything here but the savings commands works on the purse, the pocket
 * players spend from, unless staff name the bank.
 * <p>
 * Items are not traded here. {@code /credits convert} and
 * {@code /credits redeem} came first, to try the economy out before there
 * was a block to stand at, and outlived that purpose once the exchange
 * could be opened from anywhere with {@code /exchange}. Kept, they were a
 * back door around a server that switches that command off to make players
 * go to a counter.
 * <p>
 * All logging goes through {@link EconomyEvents#record}, never straight to
 * the log, so the saved sequence advances with every movement.
 */
public final class CreditCommands {

    private CreditCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("credits");

        root.executes(context -> showBalance(context.getSource()));

        root.then(Commands.literal("balance")
                .executes(context -> showBalance(context.getSource()))
                // Somebody else's: staff work, and asked about players who
                // are offline as often as online, so any known profile will do.
                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                        .requires(StaffAccess::adminOrOperator)
                        .executes(context -> showBalanceOf(context.getSource(),
                                GameProfileArgument.getGameProfiles(context, "player")))));

        // Under the mod's own tree rather than a bare /pay. This runs on
        // Arclight beside Bukkit plugins, and Essentials has owned /pay on
        // servers like this one for a decade; taking the name would decide
        // that fight silently and in the wrong direction.
        root.then(Commands.literal("pay")
                .then(Commands.argument("target", EntityArgument.player())
                        .then(Commands.argument("amount",
                                        LongArgumentType.longArg(1, CreditAccount.MAX_BALANCE))
                                .executes(context -> pay(
                                        context.getSource(),
                                        EntityArgument.getPlayer(context, "target"),
                                        LongArgumentType.getLong(context, "amount"))))));

        // Between a player's own pockets, from anywhere: the same door as
        // /bank, and closed with it. A server that switches /bank off to make
        // players walk to a banker would otherwise find these still open.
        root.then(Commands.literal("deposit")
                .requires(source -> CounterCommands.mayOpen(source, Counter.BANK))
                .then(Commands.literal("all")
                        .executes(context -> deposit(context.getSource(), PersonalBank.ALL)))
                .then(Commands.argument("amount", LongArgumentType.longArg(1, CreditAccount.MAX_BALANCE))
                        .executes(context -> deposit(context.getSource(),
                                LongArgumentType.getLong(context, "amount")))));

        root.then(Commands.literal("withdraw")
                .requires(source -> CounterCommands.mayOpen(source, Counter.BANK))
                .then(Commands.literal("all")
                        .executes(context -> withdraw(context.getSource(), PersonalBank.ALL)))
                .then(Commands.argument("amount", LongArgumentType.longArg(1, CreditAccount.MAX_BALANCE))
                        .executes(context -> withdraw(context.getSource(),
                                LongArgumentType.getLong(context, "amount")))));

        // Moving money that is not your own: operators and staff administrators.
        // The purse unless the bank is named, so the commands from before
        // there was a bank still do what they did.
        root.then(staffMove("give", 1, CreditCommands::give));
        root.then(staffMove("take", 1, CreditCommands::take));
        root.then(staffMove("set", 0, CreditCommands::set));

        // A bank's level, to look at or to set outright without charging for it.
        root.then(Commands.literal("banklevel")
                .requires(StaffAccess::adminOrOperator)
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(context -> showBankLevel(context.getSource(),
                                EntityArgument.getPlayer(context, "target")))
                        .then(Commands.argument("level", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        SavingsLevels.levels().map(levels -> levels.levels().stream()
                                                .map(BankLevels.Level::name).toList()).orElse(List.of()), builder))
                                .executes(context -> setBankLevel(context.getSource(),
                                        EntityArgument.getPlayer(context, "target"),
                                        StringArgumentType.getString(context, "level"))))));

        dispatcher.register(root);
    }

    /** What a staff movement does once it knows whom, how much and which pocket. */
    @FunctionalInterface
    private interface StaffMove {
        int run(CommandSourceStack source, ServerPlayer target, long amount, Pocket pocket);
    }

    /** {@code /credits <name> <target> <amount> [purse|bank]}. */
    private static LiteralArgumentBuilder<CommandSourceStack> staffMove(String name, long least, StaffMove move) {
        return Commands.literal(name)
                .requires(StaffAccess::adminOrOperator)
                .then(Commands.argument("target", EntityArgument.player())
                        .then(Commands.argument("amount", LongArgumentType.longArg(least, CreditAccount.MAX_BALANCE))
                                .executes(context -> move.run(context.getSource(),
                                        EntityArgument.getPlayer(context, "target"),
                                        LongArgumentType.getLong(context, "amount"), Pocket.PURSE))
                                .then(Commands.literal("purse")
                                        .executes(context -> move.run(context.getSource(),
                                                EntityArgument.getPlayer(context, "target"),
                                                LongArgumentType.getLong(context, "amount"), Pocket.PURSE)))
                                .then(Commands.literal("bank")
                                        .executes(context -> move.run(context.getSource(),
                                                EntityArgument.getPlayer(context, "target"),
                                                LongArgumentType.getLong(context, "amount"), Pocket.SAVINGS)))));
    }

    /**
     * The key for a staff message, with {@code _bank} on the end when it is
     * about the bank, so each language can word "their bank" its own way.
     */
    private static String key(String name, Pocket pocket) {
        return "economycore.command.credits." + name + (pocket == Pocket.SAVINGS ? "_bank" : "");
    }

    /**
     * What staff may not take from this pocket: in the purse, what is
     * promised to a table or a bid; the bank has nothing promised.
     */
    private static long promisedFrom(UUID player, Pocket pocket, long balance) {
        return pocket == Pocket.PURSE ? balance - Commitments.players().spendable(player, balance) : 0;
    }

    private static int showBankLevel(CommandSourceStack source, ServerPlayer target) {
        CreditStorage storage = CreditStorage.get(source.getServer());
        Optional<BankLevels.Level> level = SavingsLevels.levelOf(storage, target.getUUID());
        if (level.isEmpty()) {
            source.sendFailure(Component.translatable("economycore.my_bank.upgrade.none"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("economycore.command.credits.bank_level",
                target.getDisplayName(), SavingsLevels.nameOf(level.get().name()),
                CreditFormat.of(level.get().cap())), false);
        return 1;
    }

    /** Puts a player's bank on a level, up or down, charging nothing and taking nothing out. */
    private static int setBankLevel(CommandSourceStack source, ServerPlayer target, String name) {
        Optional<BankLevels> levels = SavingsLevels.levels();
        if (levels.isEmpty()) {
            source.sendFailure(Component.translatable("economycore.my_bank.upgrade.none"));
            return 0;
        }
        int index = levels.get().indexOf(name);
        if (index < 0) {
            source.sendFailure(Component.translatable("economycore.command.credits.bank_level_unknown", name));
            return 0;
        }
        CreditStorage.get(source.getServer()).setBankLevel(target.getUUID(), levels.get().reached(index));
        StaffAccess.announce(source,
                Component.translatable("economycore.command.credits.bank_level_set",
                        target.getDisplayName(), SavingsLevels.nameOf(name)),
                Component.translatable("economycore.command.credits.bank_level_set_by", source.getDisplayName(),
                        target.getDisplayName(), SavingsLevels.nameOf(name)));
        if (target.containerMenu instanceof BankMenu) {
            BankStatePayload.send(target);
        }
        return 1;
    }

    private static int showBalance(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        CreditStorage storage = CreditStorage.get(source.getServer());
        long balance = storage.balanceOf(player.getUUID());
        long saved = storage.balanceOf(player.getUUID(), Pocket.SAVINGS);
        source.sendSuccess(() -> Component.translatable("economycore.command.credits.balance",
                CreditFormat.of(balance), CreditFormat.of(saved)), false);
        return (int) Math.min(Integer.MAX_VALUE, balance);
    }

    /**
     * Other players' balances, for staff, with what each has set aside at a
     * game table or in a bid: that part is in the purse but cannot be spent or taken.
     */
    private static int showBalanceOf(CommandSourceStack source, Collection<GameProfile> profiles) {
        CreditStorage storage = CreditStorage.get(source.getServer());
        for (GameProfile profile : profiles) {
            long balance = storage.balanceOf(profile.getId());
            long saved = storage.balanceOf(profile.getId(), Pocket.SAVINGS);
            long promised = Commitments.players().committedBy(profile.getId());
            source.sendSuccess(() -> promised > 0
                    ? Component.translatable("economycore.command.credits.balance_of_promised",
                    profile.getName(), CreditFormat.of(balance), CreditFormat.of(saved),
                    CreditFormat.of(promised))
                    : Component.translatable("economycore.command.credits.balance_of",
                    profile.getName(), CreditFormat.of(balance), CreditFormat.of(saved)), false);
        }
        return profiles.size();
    }

    /** Puts credits from the purse away in savings; see {@link PersonalBank}. */
    private static int deposit(CommandSourceStack source, long requested) throws CommandSyntaxException {
        return report(source, PersonalBank.deposit(source.getPlayerOrException(), requested));
    }

    /** Takes credits out of savings into the purse, where they can be spent. */
    private static int withdraw(CommandSourceStack source, long requested) throws CommandSyntaxException {
        return report(source, PersonalBank.withdraw(source.getPlayerOrException(), requested));
    }

    private static int report(CommandSourceStack source, PersonalBank.Result result) {
        if (!result.done()) {
            source.sendFailure(result.message());
            return 0;
        }
        source.sendSuccess(result::message, false);
        return 1;
    }

    /**
     * Moves credits from one player to another.
     * <p>
     * The only movement in the mod that a player starts and that has a second
     * player on the far side, which is what makes it worth being careful
     * about. It spends against what is free rather than against the balance:
     * without that, paying an alt during the lockout would cancel a wager
     * exactly the way buying an item used to.
     * <p>
     * Two log lines, not one. A record carries a single owner, so a transfer
     * is a withdrawal on one balance and a deposit on the other, each naming
     * the other party. Both are recoverable, and replaying them lands on the
     * same two balances because each line carries the balance it produced.
     */
    private static int pay(CommandSourceStack source, ServerPlayer target, long amount)
            throws CommandSyntaxException {
        ServerPlayer sender = source.getPlayerOrException();
        if (sender.getUUID().equals(target.getUUID())) {
            source.sendFailure(Component.translatable("economycore.command.credits.pay_self"));
            return 0;
        }

        CreditStorage storage = CreditStorage.get(source.getServer());
        long balance = storage.balanceOf(sender.getUUID());
        long spendable = Commitments.players().spendable(sender.getUUID(), balance);
        if (amount > spendable) {
            // Two different problems, as in the shop: being short is the
            // player's own business, being short only because of a live
            // wager is something they cannot see from the balance.
            source.sendFailure(Component.translatable(amount <= balance
                    ? "economycore.command.credits.pay_committed"
                    : "economycore.command.credits.pay_short", spendable));
            return 0;
        }

        // One call, so a deposit that would break the receiver's cap, leaves
        // both balances untouched rather than taking from one and failing on
        // the other.
        if (!storage.transfer(sender.getUUID(), target.getUUID(), amount)) {
            source.sendFailure(Component.translatable("economycore.exchange.cap_reached"));
            return 0;
        }

        EconomyEvents.record(storage, TransactionType.TRANSFER, sender.getUUID(),
                -amount, storage.balanceOf(sender.getUUID()),
                "to " + target.getGameProfile().getName());
        EconomyEvents.record(storage, TransactionType.TRANSFER, target.getUUID(),
                amount, storage.balanceOf(target.getUUID()),
                "from " + sender.getGameProfile().getName());

        source.sendSuccess(() -> Component.translatable(
                "economycore.command.credits.paid", amount, target.getDisplayName()), false);
        target.sendSystemMessage(Component.translatable(
                "economycore.command.credits.pay_received", amount, sender.getDisplayName()));
        return 1;
    }

    private static int give(CommandSourceStack source, ServerPlayer target, long amount, Pocket pocket) {
        CreditStorage storage = CreditStorage.get(source.getServer());
        if (!storage.deposit(target.getUUID(), pocket, amount)) {
            source.sendFailure(Component.translatable("economycore.exchange.cap_reached"));
            return 0;
        }
        long balance = storage.balanceOf(target.getUUID(), pocket);
        EconomyEvents.record(storage, TransactionType.ADMIN_GIVE,
                target.getUUID(), pocket, amount, balance, "by " + source.getTextName());
        StaffAccess.announce(source,
                Component.translatable(key("given", pocket),
                        CreditFormat.of(amount), target.getDisplayName()),
                Component.translatable(key("given_by", pocket), source.getDisplayName(),
                        CreditFormat.of(amount), target.getDisplayName(), CreditFormat.of(balance)));
        return 1;
    }

    /**
     * Takes credits off a player: the other half of {@link #give}, for
     * correcting a grant or clawing back credits that should not exist.
     * <p>
     * Never from what the player has promised. Those credits are owed to a
     * bet or a bid that has not settled yet, and taking them would leave the
     * player unable to pay: the round would then be handed back as if it had
     * never happened.
     */
    private static int take(CommandSourceStack source, ServerPlayer target, long amount, Pocket pocket) {
        CreditStorage storage = CreditStorage.get(source.getServer());
        long balance = storage.balanceOf(target.getUUID(), pocket);
        long promised = promisedFrom(target.getUUID(), pocket, balance);
        long takeable = balance - promised;
        if (amount > takeable) {
            source.sendFailure(promised > 0
                    ? Component.translatable("economycore.command.credits.take_promised",
                            target.getDisplayName(), CreditFormat.of(promised),
                            CreditFormat.of(takeable))
                    : Component.translatable(key("take_short", pocket),
                            target.getDisplayName(), CreditFormat.of(balance)));
            return 0;
        }
        if (!storage.withdraw(target.getUUID(), pocket, amount)) {
            source.sendFailure(Component.translatable(key("take_short", pocket),
                    target.getDisplayName(), CreditFormat.of(balance)));
            return 0;
        }
        long after = storage.balanceOf(target.getUUID(), pocket);
        EconomyEvents.record(storage, TransactionType.ADMIN_TAKE,
                target.getUUID(), pocket, -amount, after, "by " + source.getTextName());
        StaffAccess.announce(source,
                Component.translatable(key("taken", pocket),
                        CreditFormat.of(amount), target.getDisplayName(), CreditFormat.of(after)),
                Component.translatable(key("taken_by", pocket), source.getDisplayName(),
                        CreditFormat.of(amount), target.getDisplayName(), CreditFormat.of(after)));
        return 1;
    }

    /**
     * Sets a balance outright.
     * <p>
     * For correcting a balance that went wrong, which on a live server
     * eventually happens. Logged as a grant or a removal depending on which
     * way it moved, so the audit trail still adds up. Never below what the
     * player has promised, for the same reason {@link #take} stops there.
     */
    private static int set(CommandSourceStack source, ServerPlayer target, long amount, Pocket pocket) {
        CreditStorage storage = CreditStorage.get(source.getServer());
        long before = storage.balanceOf(target.getUUID(), pocket);
        long promised = promisedFrom(target.getUUID(), pocket, before);
        if (amount < before && amount < promised) {
            source.sendFailure(Component.translatable("economycore.command.credits.set_promised",
                    target.getDisplayName(), CreditFormat.of(promised)));
            return 0;
        }
        long delta = amount - before;
        if (delta != 0) {
            boolean done = delta > 0
                    ? storage.deposit(target.getUUID(), pocket, delta)
                    : storage.withdraw(target.getUUID(), pocket, -delta);
            if (!done) {
                source.sendFailure(Component.translatable("economycore.exchange.cap_reached"));
                return 0;
            }
            EconomyEvents.record(storage,
                    delta > 0 ? TransactionType.ADMIN_GIVE : TransactionType.ADMIN_TAKE,
                    target.getUUID(), pocket, delta, amount, "set by " + source.getTextName());
        }
        StaffAccess.announce(source,
                Component.translatable(key("set", pocket),
                        target.getDisplayName(), CreditFormat.of(amount)),
                Component.translatable(key("set_by", pocket), source.getDisplayName(),
                        target.getDisplayName(), CreditFormat.of(amount)));
        return 1;
    }
}
