package com.github.arrivedbog593.economycore.client;

import com.github.arrivedbog593.economycore.platform.network.AdminBankStatePayload;

/**
 * The bank's settings as the server last sent them, for its settings window.
 * <p>
 * Display data with no authority, like the shop's catalog. A new instance
 * arrives with every change, so the window can tell by identity that the
 * settings moved underneath it.
 */
public final class ClientAdminBankState {

    private static AdminBankStatePayload state;

    private ClientAdminBankState() {
    }

    public static void accept(AdminBankStatePayload payload) {
        state = payload;
    }

    /** The last settings sent, or null before any arrived. */
    public static AdminBankStatePayload state() {
        return state;
    }
}
