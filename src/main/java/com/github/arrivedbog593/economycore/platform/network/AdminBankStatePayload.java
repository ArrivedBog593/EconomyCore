package com.github.arrivedbog593.economycore.platform.network;

import com.github.arrivedbog593.economycore.EconomyCore;
import com.github.arrivedbog593.economycore.engine.BankLevels;
import com.github.arrivedbog593.economycore.engine.InterestTranches;
import com.github.arrivedbog593.economycore.platform.BankSettings;
import com.github.arrivedbog593.economycore.platform.EconomyData;
import com.github.arrivedbog593.economycore.platform.SavingsLevels;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The bank's settings, for its settings window: interest, what dying costs,
 * and the account levels.
 * <p>
 * Sent when the window opens and after every change to any of it, to every
 * admin with the window open, so two of them never edit different versions.
 *
 * @param interestOn  whether savings earn interest
 * @param periodHours how many real hours apart it is paid
 * @param deathLoss   what share of the purse dying costs
 * @param combatOnly  whether only deaths in combat cost it
 * @param readable    false if the levels in the config cannot be read, and so cannot be edited here
 * @param levels      the levels, from the first; empty for a bank with no limit
 */
public record AdminBankStatePayload(boolean interestOn, int periodHours, int deathLoss, boolean combatOnly,
                                    boolean readable, List<BankLevels.Level> levels)
        implements CustomPacketPayload {

    public static final Type<AdminBankStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(EconomyCore.MOD_ID, "admin_bank_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AdminBankStatePayload> STREAM_CODEC =
            StreamCodec.of(AdminBankStatePayload::write, AdminBankStatePayload::read);

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** The settings as they are now. */
    public static AdminBankStatePayload current() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        EconomyData data = EconomyData.get(server);
        boolean empty = BankSettings.levelLines().isEmpty();
        Optional<BankLevels> levels = SavingsLevels.levels();
        return new AdminBankStatePayload(
                BankSettings.interestOn(),
                BankSettings.periodHours(),
                data.deathLossPercent(),
                data.deathLossCombatOnly(),
                empty || levels.isPresent(),
                levels.map(BankLevels::levels).orElse(List.of()));
    }

    private static void write(RegistryFriendlyByteBuf buffer, AdminBankStatePayload payload) {
        buffer.writeBoolean(payload.interestOn);
        buffer.writeVarInt(payload.periodHours);
        buffer.writeVarInt(payload.deathLoss);
        buffer.writeBoolean(payload.combatOnly);
        buffer.writeBoolean(payload.readable);
        buffer.writeVarInt(payload.levels.size());
        for (BankLevels.Level level : payload.levels) {
            buffer.writeUtf(level.name());
            buffer.writeVarLong(level.cap());
            buffer.writeVarLong(level.credits());
            buffer.writeVarInt(level.items().size());
            for (BankLevels.ItemCost cost : level.items()) {
                buffer.writeUtf(cost.item());
                buffer.writeVarInt(cost.count());
            }
            buffer.writeUtf(level.tranches() == null ? "" : level.tranches().toText());
        }
    }

    private static AdminBankStatePayload read(RegistryFriendlyByteBuf buffer) {
        boolean interestOn = buffer.readBoolean();
        int periodHours = buffer.readVarInt();
        int deathLoss = buffer.readVarInt();
        boolean combatOnly = buffer.readBoolean();
        boolean readable = buffer.readBoolean();
        int count = Math.min(buffer.readVarInt(), 256);
        List<BankLevels.Level> levels = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String name = buffer.readUtf();
            long cap = buffer.readVarLong();
            long credits = buffer.readVarLong();
            int itemCount = Math.min(buffer.readVarInt(), 64);
            List<BankLevels.ItemCost> items = new ArrayList<>(itemCount);
            for (int j = 0; j < itemCount; j++) {
                items.add(new BankLevels.ItemCost(buffer.readUtf(), buffer.readVarInt()));
            }
            String tranches = buffer.readUtf();
            levels.add(new BankLevels.Level(name, cap, credits, items,
                    tranches.isEmpty() ? null : InterestTranches.parse(List.of(tranches.split(","))).orElse(null)));
        }
        return new AdminBankStatePayload(interestOn, periodHours, deathLoss, combatOnly, readable, levels);
    }

    public static void handleOnClient(AdminBankStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() ->
                com.github.arrivedbog593.economycore.client.ClientAdminBankState.accept(payload));
    }
}
