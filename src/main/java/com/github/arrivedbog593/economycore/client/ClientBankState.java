package com.github.arrivedbog593.economycore.client;

import com.github.arrivedbog593.economycore.platform.network.BankStatePayload;

/**
 * What the server last said about this player's bank, besides the balances.
 * <p>
 * Display data with no authority, like the shop's catalog. The time it
 * arrived is kept with it, so the countdown to interest runs on while the
 * window stays open without asking the server again.
 */
public final class ClientBankState {

    private static BankStatePayload state;
    private static long receivedAt;

    private ClientBankState() {
    }

    public static void accept(BankStatePayload payload) {
        state = payload;
        receivedAt = System.currentTimeMillis();
    }

    /** The last state sent, or null before any arrived. */
    public static BankStatePayload state() {
        return state;
    }

    /** How long until interest is paid, counted down from when the state arrived; -1 while it is off. */
    public static long millisUntilInterest() {
        if (state == null || state.millisUntilInterest() < 0) {
            return -1;
        }
        return Math.max(0, state.millisUntilInterest() - (System.currentTimeMillis() - receivedAt));
    }
}
