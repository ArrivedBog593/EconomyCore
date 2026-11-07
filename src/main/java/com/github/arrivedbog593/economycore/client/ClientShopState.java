package com.github.arrivedbog593.economycore.client;

import com.github.arrivedbog593.economycore.platform.network.ShopCatalogPayload;

import java.util.List;

/**
 * The shop catalog the server last sent, for the screen to draw.
 * <p>
 * Client only, and deliberately a plain holder: it is display data with no
 * authority. Editing it locally changes what a player sees and nothing else,
 * because prices are looked up again on the server for every purchase.
 */
public final class ClientShopState {

    private static List<ShopCatalogPayload.Entry> entries = List.of();

    /** When the catalog arrived, so a restock counts down without the server. */
    private static long receivedAt;

    private ClientShopState() {
    }

    public static void accept(ShopCatalogPayload payload) {
        entries = payload.entries();
        receivedAt = System.currentTimeMillis();
    }

    public static List<ShopCatalogPayload.Entry> entries() {
        return entries;
    }

    /** How long until this entry fills back up, counted down from when the catalog came. */
    public static long restockLeft(ShopCatalogPayload.Entry entry) {
        return Math.max(0, entry.restockIn() - (System.currentTimeMillis() - receivedAt));
    }
}
