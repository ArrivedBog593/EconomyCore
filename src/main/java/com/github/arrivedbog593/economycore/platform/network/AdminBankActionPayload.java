package com.github.arrivedbog593.economycore.platform.network;

import com.github.arrivedbog593.economycore.EconomyCore;
import com.github.arrivedbog593.economycore.platform.AdminNotices;
import com.github.arrivedbog593.economycore.platform.BankSettings;
import com.github.arrivedbog593.economycore.platform.EconomyData;
import com.github.arrivedbog593.economycore.platform.SavingsLevels;
import com.github.arrivedbog593.economycore.platform.item.AdminKeyItem;
import com.github.arrivedbog593.economycore.platform.menu.AdminBankMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;


/**
 * A change asked for at the bank's settings window.
 * <p>
 * Every field is a request. The server checks again who is asking, that the
 * window is still theirs, and that the change leaves the config readable;
 * see {@link BankSettings}.
 *
 * @param kind  what is being asked for
 * @param index the level, for saving and removing; -1 to add one
 * @param value hours, or a percent
 * @param line  the level as a config line, for saving
 */
public record AdminBankActionPayload(int kind, int index, long value, String line)
        implements CustomPacketPayload {

    public static final int KIND_TOGGLE_INTEREST = 0;
    public static final int KIND_PERIOD = 1;
    public static final int KIND_DEATH_LOSS = 2;
    public static final int KIND_TOGGLE_COMBAT = 3;
    public static final int KIND_SAVE_LEVEL = 4;
    public static final int KIND_REMOVE_LEVEL = 5;

    /** Longer than any level line written by hand; the config has no limit, the network does. */
    private static final int MAX_LINE = 2048;

    public static final Type<AdminBankActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(EconomyCore.MOD_ID, "admin_bank_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AdminBankActionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, AdminBankActionPayload::kind,
                    ByteBufCodecs.VAR_INT, AdminBankActionPayload::index,
                    ByteBufCodecs.VAR_LONG, AdminBankActionPayload::value,
                    ByteBufCodecs.stringUtf8(MAX_LINE), AdminBankActionPayload::line,
                    AdminBankActionPayload::new);

    public static AdminBankActionPayload of(int kind, long value) {
        return new AdminBankActionPayload(kind, -1, value, "");
    }

    public static AdminBankActionPayload saveLevel(int index, String line) {
        return new AdminBankActionPayload(KIND_SAVE_LEVEL, index, 0, line);
    }

    public static AdminBankActionPayload removeLevel(int index) {
        return new AdminBankActionPayload(KIND_REMOVE_LEVEL, index, 0, "");
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleOnServer(AdminBankActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !AdminKeyItem.mayAdminister(player, player.getMainHandItem())
                    || !(player.containerMenu instanceof AdminBankMenu)) {
                return;
            }
            MinecraftServer server = player.server;
            switch (payload.kind()) {
                case KIND_TOGGLE_INTEREST -> {
                    boolean on = !BankSettings.interestOn();
                    BankSettings.setInterest(server, on);
                    AdminNotices.announce(player, Component.translatable(on
                            ? "economycore.admin.bank.interest_on" : "economycore.admin.bank.interest_off"));
                }
                case KIND_PERIOD -> {
                    if (payload.value() <= Integer.MAX_VALUE
                            && BankSettings.setPeriodHours(server, (int) payload.value())) {
                        AdminNotices.announce(player, Component.translatable(
                                "economycore.admin.bank.period_set", payload.value()));
                    }
                }
                case KIND_DEATH_LOSS -> {
                    if (payload.value() <= Integer.MAX_VALUE
                            && BankSettings.setDeathLoss(server, (int) payload.value())) {
                        AdminNotices.announce(player, payload.value() == 0
                                ? Component.translatable("economycore.deathloss.off")
                                : Component.translatable("economycore.deathloss.set", payload.value()));
                    }
                }
                case KIND_TOGGLE_COMBAT -> {
                    boolean only = !EconomyData.get(server).deathLossCombatOnly();
                    BankSettings.setDeathLossCombatOnly(server, only);
                    AdminNotices.announce(player, Component.translatable(only
                            ? "economycore.deathloss.combat_on" : "economycore.deathloss.combat_off"));
                }
                case KIND_SAVE_LEVEL -> {
                    BankSettings.Outcome outcome = BankSettings.saveLevel(server, payload.index(), payload.line());
                    if (!outcome.done()) {
                        player.displayClientMessage(outcome.note(), true);
                        return;
                    }
                    AdminNotices.announce(player, Component.translatable(payload.index() < 0
                            ? "economycore.admin.bank.level_added" : "economycore.admin.bank.level_saved",
                            SavingsLevels.nameOf(payload.line().split(";")[0].trim())));
                    if (outcome.note() != null) {
                        player.sendSystemMessage(outcome.note());
                    }
                }
                case KIND_REMOVE_LEVEL -> {
                    BankSettings.Outcome outcome = BankSettings.removeLevel(server, payload.index());
                    if (!outcome.done()) {
                        player.displayClientMessage(outcome.note(), true);
                        return;
                    }
                    AdminNotices.announce(player, Component.translatable("economycore.admin.bank.level_removed"));
                }
                default -> {
                }
            }
        });
    }
}
