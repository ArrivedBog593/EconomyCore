package com.github.arrivedbog593.economycore.client;

import com.github.arrivedbog593.economycore.platform.network.PursePayload;

/** This player's purse and bank, as last sent, for the corner of the screen. */
public final class ClientPurse {

    private static PursePayload last;
    private static long receivedAt;

    private ClientPurse() {
    }

    public static void accept(PursePayload payload) {
        last = payload;
        receivedAt = System.currentTimeMillis();
    }

    /** Null until the server has said, and again after leaving it: nothing is shown then. */
    public static PursePayload last() {
        return last;
    }

    /** What is left of the merchant's pass, counted down from when the server said. */
    public static long passLeft() {
        return last == null ? 0 : Math.max(0, last.passLeft() - (System.currentTimeMillis() - receivedAt));
    }

    public static void forget() {
        last = null;
    }
}
