package com.github.arrivedbog593.economycore.platform;

import com.github.arrivedbog593.economycore.EconomyCore;
import com.github.arrivedbog593.economycore.api.MerchantPass;
import com.github.arrivedbog593.economycore.platform.network.PursePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Notices when a merchant's pass starts or runs out for a player online.
 * <p>
 * Both change what they may type: {@code /bank} and the rest are left out of
 * the command list a client autocompletes from when they may not use them,
 * and that list is only sent when asked. So whenever a pass starts or runs
 * out, the player is sent the list again, and told, and their screen's
 * purse line is updated with what is left of it.
 */
@EventBusSubscriber(modid = EconomyCore.MOD_ID)
public final class PassWatch {

    /**
     * Every second: a pass lasts days, but one given for seconds should be
     * seen to end when its countdown does, and a look at each player online
     * costs next to nothing.
     */
    private static final int CHECK_EVERY_TICKS = 20;

    /** What each online player's pass was last seen as. */
    private static final Map<UUID, Boolean> LAST_SEEN = new HashMap<>();

    private static int ticks;

    private PassWatch() {
    }

    /** A pass was just used: the command list and the purse line change at once. */
    public static void changed(ServerPlayer player) {
        LAST_SEEN.put(player.getUUID(), MerchantPass.isActive(player.server, player.getUUID()));
        player.server.getCommands().sendCommands(player);
        PursePayload.send(player);
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LAST_SEEN.put(player.getUUID(), MerchantPass.isActive(player.server, player.getUUID()));
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_SEEN.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++ticks < CHECK_EVERY_TICKS) {
            return;
        }
        ticks = 0;
        MinecraftServer server = event.getServer();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            boolean active = MerchantPass.isActive(server, player.getUUID());
            Boolean before = LAST_SEEN.put(player.getUUID(), active);
            if (before != null && before && !active) {
                player.sendSystemMessage(Component.translatable("economycore.pass.expired")
                        .withStyle(ChatFormatting.GOLD));
                server.getCommands().sendCommands(player);
                PursePayload.send(player);
            }
        }
    }

    /**
     * A span as a player reads it, on a pass or a restock, in its two largest
     * units: "3 d 4 h", "5 h 20 min", "12 min 5 s", "40 s". Rounded up to the
     * second, so it never reads zero while something is left.
     */
    public static Component describe(long millis) {
        long seconds = Math.max(1, (millis + 999) / 1_000);
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        if (days > 0) {
            return Component.translatable("economycore.pass.days_hours", days, hours % 24);
        }
        if (hours > 0) {
            return Component.translatable("economycore.pass.hours_minutes", hours, minutes % 60);
        }
        if (minutes > 0) {
            return Component.translatable("economycore.pass.minutes_seconds", minutes, seconds % 60);
        }
        return Component.translatable("economycore.pass.seconds", seconds);
    }
}
