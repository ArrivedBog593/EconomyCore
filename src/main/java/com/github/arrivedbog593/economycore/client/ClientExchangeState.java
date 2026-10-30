package com.github.arrivedbog593.economycore.client;

import com.github.arrivedbog593.economycore.platform.network.ExchangeCatalogPayload;

import java.util.List;

/**
 * The catalog the server last sent, for the exchange screen to draw.
 * <p>
 * Client only, and deliberately a plain holder: it is display data with no
 * authority whatsoever. Editing it locally changes what a player sees and
 * nothing else because every action is revalidated on the server against the
 * real table.
 */
public final class ClientExchangeState {

    private static List<ExchangeCatalogPayload.Entry> entries = List.of();

    private ClientExchangeState() {
    }

    public static void accept(ExchangeCatalogPayload payload) {
        entries = payload.entries();
    }

    public static List<ExchangeCatalogPayload.Entry> entries() {
        return entries;
    }
}
