package com.github.arrivedbog593.economycore.platform.network;

import com.github.arrivedbog593.economycore.EconomyCore;
import com.github.arrivedbog593.economycore.engine.BankMovement;
import com.github.arrivedbog593.economycore.engine.InterestTranches;
import com.github.arrivedbog593.economycore.platform.CreditStorage;
import com.github.arrivedbog593.economycore.platform.EconomyData;
import com.github.arrivedbog593.economycore.platform.SavingsInterest;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything the banker's window says besides the two balances: the
 * player's recent movements, when interest is next paid and at what rates,
 * and what dying costs on this server.
 * <p>
 * The balances travel with the menu, every tick. This is sent when the
 * window opens and again after each deposit or withdrawal, which is the only
 * time any of it changes while the window is open.
 *
 * @param recent             oldest first
 * @param millisUntilInterest how long until interest is paid, or -1 while it is off
 * @param interestPeriod     how far apart payments are, in milliseconds
 * @param tranches           the rates; empty while interest is off or unreadable
 * @param deathLossPercent   what share of the purse dying costs
 * @param combatOnly         whether only deaths in combat cost it
 */
public record BankStatePayload(List<BankMovement> recent, long millisUntilInterest, long interestPeriod,
                               List<InterestTranches.Tranche> tranches, int deathLossPercent,
                               boolean combatOnly) implements CustomPacketPayload {

    public static final Type<BankStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(EconomyCore.MOD_ID, "bank_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BankStatePayload> STREAM_CODEC =
            StreamCodec.of(BankStatePayload::write, BankStatePayload::read);

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** This player's state, as the server has it now. */
    public static BankStatePayload of(ServerPlayer player) {
        EconomyData data = EconomyData.get(player.server);
        boolean on = SavingsInterest.isOn();
        List<InterestTranches.Tranche> tranches = on
                ? SavingsInterest.tranches().map(InterestTranches::tranches).orElse(List.of())
                : List.of();
        return new BankStatePayload(
                CreditStorage.get(player.server).recentBankMovements(player.getUUID()),
                SavingsInterest.millisUntilNext(player.server),
                on ? SavingsInterest.periodMillis() : 0,
                tranches,
                data.deathLossPercent(),
                data.deathLossCombatOnly());
    }

    public static void send(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, of(player));
    }

    private static void write(RegistryFriendlyByteBuf buffer, BankStatePayload payload) {
        buffer.writeVarInt(payload.recent.size());
        for (BankMovement movement : payload.recent) {
            buffer.writeLong(movement.time());
            buffer.writeVarInt(movement.kind().ordinal());
            buffer.writeVarLong(movement.amount());
        }
        buffer.writeLong(payload.millisUntilInterest);
        buffer.writeVarLong(payload.interestPeriod);
        buffer.writeVarInt(payload.tranches.size());
        for (InterestTranches.Tranche tranche : payload.tranches) {
            buffer.writeVarLong(tranche.upTo());
            buffer.writeVarLong(tranche.basisPoints());
        }
        buffer.writeVarInt(payload.deathLossPercent);
        buffer.writeBoolean(payload.combatOnly);
    }

    private static BankStatePayload read(RegistryFriendlyByteBuf buffer) {
        int count = Math.min(buffer.readVarInt(), BankMovement.KEPT);
        List<BankMovement> recent = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            recent.add(new BankMovement(buffer.readLong(), BankMovement.Kind.byOrdinal(buffer.readVarInt()),
                    buffer.readVarLong()));
        }
        long until = buffer.readLong();
        long period = buffer.readVarLong();
        int tranchesCount = Math.min(buffer.readVarInt(), 64);
        List<InterestTranches.Tranche> tranches = new ArrayList<>(tranchesCount);
        for (int i = 0; i < tranchesCount; i++) {
            tranches.add(new InterestTranches.Tranche(buffer.readVarLong(), buffer.readVarLong()));
        }
        return new BankStatePayload(recent, until, period, tranches, buffer.readVarInt(), buffer.readBoolean());
    }

    public static void handleOnClient(BankStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                com.github.arrivedbog593.economycore.client.ClientBankState.accept(payload));
    }
}
