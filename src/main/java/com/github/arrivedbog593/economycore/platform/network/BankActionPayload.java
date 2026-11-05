package com.github.arrivedbog593.economycore.platform.network;

import com.github.arrivedbog593.economycore.EconomyCore;
import com.github.arrivedbog593.economycore.engine.CreditAccount;
import com.github.arrivedbog593.economycore.platform.PersonalBank;
import com.github.arrivedbog593.economycore.platform.menu.BankMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * A deposit or a withdrawal asked for at the banker's window.
 * <p>
 * Acted on only while that window is open and the player is still at it, so
 * a client cannot reach its savings from anywhere by sending this without
 * one: the menu's anchor is what decides, exactly as it decides whether the
 * window stays open.
 *
 * @param deposit true to put credits away, false to take them out
 * @param amount  how many, or {@link PersonalBank#ALL}
 */
public record BankActionPayload(boolean deposit, long amount) implements CustomPacketPayload {

    public static final Type<BankActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(EconomyCore.MOD_ID, "bank_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BankActionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, BankActionPayload::deposit,
                    ByteBufCodecs.VAR_LONG, BankActionPayload::amount,
                    BankActionPayload::new);

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(BankActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof BankMenu menu)
                    || !menu.stillValid(player)) {
                return;
            }
            long amount = payload.amount();
            if (amount != PersonalBank.ALL && (amount <= 0 || amount > CreditAccount.MAX_BALANCE)) {
                return;
            }
            PersonalBank.Result result = payload.deposit()
                    ? PersonalBank.deposit(player, amount)
                    : PersonalBank.withdraw(player, amount);
            // Above the hotbar, where the window does not cover it.
            player.displayClientMessage(result.message(), true);
        });
    }
}
