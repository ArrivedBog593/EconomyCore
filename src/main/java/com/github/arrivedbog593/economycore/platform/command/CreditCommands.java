package com.github.arrivedbog593.economycore.platform.command;

import com.github.arrivedbog593.economycore.engine.CreditAccount;
import com.github.arrivedbog593.economycore.engine.TransactionType;
import com.github.arrivedbog593.economycore.platform.Commitments;
import com.github.arrivedbog593.economycore.platform.CreditExchange;
import com.github.arrivedbog593.economycore.platform.CreditFormat;
import com.github.arrivedbog593.economycore.platform.CreditStorage;
import com.github.arrivedbog593.economycore.platform.EconomyEvents;
import com.github.arrivedbog593.economycore.platform.ItemIds;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Player-facing credit commands, including the full round trip between items
 * and credits.
 * <p>
 * These exist so the economy can be exercised before any table block or GUI
 * is written. The exchange GUI will call the same {@link CreditExchange}
 * methods, so the behavior stays identical whichever way a player uses it.
 * <p>
 * Item ids use {@link ResourceLocationArgument} rather than a string
 * argument. Brigadier's unquoted strings do not allow a colon, so
 * {@code minecraft:diamond} fails to parse as a plain word; the resource
 * location argument understands the format natively and fills in
 * {@code minecraft:} when the namespace is left off.
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
                .executes(context -> showBalance(context.getSource())));

        root.then(Commands.literal("convert")
                .executes(context -> convertHeld(context.getSource())));

        root.then(Commands.literal("redeem")
                .then(Commands.argument("item", ResourceLocationArgument.id())
                        .suggests(EconomySuggestions.PRICED_ITEMS)
                        .executes(context -> redeemAll(
                                context.getSource(),
                                ResourceLocationArgument.getId(context, "item").toString()))
                        .then(Commands.argument("count", LongArgumentType.longArg(1))
                                .executes(context -> redeemExactly(
                                        context.getSource(),
                                        ResourceLocationArgument.getId(context, "item").toString(),
                                        LongArgumentType.getLong(context, "count"))))));

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

        // Moving money that is not your own: operators and staff administrators.
        root.then(Commands.literal("give")
                .requires(StaffAccess::adminOrOperator)
                .then(Commands.argument("target", EntityArgument.player())
                        .then(Commands.argument("amount",
                                        LongArgumentType.longArg(1, CreditAccount.MAX_BALANCE))
                                .executes(context -> give(
                                        context.getSource(),
                                        EntityArgument.getPlayer(context, "target"),
                                        LongArgumentType.getLong(context, "amount"))))));

        root.then(Commands.literal("take")
                .requires(StaffAccess::adminOrOperator)
                .then(Commands.argument("target", EntityArgument.player())
                        .then(Commands.argument("amount",
                                        LongArgumentType.longArg(1, CreditAccount.MAX_BALANCE))
                                .executes(context -> take(
                                        context.getSource(),
                                        EntityArgument.getPlayer(context, "target"),
                                        LongArgumentType.getLong(context, "amount"))))));

        root.then(Commands.literal("set")
                .requires(StaffAccess::adminOrOperator)
                .then(Commands.argument("target", EntityArgument.player())
                        .then(Commands.argument("amount",
                                        LongArgumentType.longArg(0, CreditAccount.MAX_BALANCE))
                                .executes(context -> set(
                                        context.getSource(),
                                        EntityArgument.getPlayer(context, "target"),
                                        LongArgumentType.getLong(context, "amount"))))));

        dispatcher.register(root);
    }

    private static int showBalance(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        long balance = CreditStorage.get(source.getServer()).balanceOf(player.getUUID());
        source.sendSuccess(() -> Component.translatable(
                "economycore.command.credits.balance", balance), false);
        return (int) Math.min(Integer.MAX_VALUE, balance);
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

    private static int give(CommandSourceStack source, ServerPlayer target, long amount) {
        CreditStorage storage = CreditStorage.get(source.getServer());
        if (!storage.deposit(target.getUUID(), amount)) {
            source.sendFailure(Component.translatable("economycore.exchange.cap_reached"));
            return 0;
        }
        long balance = storage.balanceOf(target.getUUID());
        EconomyEvents.record(storage, TransactionType.ADMIN_GIVE,
                target.getUUID(), amount, balance, "by " + source.getTextName());
        StaffAccess.announce(source,
                Component.translatable("economycore.command.credits.given",
                        CreditFormat.of(amount), target.getDisplayName()),
                Component.translatable("economycore.command.credits.given_by", source.getDisplayName(),
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
    private static int take(CommandSourceStack source, ServerPlayer target, long amount) {
        CreditStorage storage = CreditStorage.get(source.getServer());
        long balance = storage.balanceOf(target.getUUID());
        long takeable = Commitments.players().spendable(target.getUUID(), balance);
        if (amount > takeable) {
            source.sendFailure(takeable < balance
                    ? Component.translatable("economycore.command.credits.take_promised",
                            target.getDisplayName(), CreditFormat.of(balance - takeable),
                            CreditFormat.of(takeable))
                    : Component.translatable("economycore.command.credits.take_short",
                            target.getDisplayName(), CreditFormat.of(balance)));
            return 0;
        }
        if (!storage.withdraw(target.getUUID(), amount)) {
            source.sendFailure(Component.translatable("economycore.command.credits.take_short",
                    target.getDisplayName(), CreditFormat.of(balance)));
            return 0;
        }
        long after = storage.balanceOf(target.getUUID());
        EconomyEvents.record(storage, TransactionType.ADMIN_TAKE,
                target.getUUID(), -amount, after, "by " + source.getTextName());
        StaffAccess.announce(source,
                Component.translatable("economycore.command.credits.taken",
                        CreditFormat.of(amount), target.getDisplayName(), CreditFormat.of(after)),
                Component.translatable("economycore.command.credits.taken_by", source.getDisplayName(),
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
    private static int set(CommandSourceStack source, ServerPlayer target, long amount) {
        CreditStorage storage = CreditStorage.get(source.getServer());
        long before = storage.balanceOf(target.getUUID());
        long promised = before - Commitments.players().spendable(target.getUUID(), before);
        if (amount < before && amount < promised) {
            source.sendFailure(Component.translatable("economycore.command.credits.set_promised",
                    target.getDisplayName(), CreditFormat.of(promised)));
            return 0;
        }
        long delta = amount - before;
        if (delta != 0) {
            boolean done = delta > 0
                    ? storage.deposit(target.getUUID(), delta)
                    : storage.withdraw(target.getUUID(), -delta);
            if (!done) {
                source.sendFailure(Component.translatable("economycore.exchange.cap_reached"));
                return 0;
            }
            EconomyEvents.record(storage,
                    delta > 0 ? TransactionType.ADMIN_GIVE : TransactionType.ADMIN_TAKE,
                    target.getUUID(), delta, amount, "set by " + source.getTextName());
        }
        StaffAccess.announce(source,
                Component.translatable("economycore.command.credits.set",
                        target.getDisplayName(), CreditFormat.of(amount)),
                Component.translatable("economycore.command.credits.set_by", source.getDisplayName(),
                        target.getDisplayName(), CreditFormat.of(amount)));
        return 1;
    }

    /** Items to credits. */
    private static int convertHeld(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ItemStack held = player.getMainHandItem();
        CreditStorage storage = CreditStorage.get(source.getServer());

        String itemId = held.isEmpty() ? "empty" : ItemIds.idOf(held);
        CreditExchange.Result result = CreditExchange.deposit(
                player, held, EconomyEvents.economy(), storage);

        if (!result.success()) {
            source.sendFailure(Component.translatable(result.failureKey()));
            return 0;
        }

        long balance = storage.balanceOf(player.getUUID());
        EconomyEvents.record(storage, TransactionType.CONVERT_IN, player.getUUID(),
                result.credits(), balance, result.itemCount() + "x " + itemId);
        source.sendSuccess(() -> Component.translatable(
                "economycore.command.credits.converted",
                result.itemCount(), result.credits()), false);
        return 1;
    }

    /** Credits to items: as many as the balance covers. */
    private static int redeemAll(CommandSourceStack source, String itemId)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Optional<Item> item = ItemIds.item(itemId);
        if (item.isEmpty()) {
            source.sendFailure(Component.translatable(
                    "economycore.exchange.no_such_item", itemId));
            return 0;
        }
        CreditStorage storage = CreditStorage.get(source.getServer());
        return report(source, player, itemId, storage, CreditExchange.redeemAll(
                player, item.get(), EconomyEvents.economy(), storage));
    }

    /**
     * Credits to items: exactly this many, or nothing.
     * <p>
     * A short balance refuses the whole request and says how many would have
     * worked, rather than quietly handing over fewer than asked for.
     */
    private static int redeemExactly(CommandSourceStack source, String itemId, long count)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Optional<Item> item = ItemIds.item(itemId);
        if (item.isEmpty()) {
            source.sendFailure(Component.translatable(
                    "economycore.exchange.no_such_item", itemId));
            return 0;
        }
        CreditStorage storage = CreditStorage.get(source.getServer());
        return report(source, player, itemId, storage, CreditExchange.redeemExactly(
                player, item.get(), count, EconomyEvents.economy(), storage));
    }

    /** Shared reporting and logging for both redeem forms. */
    private static int report(CommandSourceStack source, ServerPlayer player, String itemId,
                              CreditStorage storage, CreditExchange.Result result) {
        if (!result.success()) {
            source.sendFailure(Component.translatable(
                    result.failureKey(),
                    result.affordable(),
                    ItemIds.displayName(itemId),
                    result.required()));
            return 0;
        }

        long balance = storage.balanceOf(player.getUUID());
        EconomyEvents.record(storage, TransactionType.CONVERT_OUT, player.getUUID(),
                -result.credits(), balance, result.itemCount() + "x " + itemId);
        source.sendSuccess(() -> Component.translatable(
                "economycore.command.credits.redeemed",
                result.itemCount(), ItemIds.displayName(itemId), result.credits()), false);
        return 1;
    }
}