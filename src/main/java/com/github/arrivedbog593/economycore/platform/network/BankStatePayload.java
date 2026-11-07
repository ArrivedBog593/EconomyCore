package com.github.arrivedbog593.economycore.platform.network;

import com.github.arrivedbog593.economycore.EconomyCore;
import com.github.arrivedbog593.economycore.engine.BankLevels;
import com.github.arrivedbog593.economycore.engine.BankMovement;
import com.github.arrivedbog593.economycore.engine.InterestTranches;
import com.github.arrivedbog593.economycore.platform.CreditStorage;
import com.github.arrivedbog593.economycore.platform.EconomyData;
import com.github.arrivedbog593.economycore.platform.SavingsInterest;
import com.github.arrivedbog593.economycore.platform.SavingsLevels;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Everything the banker's window says besides the two balances: the
 * player's recent movements, when interest is next paid and at what rates,
 * what dying costs on this server, and the account's level and the next.
 * <p>
 * The balances travel with the menu, every tick. This is sent when the
 * window opens and again after each deposit, withdrawal or upgrade, which is
 * the only time any of it changes while the window is open.
 *
 * @param recent             oldest first
 * @param millisUntilInterest how long until interest is paid, or -1 while it is off
 * @param interestPeriod     how far apart payments are, in milliseconds
 * @param tranches           the rates this player's account earns; empty while interest is off or unreadable
 * @param deathLossPercent   what share of the purse dying costs
 * @param combatOnly         whether only deaths in combat cost it
 * @param level              the account's level, or null with no levels configured
 */
public record BankStatePayload(List<BankMovement> recent, long millisUntilInterest, long interestPeriod,
                               List<InterestTranches.Tranche> tranches, int deathLossPercent,
                               boolean combatOnly, @Nullable Level level) implements CustomPacketPayload {

    /**
     * Where the account stands among the levels.
     *
     * @param name  the level's name, for its translation
     * @param place where it is, from 0
     * @param count how many levels there are
     * @param cap   the most it holds
     * @param next  the level after it, or null at the top
     */
    public record Level(String name, int place, int count, long cap, @Nullable Next next) {
    }

    /**
     * The next level and what it costs.
     *
     * @param maxInterest the most it pays at once, or -1 while interest is off
     */
    public record Next(String name, long cap, long credits, List<BankLevels.ItemCost> items, long maxInterest) {
    }

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
        CreditStorage storage = CreditStorage.get(player.server);
        boolean on = SavingsInterest.isOn();
        List<InterestTranches.Tranche> tranches = on
                ? SavingsLevels.tranchesOf(storage, player.getUUID()).map(InterestTranches::tranches).orElse(List.of())
                : List.of();
        return new BankStatePayload(
                storage.recentBankMovements(player.getUUID()),
                SavingsInterest.millisUntilNext(player.server),
                on ? SavingsInterest.periodMillis() : 0,
                tranches,
                data.deathLossPercent(),
                data.deathLossCombatOnly(),
                levelOf(storage, player.getUUID(), on));
    }

    private static @Nullable Level levelOf(CreditStorage storage, UUID player, boolean interestOn) {
        Optional<BankLevels> read = SavingsLevels.levels();
        if (read.isEmpty()) {
            return null;
        }
        BankLevels levels = read.get();
        int place = levels.placeOf(storage.bankLevel(player));
        BankLevels.Level at = levels.level(place);
        Next next = levels.next(place).map(level -> {
            Optional<InterestTranches> pays = level.tranches() != null
                    ? Optional.of(level.tranches()) : SavingsInterest.tranches();
            return new Next(level.name(), level.cap(), level.credits(), level.items(),
                    interestOn ? pays.map(InterestTranches::maxInterest).orElse(0L) : -1);
        }).orElse(null);
        return new Level(at.name(), place, levels.levels().size(), at.cap(), next);
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
            buffer.writeVarLong(tranche.rate());
        }
        buffer.writeVarInt(payload.deathLossPercent);
        buffer.writeBoolean(payload.combatOnly);
        buffer.writeBoolean(payload.level != null);
        if (payload.level != null) {
            Level level = payload.level;
            buffer.writeUtf(level.name);
            buffer.writeVarInt(level.place);
            buffer.writeVarInt(level.count);
            buffer.writeVarLong(level.cap);
            buffer.writeBoolean(level.next != null);
            if (level.next != null) {
                Next next = level.next;
                buffer.writeUtf(next.name);
                buffer.writeVarLong(next.cap);
                buffer.writeVarLong(next.credits);
                buffer.writeVarInt(next.items.size());
                for (BankLevels.ItemCost cost : next.items) {
                    buffer.writeUtf(cost.item());
                    buffer.writeVarInt(cost.count());
                }
                buffer.writeLong(next.maxInterest);
            }
        }
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
        int deathLoss = buffer.readVarInt();
        boolean combatOnly = buffer.readBoolean();
        Level level = null;
        if (buffer.readBoolean()) {
            String name = buffer.readUtf();
            int place = buffer.readVarInt();
            int levels = buffer.readVarInt();
            long cap = buffer.readVarLong();
            Next next = null;
            if (buffer.readBoolean()) {
                String nextName = buffer.readUtf();
                long nextCap = buffer.readVarLong();
                long credits = buffer.readVarLong();
                int itemCount = Math.min(buffer.readVarInt(), 64);
                List<BankLevels.ItemCost> items = new ArrayList<>(itemCount);
                for (int i = 0; i < itemCount; i++) {
                    items.add(new BankLevels.ItemCost(buffer.readUtf(), buffer.readVarInt()));
                }
                next = new Next(nextName, nextCap, credits, items, buffer.readLong());
            }
            level = new Level(name, place, levels, cap, next);
        }
        return new BankStatePayload(recent, until, period, tranches, deathLoss, combatOnly, level);
    }

    public static void handleOnClient(BankStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                com.github.arrivedbog593.economycore.client.ClientBankState.accept(payload));
    }
}
