package com.github.arrivedbog593.economycore.platform.network;

import com.github.arrivedbog593.economycore.EconomyCore;
import com.github.arrivedbog593.economycore.engine.CreditAccount;
import com.github.arrivedbog593.economycore.platform.AdminNotices;
import com.github.arrivedbog593.economycore.platform.EconomyData;
import com.github.arrivedbog593.economycore.platform.EconomyEvents;
import com.github.arrivedbog593.economycore.platform.ShopEntry;
import com.github.arrivedbog593.economycore.platform.item.AdminKeyItem;
import com.github.arrivedbog593.economycore.platform.menu.AdminShopMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Adding, repricing, and removing what the shop sells, and the terms each
 * entry is sold on: on or off, its stock, its restock, its limit a day.
 * <p>
 * A payload rather than a menu button because two of the three carry a price,
 * and {@code clickMenuButton} has only an integer to work with. Packing a
 * price into a button id would have capped what a shop could charge at
 * whatever the encoding left over.
 * <p>
 * Every field is a request. The server re-checks who is asking what they are
 * holding and whether the entry still exists, because a screen open for a
 * while is a screen whose idea of the catalog may be out of date.
 *
 * @param kind   what is being asked for
 * @param number the catalog position for repricing and removing
 * @param price  credits for adding and repricing; for the terms, the new
 *               figure: a stock (or {@link ShopEntry#UNLIMITED}), hours, a limit
 */
public record AdminShopActionPayload(int kind, int number, long price)
        implements CustomPacketPayload {

    public static final int KIND_ADD = 0;
    public static final int KIND_REPRICE = 1;
    public static final int KIND_REMOVE = 2;
    public static final int KIND_ENABLE = 3;
    public static final int KIND_DISABLE = 4;
    public static final int KIND_STOCK = 5;
    public static final int KIND_RESTOCK = 6;
    public static final int KIND_LIMIT = 7;

    /** The most hours apart a restock may be set, a year, as the command allows. */
    private static final long MAX_RESTOCK_HOURS = 24 * 365;

    /** The highest limit a day, as the command allows. */
    private static final long MAX_DAILY_LIMIT = 1_000_000;

    public static final Type<AdminShopActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(EconomyCore.MOD_ID, "admin_shop_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AdminShopActionPayload>
            STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AdminShopActionPayload::kind,
            ByteBufCodecs.VAR_INT, AdminShopActionPayload::number,
            ByteBufCodecs.VAR_LONG, AdminShopActionPayload::price,
            AdminShopActionPayload::new);

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static AdminShopActionPayload add(long price) {
        return new AdminShopActionPayload(KIND_ADD, 0, price);
    }

    public static AdminShopActionPayload reprice(int number, long price) {
        return new AdminShopActionPayload(KIND_REPRICE, number, price);
    }

    public static AdminShopActionPayload remove(int number) {
        return new AdminShopActionPayload(KIND_REMOVE, number, 0);
    }

    /** One of the terms an entry is sold on: on or off, stock, restock hours, limit a day. */
    public static AdminShopActionPayload terms(int kind, int number, long value) {
        return new AdminShopActionPayload(kind, number, value);
    }

    public static void handleOnServer(AdminShopActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            // Having the screen open is not permission. It could have been
            // revoked since it opened, and the packet could have been sent by
            // something that never opened one at all.
            if (!AdminKeyItem.mayAdminister(player, player.getMainHandItem())) {
                return;
            }
            if (!(player.containerMenu instanceof AdminShopMenu menu)) {
                return;
            }

            switch (payload.kind()) {
                case KIND_ADD -> add(payload, player, menu);
                case KIND_REPRICE -> reprice(payload, player);
                case KIND_REMOVE -> remove(payload, player);
                case KIND_ENABLE, KIND_DISABLE, KIND_STOCK, KIND_RESTOCK, KIND_LIMIT -> terms(payload, player);
                default -> {
                }
            }
            // No catalog is sent from here. Each of the three above rebuilds
            // the economy when it changes something, and that refreshes every
            // open screen, this player's included. Sending one more here
            // would have left the other administrator looking at the version
            // that no longer exists.
        });
    }

    private static void add(AdminShopActionPayload payload, ServerPlayer player,
                            AdminShopMenu menu) {
        ItemStack listing = menu.held();
        if (listing.isEmpty() || payload.price() < 1) {
            return;
        }
        // Copied, not consumed. The stack only describes the sale; taking it
        // would cost an admin the enchanted sword they used to set one up.
        int number = EconomyData.get(player.server).addShopEntry(listing.copy(), payload.price());
        EconomyEvents.economy().rebuild(player.server);
        menu.clearInput();

        AdminNotices.announce(player, Component.translatable(
                "economycore.command.shop.added",
                listing.getHoverName(), payload.price(), number));
    }

    private static void reprice(AdminShopActionPayload payload, ServerPlayer player) {
        if (payload.price() < 1) {
            return;
        }
        Optional<ShopEntry> updated = EconomyData.get(player.server)
                .repriceShopEntry(payload.number(), payload.price());
        if (updated.isEmpty()) {
            player.displayClientMessage(Component.translatable(
                    "economycore.command.shop.no_such_entry", payload.number()), true);
            return;
        }
        EconomyEvents.economy().rebuild(player.server);
        AdminNotices.announce(player, Component.translatable(
                "economycore.command.shop.repriced",
                updated.get().stack().getHoverName(), payload.price(),
                payload.number()));
    }

    /**
     * Changes the terms an entry is sold on. Each figure is checked against
     * what the matching command accepts, so the window can do nothing a
     * command could not.
     */
    private static void terms(AdminShopActionPayload payload, ServerPlayer player) {
        long value = payload.price();
        long now = System.currentTimeMillis();
        UnaryOperator<ShopEntry> change;
        String key;
        switch (payload.kind()) {
            case KIND_ENABLE -> {
                change = entry -> entry.withEnabled(true);
                key = "economycore.command.shop.enabled";
            }
            case KIND_DISABLE -> {
                change = entry -> entry.withEnabled(false);
                key = "economycore.command.shop.disabled";
            }
            case KIND_STOCK -> {
                if (value < ShopEntry.UNLIMITED || value > CreditAccount.MAX_BALANCE) {
                    return;
                }
                change = entry -> entry.withStock(value, now);
                key = value == ShopEntry.UNLIMITED
                        ? "economycore.command.shop.stock_unlimited" : "economycore.command.shop.stock_set";
            }
            case KIND_RESTOCK -> {
                if (value < 0 || value > MAX_RESTOCK_HOURS) {
                    return;
                }
                change = entry -> entry.withRestock((int) value, now);
                key = value == 0 ? "economycore.command.shop.restock_off" : "economycore.command.shop.restock_set";
            }
            case KIND_LIMIT -> {
                if (value < 0 || value > MAX_DAILY_LIMIT) {
                    return;
                }
                change = entry -> entry.withDailyLimit((int) value);
                key = value == 0 ? "economycore.command.shop.limit_off" : "economycore.command.shop.limit_set";
            }
            default -> {
                return;
            }
        }
        Optional<ShopEntry> updated = EconomyData.get(player.server).updateShopEntry(payload.number(), change);
        if (updated.isEmpty()) {
            player.displayClientMessage(Component.translatable(
                    "economycore.command.shop.no_such_entry", payload.number()), true);
            return;
        }
        EconomyEvents.economy().rebuild(player.server);
        AdminNotices.announce(player, Component.translatable(key,
                updated.get().stack().getHoverName(), payload.number(), value));
    }

    private static void remove(AdminShopActionPayload payload, ServerPlayer player) {
        Optional<ShopEntry> removed = EconomyData.get(player.server)
                .removeShopEntry(payload.number());
        if (removed.isEmpty()) {
            player.displayClientMessage(Component.translatable(
                    "economycore.command.shop.no_such_entry", payload.number()), true);
            return;
        }
        EconomyEvents.economy().rebuild(player.server);
        // Announced rather than whispered. The other administrator's numbers
        // just moved: everything after this entry shifts up one, and their
        // screen redrawing is different from being told why.
        AdminNotices.announce(player, Component.translatable(
                "economycore.command.shop.removed",
                removed.get().stack().getHoverName(), payload.number()));
    }
}