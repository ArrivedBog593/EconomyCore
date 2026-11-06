package com.github.arrivedbog593.economycore.platform.network;

import com.github.arrivedbog593.economycore.EconomyCore;
import com.github.arrivedbog593.economycore.api.MerchantPass;
import com.github.arrivedbog593.economycore.api.Pocket;
import com.github.arrivedbog593.economycore.platform.CreditStorage;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * A player's own purse and bank, and when their merchant's pass runs out,
 * for the side of their screen.
 * <p>
 * Sent to that player alone, when they join and whenever either moves. A
 * scoreboard objective would have needed none of this, but every player
 * would see every other player's purse in it, and a score is an {@code int}:
 * it stops at a little over two billion.
 */
public record PursePayload(long purse, long savings, long passLeft) implements CustomPacketPayload {

    public static final Type<PursePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(EconomyCore.MOD_ID, "purse"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PursePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, PursePayload::purse,
                    ByteBufCodecs.VAR_LONG, PursePayload::savings,
                    ByteBufCodecs.VAR_LONG, PursePayload::passLeft,
                    PursePayload::new);

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void send(ServerPlayer player) {
        CreditStorage storage = CreditStorage.get(player.server);
        PacketDistributor.sendToPlayer(player, new PursePayload(
                storage.balanceOf(player.getUUID()),
                storage.balanceOf(player.getUUID(), Pocket.SAVINGS),
                // What is left, not when it ends: the client's clock is not the server's.
                Math.max(0, MerchantPass.expiresAt(player.server, player.getUUID()) - System.currentTimeMillis())));
    }

    public static void handleOnClient(PursePayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                com.github.arrivedbog593.economycore.client.ClientPurse.accept(payload));
    }
}
