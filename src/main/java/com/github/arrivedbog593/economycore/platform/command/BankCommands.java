package com.github.arrivedbog593.economycore.platform.command;

import com.github.arrivedbog593.economycore.engine.CreditAccount;
import com.github.arrivedbog593.economycore.engine.TransactionType;
import com.github.arrivedbog593.economycore.platform.CreditFormat;
import com.github.arrivedbog593.economycore.platform.CreditStorage;
import com.github.arrivedbog593.economycore.platform.ServerBank;
import com.github.arrivedbog593.economycore.platform.ServerIncome;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * {@code /economycore bank}: the server bank's balance, and putting money in
 * and taking it out.
 * <p>
 * Operators and staff administrators. The bank is the server's money, and an
 * administrator already runs everything else the server owns.
 */
public final class BankCommands {

    private BankCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("economycore");

        root.then(Commands.literal("bank")
                .requires(StaffAccess::adminOrOperator)
                .executes(context -> status(context.getSource()))
                .then(Commands.literal("add")
                        .then(Commands.argument("amount",
                                        LongArgumentType.longArg(1, CreditAccount.MAX_BALANCE))
                                .executes(context -> add(context.getSource(),
                                        LongArgumentType.getLong(context, "amount")))))
                .then(Commands.literal("take")
                        .then(Commands.argument("amount",
                                        LongArgumentType.longArg(1, CreditAccount.MAX_BALANCE))
                                .executes(context -> take(context.getSource(),
                                        LongArgumentType.getLong(context, "amount")))))
                .then(Commands.literal("set")
                        .then(Commands.argument("amount",
                                        LongArgumentType.longArg(0, CreditAccount.MAX_BALANCE))
                                .executes(context -> set(context.getSource(),
                                        LongArgumentType.getLong(context, "amount"))))));

        dispatcher.register(root);
    }

    private static int status(CommandSourceStack source) {
        CreditStorage storage = CreditStorage.get(source.getServer());
        if (!ServerBank.isOn()) {
            source.sendSuccess(() -> Component.translatable("economycore.bank.off")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("economycore.bank.balance",
                format(ServerBank.balance(storage))).withStyle(ChatFormatting.GOLD), false);
        if (!ServerBank.usedBy().isEmpty()) {
            source.sendSuccess(() -> Component.translatable("economycore.bank.used_by",
                    String.join(", ", ServerBank.usedBy().stream().sorted().toList())), false);
        }
        long promised = ServerBank.promised();
        if (promised > 0) {
            source.sendSuccess(() -> Component.translatable("economycore.bank.promised",
                    format(promised)), false);
        }
        source.sendSuccess(() -> Component.translatable(ServerIncome.fundsTheBank()
                ? "economycore.bank.income_in"
                : "economycore.bank.income_out"), false);
        return 1;
    }

    private static int add(CommandSourceStack source, long amount) {
        if (!isOn(source)) {
            return 0;
        }
        CreditStorage storage = CreditStorage.get(source.getServer());
        long kept = ServerBank.credit(storage, amount);
        ServerBank.record(storage, TransactionType.ADMIN_GIVE, kept,
                "seeded by " + source.getTextName());
        StaffAccess.announce(source, Component.translatable("economycore.bank.added",
                        format(kept), format(ServerBank.balance(storage))),
                Component.translatable("economycore.bank.added_by", source.getDisplayName(),
                        format(kept), format(ServerBank.balance(storage))));
        return 1;
    }

    private static int take(CommandSourceStack source, long amount) {
        if (!isOn(source)) {
            return 0;
        }
        CreditStorage storage = CreditStorage.get(source.getServer());

        // Not below what the mods using the bank have promised out of it.
        // Those credits are owed — a casino's live bets — and taking them
        // leaves the bank unable to pay: the payout is then refused and the
        // round handed back. Refusing new promises was never going to be
        // enough on its own, because it is this withdrawal that creates the
        // shortfall.
        long promised = ServerBank.promised();
        long takeable = Math.max(0L, ServerBank.balance(storage) - promised);
        if (amount > takeable) {
            source.sendFailure(Component.translatable("economycore.bank.cannot_take_promised",
                    format(promised), format(takeable)));
            return 0;
        }
        if (!ServerBank.debit(storage, amount)) {
            source.sendFailure(Component.translatable("economycore.bank.cannot_take",
                    format(ServerBank.balance(storage))));
            return 0;
        }
        ServerBank.record(storage, TransactionType.ADMIN_TAKE, -amount,
                "withdrawn by " + source.getTextName());
        StaffAccess.announce(source, Component.translatable("economycore.bank.taken",
                        format(amount), format(ServerBank.balance(storage))),
                Component.translatable("economycore.bank.taken_by", source.getDisplayName(),
                        format(amount), format(ServerBank.balance(storage))));
        return 1;
    }

    private static int set(CommandSourceStack source, long amount) {
        if (!isOn(source)) {
            return 0;
        }
        CreditStorage storage = CreditStorage.get(source.getServer());
        long before = ServerBank.balance(storage);
        if (amount < before) {
            // Lowering it is a withdrawal, and stops where a withdrawal would.
            long floor = Math.min(before, ServerBank.promised());
            if (amount < floor) {
                source.sendFailure(Component.translatable("economycore.bank.cannot_take_promised",
                        format(ServerBank.promised()), format(before - floor)));
                return 0;
            }
            ServerBank.debit(storage, before - amount);
        } else if (amount > before) {
            ServerBank.credit(storage, amount - before);
        }
        long delta = ServerBank.balance(storage) - before;
        if (delta != 0) {
            ServerBank.record(storage, delta > 0 ? TransactionType.ADMIN_GIVE : TransactionType.ADMIN_TAKE,
                    delta, "set by " + source.getTextName());
        }
        StaffAccess.announce(source, Component.translatable("economycore.bank.set", format(amount)),
                Component.translatable("economycore.bank.set_by", source.getDisplayName(), format(amount)));
        return 1;
    }

    private static boolean isOn(CommandSourceStack source) {
        if (ServerBank.isOn()) {
            return true;
        }
        source.sendFailure(Component.translatable("economycore.bank.off"));
        return false;
    }

    private static String format(long credits) {
        return CreditFormat.of(credits);
    }
}
