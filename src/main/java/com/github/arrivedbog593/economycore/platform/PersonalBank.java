package com.github.arrivedbog593.economycore.platform;

import com.github.arrivedbog593.economycore.api.CreditFormat;
import com.github.arrivedbog593.economycore.api.CreditMath;
import com.github.arrivedbog593.economycore.api.Pocket;
import com.github.arrivedbog593.economycore.api.Settlement;
import com.github.arrivedbog593.economycore.api.TransactionType;
import com.github.arrivedbog593.economycore.engine.BankMovement;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.math.RoundingMode;

/**
 * A player moving their own credits between purse and savings.
 * <p>
 * One place for it, whichever door it came through — {@code /credits deposit},
 * the banker's screen — so the rules cannot drift apart: only free credits go
 * into savings, never what is promised at a table or in a bid, or saving them
 * would cancel the wager the way spending them elsewhere used to.
 * <p>
 * Each move is one settlement, so both pockets change or neither does, and
 * both lines reach the log together.
 */
public final class PersonalBank {

    /** Stands for "everything there is". */
    public static final long ALL = -1;

    /**
     * What happened, as a message for the player.
     *
     * @param moved how much changed pockets; zero when nothing did
     */
    public record Result(long moved, Component message) {
        public boolean done() {
            return moved > 0;
        }
    }

    private PersonalBank() {
    }

    /**
     * Puts a share of the free purse away: half, say. Worked out here, from
     * the purse as it is now, rather than by the screen from figures that may
     * be a tick old.
     */
    public static Result depositShare(ServerPlayer player, int percent) {
        CreditStorage storage = CreditStorage.get(player.server);
        long free = Commitments.players().spendable(player.getUUID(), storage.balanceOf(player.getUUID()));
        return deposit(player, shareOf(free, percent));
    }

    /** Takes a share of savings out into the purse. */
    public static Result withdrawShare(ServerPlayer player, int percent) {
        long saved = CreditStorage.get(player.server).balanceOf(player.getUUID(), Pocket.SAVINGS);
        return withdraw(player, shareOf(saved, percent));
    }

    /** Rounded down, so a share never asks for more than there is; zero becomes "nothing to move". */
    private static long shareOf(long amount, int percent) {
        long share = CreditMath.percentOf(amount, Math.clamp(percent, 0, CreditMath.PERCENT), RoundingMode.FLOOR);
        return share > 0 ? share : 0;
    }

    /** Puts credits from the purse away in savings; {@link #ALL} for every free one. */
    public static Result deposit(ServerPlayer player, long requested) {
        CreditStorage storage = CreditStorage.get(player.server);
        long balance = storage.balanceOf(player.getUUID());
        long free = Commitments.players().spendable(player.getUUID(), balance);
        long amount = requested == ALL ? free : requested;
        if (amount <= 0) {
            return refused(Component.translatable("economycore.command.credits.deposit_nothing"));
        }
        if (amount > free) {
            return refused(Component.translatable(amount <= balance
                    ? "economycore.command.credits.deposit_committed"
                    : "economycore.command.credits.deposit_short", CreditFormat.of(free)));
        }
        return move(player, storage, Pocket.PURSE, Pocket.SAVINGS, amount,
                TransactionType.SAVINGS_DEPOSIT, BankMovement.Kind.DEPOSIT, "economycore.command.credits.deposited");
    }

    /** Takes credits out of savings into the purse; {@link #ALL} for all of them. */
    public static Result withdraw(ServerPlayer player, long requested) {
        CreditStorage storage = CreditStorage.get(player.server);
        long saved = storage.balanceOf(player.getUUID(), Pocket.SAVINGS);
        long amount = requested == ALL ? saved : requested;
        if (amount <= 0) {
            return refused(Component.translatable("economycore.command.credits.withdraw_nothing"));
        }
        if (amount > saved) {
            return refused(Component.translatable("economycore.command.credits.withdraw_short",
                    CreditFormat.of(saved)));
        }
        return move(player, storage, Pocket.SAVINGS, Pocket.PURSE, amount,
                TransactionType.SAVINGS_WITHDRAW, BankMovement.Kind.WITHDRAWAL, "economycore.command.credits.withdrew");
    }

    private static Result move(ServerPlayer player, CreditStorage storage, Pocket from, Pocket to,
                               long amount, TransactionType type, BankMovement.Kind kind, String doneKey) {
        Settlement.Result result = Settlement.create()
                .move(player.getUUID(), from, -amount, type)
                .move(player.getUUID(), to, amount, type)
                .apply(player.server, type.code());
        if (!result.applied()) {
            return refused(result.refusal() == Settlement.Refusal.BALANCE_CAP
                    ? Component.translatable("economycore.exchange.cap_reached")
                    : Component.translatable("economycore.command.credits.pay_short",
                    CreditFormat.of(storage.balanceOf(player.getUUID(), from))));
        }
        storage.recordBankMovement(player.getUUID(), new BankMovement(System.currentTimeMillis(), kind, amount));
        return new Result(amount, Component.translatable(doneKey, CreditFormat.of(amount),
                CreditFormat.of(storage.balanceOf(player.getUUID())),
                CreditFormat.of(storage.balanceOf(player.getUUID(), Pocket.SAVINGS))));
    }

    private static Result refused(Component message) {
        return new Result(0, message);
    }
}
