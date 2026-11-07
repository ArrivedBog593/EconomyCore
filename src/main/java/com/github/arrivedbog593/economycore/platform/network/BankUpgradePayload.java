package com.github.arrivedbog593.economycore.platform.network;

import com.github.arrivedbog593.economycore.EconomyCore;
import com.github.arrivedbog593.economycore.platform.SavingsLevels;
import com.github.arrivedbog593.economycore.platform.menu.BankMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * Moving the account up a level, asked for at the banker's Upgrades tab.
 * <p>
 * Carries nothing: the server knows which level is next and what it costs,
 * and checks the player can pay rather than trusting the screen. Acted on
 * only with the window open and still in reach, as a deposit is.
 */
public record BankUpgradePayload() implements CustomPacketPayload {

    public static final BankUpgradePayload INSTANCE = new BankUpgradePayload();

    public static final Type<BankUpgradePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(EconomyCore.MOD_ID, "bank_upgrade"));

    public static final StreamCodec<ByteBuf, BankUpgradePayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(BankUpgradePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof BankMenu menu)
                    || !menu.stillValid(player)) {
                return;
            }
            SavingsLevels.Result result = SavingsLevels.upgrade(player);
            player.displayClientMessage(result.message(), true);
            if (result.done()) {
                player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP,
                        SoundSource.PLAYERS, 0.75F, 1.0F);
                BankStatePayload.send(player);
            }
        });
    }
}
