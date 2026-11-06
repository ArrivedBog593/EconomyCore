package com.github.arrivedbog593.economycore.client;

import com.github.arrivedbog593.economycore.platform.network.PursePayload;

/** This player's purse and bank, as last sent, for the corner of the screen. */
public final class ClientPurse {

    private static PursePayload last;

    private ClientPurse() {
    }

    public static void accept(PursePayload payload) {
        last = payload;
    }

    /** Null until the server has said, and again after leaving it: nothing is shown then. */
    public static PursePayload last() {
        return last;
    }

    public static void forget() {
        last = null;
    }
}
