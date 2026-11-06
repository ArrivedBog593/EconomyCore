package com.github.arrivedbog593.economycore.platform;

import com.github.arrivedbog593.economycore.EconomyCore;
import com.github.arrivedbog593.economycore.platform.network.PursePayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.UUID;

/**
 * Keeps the purse in the corner of each player's screen up to date.
 * <p>
 * Sent on joining, and then only to players whose purse or bank moved: the
 * store notes every account that changes, and once a tick those that did and
 * are online are sent theirs. A bet, a sale and a deposit in the same tick
 * cost one packet.
 */
@EventBusSubscriber(modid = EconomyCore.MOD_ID)
public final class PurseSync {

    private PurseSync() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PursePayload.send(player);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        for (UUID moved : CreditStorage.get(server).drainTouched()) {
            ServerPlayer player = server.getPlayerList().getPlayer(moved);
            if (player != null) {
                PursePayload.send(player);
            }
        }
    }
}
