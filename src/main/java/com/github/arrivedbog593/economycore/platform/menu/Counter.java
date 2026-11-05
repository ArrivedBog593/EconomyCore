package com.github.arrivedbog593.economycore.platform.menu;

import com.github.arrivedbog593.economycore.platform.item.AdminKeyItem;
import com.github.arrivedbog593.economycore.platform.network.ExchangeCatalogPayload;
import com.github.arrivedbog593.economycore.platform.network.ShopCatalogPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The places a player does business: the exchange, the shop, and the bank
 * where they keep their savings.
 * <p>
 * Every door opens them through here — the block, its clerk, the command —
 * so the screen a player gets is the same one however they came to it, and
 * the catalog always reaches the client before the screen does. Sent after,
 * the first frame renders an empty list.
 */
public enum Counter {

    EXCHANGE("block.economycore.exchange", "economycore.admin.exchange.title"),
    SHOP("block.economycore.shop", "economycore.admin.shop.title"),
    /** No block and no settings: a banker or {@code /bank}, and the same screen for staff. */
    BANK("economycore.my_bank.title", "economycore.my_bank.title");

    private final String titleKey;
    private final String adminTitleKey;

    Counter(String titleKey, String adminTitleKey) {
        this.titleKey = titleKey;
        this.adminTitleKey = adminTitleKey;
    }

    /** Opens the customer's screen, kept open for as long as the anchor allows. */
    public void open(ServerPlayer player, MenuAnchor anchor) {
        sendCatalog(player);
        player.openMenu(new SimpleMenuProvider(
                        (containerId, inventory, who) -> switch (this) {
                            case EXCHANGE -> new ExchangeMenu(containerId, inventory, anchor);
                            case SHOP -> new ShopMenu(containerId, inventory, anchor);
                            case BANK -> new BankMenu(containerId, inventory, anchor);
                        },
                        Component.translatable(titleKey)),
                buffer -> {
                });
    }

    /**
     * What a staff key does at this counter: opens its settings, or says the
     * key is not this player's.
     * <p>
     * A refusal still counts as having used the key. Falling through to the
     * customer's screen would read as the key having worked.
     */
    public void administer(ServerPlayer player, ItemStack key, MenuAnchor anchor) {
        if (this == BANK) {
            // Nothing to configure: a staff member at the banker is a customer.
            open(player, anchor);
            return;
        }
        if (!AdminKeyItem.mayAdminister(player, key)) {
            player.displayClientMessage(
                    Component.translatable("economycore.admin.key_not_yours"), true);
            return;
        }
        sendCatalog(player);
        player.openMenu(new SimpleMenuProvider(
                        (containerId, inventory, who) -> switch (this) {
                            case EXCHANGE -> new AdminExchangeMenu(containerId, inventory, anchor);
                            case SHOP -> new AdminShopMenu(containerId, inventory, anchor);
                            case BANK -> throw new IllegalStateException("The bank has no settings");
                        },
                        Component.translatable(adminTitleKey)),
                buffer -> {
                });
    }

    private void sendCatalog(ServerPlayer player) {
        switch (this) {
            case EXCHANGE -> PacketDistributor.sendToPlayer(player, ExchangeCatalogPayload.current());
            case SHOP -> PacketDistributor.sendToPlayer(player, ShopCatalogPayload.current(player.server));
            case BANK -> {
                // No catalog: the menu carries the two balances itself.
            }
        }
    }
}
