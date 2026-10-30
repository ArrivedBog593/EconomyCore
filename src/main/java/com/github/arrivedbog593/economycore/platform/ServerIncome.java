package com.github.arrivedbog593.economycore.platform;

import com.github.arrivedbog593.economycore.engine.TransactionRecord;
import com.github.arrivedbog593.economycore.engine.TransactionType;

import java.util.Objects;

/**
 * Where the credits the server takes in go.
 * <p>
 * The server collects: the shop's sales, the surcharge on buying an item
 * back, and whatever fees a mod built on this one charges — an auction
 * house's cut, a bazaar's tax. Those are always charged; they are how credits
 * leave players' hands, and a server where credits only ever come in watches
 * its prices climb for good.
 * <p>
 * Where they end up is the only thing that varies. While the {@link ServerBank}
 * is on they are paid into it, so a mod that pays out of the bank — a casino
 * — is funded by them. With the bank off, or with the config set to keep the
 * bank out of it, they leave circulation. Either way the log records where
 * they went.
 */
public final class ServerIncome {

    private ServerIncome() {
    }

    /** Whether income goes to the bank right now, rather than out of circulation. */
    public static boolean fundsTheBank() {
        return ServerBank.isOn() && EconomyConfig.INCOME_TO_BANK.getAsBoolean();
    }

    /**
     * Takes in credits a player has already paid.
     * <p>
     * The caller has taken them from the player and logged that; this is the
     * other half of the movement. What the bank cannot hold past its balance
     * cap leaves circulation like everything else that is not kept.
     *
     * @param type   why — the same type the player's half was logged with
     * @param amount what the player paid
     * @param detail free text for the log
     */
    public static void collect(CreditStorage storage, TransactionType type, long amount, String detail) {
        Objects.requireNonNull(storage, "storage");
        Objects.requireNonNull(type, "type");
        if (amount <= 0) {
            return;
        }
        long kept = 0;
        if (fundsTheBank()) {
            kept = ServerBank.credit(storage, amount);
            if (kept > 0) {
                ServerBank.record(storage, type, kept, detail);
            }
        }
        long destroyed = amount - kept;
        if (destroyed > 0) {
            EconomyEvents.record(storage, type, TransactionRecord.NOBODY, destroyed, 0,
                    detail + " (left circulation)");
        }
    }
}
