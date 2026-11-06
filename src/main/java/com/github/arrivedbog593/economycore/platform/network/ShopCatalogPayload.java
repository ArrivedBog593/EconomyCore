package com.github.arrivedbog593.economycore.platform.network;

import com.github.arrivedbog593.economycore.EconomyCore;
import com.github.arrivedbog593.economycore.platform.EconomyData;
import net.minecraft.world.item.ItemStack;
import com.github.arrivedbog593.economycore.platform.ShopEntry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * What the shop sells and for how much, sent as a player opens it.
 * <p>
 * Sent rather than cached because the catalog is edited live with commands.
 * A client holding yesterday's prices would show a bargain that the server
 * then refuses, which reads as a bug rather than as staleness.
 * <p>
 * Ordering is cheapest first, unlike the exchange: someone browsing a shop is
 * usually looking for what they can afford, not for what they cannot.
 *
 * @param entries item id and price, cheapest first
 */
public record ShopCatalogPayload(List<Entry> entries) implements CustomPacketPayload {

    /** A catalog larger than this is a mistake, not a shop. */
    public static final int MAX_ENTRIES = 512;

    public static final Type<ShopCatalogPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(EconomyCore.MOD_ID, "shop_catalog"));

    /**
     * One thing for sale.
     * <p>
     * The whole stack goes over the wire, so the screen can render the
     * enchantment glint and the real tooltip rather than a name and a guess.
     *
     * @param number the entry's place in the catalog, counting from one, and
     *               what a purchase quotes back. It shifts when an admin
     *               removes something, so the server always re-reads the
     *               catalog rather than trusting what the client believed
     * @param stack       exactly what a purchase delivers
     * @param price       credits per purchase
     * @param enabled     whether it can be bought; off, it is shown but not sold
     * @param stock       purchases left, or {@link ShopEntry#UNLIMITED}
     * @param limitLeft   purchases this player has left today, or -1 for no limit
     * @param restockIn   milliseconds until it fills back up, or -1 if it never does
     * @param capacity     what a restock fills up to, or {@link ShopEntry#UNLIMITED}; for the staff's window
     * @param restockHours how many hours apart it fills up, zero for never; for the staff's window
     * @param dailyLimit   purchases each player may make a day, zero for none; for the staff's window
     */
    public record Entry(int number, ItemStack stack, long price, boolean enabled, long stock,
                        int limitLeft, long restockIn, long capacity, int restockHours, int dailyLimit) {

        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC =
                StreamCodec.of((buffer, entry) -> {
                    buffer.writeVarInt(entry.number());
                    ItemStack.STREAM_CODEC.encode(buffer, entry.stack());
                    buffer.writeVarLong(entry.price());
                    buffer.writeBoolean(entry.enabled());
                    buffer.writeLong(entry.stock());
                    buffer.writeInt(entry.limitLeft());
                    buffer.writeLong(entry.restockIn());
                    buffer.writeLong(entry.capacity());
                    buffer.writeVarInt(entry.restockHours());
                    buffer.writeVarInt(entry.dailyLimit());
                }, buffer -> new Entry(buffer.readVarInt(), ItemStack.STREAM_CODEC.decode(buffer),
                        buffer.readVarLong(), buffer.readBoolean(), buffer.readLong(), buffer.readInt(),
                        buffer.readLong(), buffer.readLong(), buffer.readVarInt(), buffer.readVarInt()));

        /** Whether it can be put in a cart: switched on, and not sold out. */
        public boolean onSale() {
            return enabled && (stock == ShopEntry.UNLIMITED || stock > 0) && limitLeft != 0;
        }

        /** The most of it that can go in one cart: what is left, and what this player may still buy today. */
        public long mostPerCart() {
            long most = stock == ShopEntry.UNLIMITED ? Long.MAX_VALUE : stock;
            return limitLeft < 0 ? most : Math.min(most, limitLeft);
        }
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, ShopCatalogPayload> STREAM_CODEC =
            StreamCodec.composite(
                    Entry.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ENTRIES)),
                    ShopCatalogPayload::entries,
                    ShopCatalogPayload::new);

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Snapshots the live shop as one player sees it: their own limits a day
     * are part of it, so each player is sent their own.
     * <p>
     * Restocks that fell due are counted first, so a shop opened the hour it
     * fills shows it full.
     */
    public static ShopCatalogPayload current(ServerPlayer player) {
        EconomyData data = EconomyData.get(player.server);
        long now = System.currentTimeMillis();
        data.restockShop(now);
        List<ShopEntry> sold = data.shopEntries();
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < sold.size() && i < MAX_ENTRIES; i++) {
            ShopEntry entry = sold.get(i);
            int limitLeft = entry.dailyLimit() <= 0 ? -1
                    : Math.max(0, entry.dailyLimit() - data.boughtToday(entry.id(), player.getUUID(), now));
            long restockIn = entry.nextRestock() <= 0 ? -1 : Math.max(0, entry.nextRestock() - now);
            entries.add(new Entry(i + 1, entry.prototype(), entry.price(), entry.enabled(), entry.stock(),
                    limitLeft, restockIn, entry.capacity(), entry.restockHours(), entry.dailyLimit()));
        }
        // Sent in catalog order. How a player wants it sorted is their own
        // business and the screen handles it locally, so the packet stays the
        // plain truth about what the shop holds.
        return new ShopCatalogPayload(List.copyOf(entries));
    }

    /** Handed to the client's holder for the screen to read. */
    public static void handleOnClient(ShopCatalogPayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                com.github.arrivedbog593.economycore.client.ClientShopState.accept(payload));
    }
}
