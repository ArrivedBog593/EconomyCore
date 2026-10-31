package com.github.arrivedbog593.economycore.platform.network;

import com.github.arrivedbog593.economycore.EconomyCore;
import com.github.arrivedbog593.economycore.api.CreditFormat;
import com.github.arrivedbog593.economycore.platform.menu.ExchangeMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * A tray on its way to being counted.
 * <p>
 * Carries what the button was showing. The tray's worth is a data slot that
 * resyncs every tick, so the window in which it can go stale is far shorter
 * than the catalog's — but a player watching "+1,234" and being paid
 * something else is the same broken promise, however brief the gap, and the
 * check costs one number.
 *
 * @param expectedValue what the screen said the tray was worth
 */
public record ExchangeConvertPayload(long expectedValue) implements CustomPacketPayload {

    public static final Type<ExchangeConvertPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(EconomyCore.MOD_ID, "exchange_convert"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ExchangeConvertPayload>
            STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, ExchangeConvertPayload::expectedValue,
            ExchangeConvertPayload::new);

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(ExchangeConvertPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            // The tray belongs to the menu, so there is nothing to convert
            // unless this player has one open.
            if (!(player.containerMenu instanceof ExchangeMenu menu)) {
                return;
            }

            ExchangeMenu.ConvertResult result = menu.convertTray(player, payload.expectedValue());
            if (!result.success()) {
                player.sendSystemMessage(Component.translatable(result.failureKey(),
                        CreditFormat.of(result.expected()),
                        CreditFormat.of(result.actual())));
                return;
            }
            player.sendSystemMessage(Component.translatable(
                    "economycore.exchange.converted", CreditFormat.of(result.credits())));
        });
    }
}
