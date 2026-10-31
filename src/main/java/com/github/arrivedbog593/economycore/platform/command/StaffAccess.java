package com.github.arrivedbog593.economycore.platform.command;

import com.github.arrivedbog593.economycore.api.StaffRank;
import com.github.arrivedbog593.economycore.platform.EconomyData;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Who may move money that is not their own, and how everyone else who may
 * hears that it moved.
 * <p>
 * Operators, the console, and staff at the administrator rank: the server's
 * bank and players' balances are the server's business, and an administrator
 * already runs everything else the server owns. Moderators run the shop and
 * the exchange and stop there.
 */
public final class StaffAccess {

    private StaffAccess() {
    }

    /** Whether this player is listed at the administrator rank. */
    public static boolean isAdmin(ServerPlayer player) {
        return EconomyData.get(player.server).rankOf(player.getUUID())
                .filter(rank -> rank == StaffRank.ADMIN)
                .isPresent();
    }

    /**
     * Operators and the console always; a player otherwise only at the
     * administrator rank. Asked when the command tree is sent and again when
     * the command runs, so a rank taken away stops working at once.
     */
    public static boolean adminOrOperator(CommandSourceStack source) {
        if (source.hasPermission(2)) {
            return true;
        }
        ServerPlayer player = source.getPlayer();
        return player != null && isAdmin(player);
    }

    /**
     * Tells whoever moved the money what they did, and everyone else who
     * could have that it happened, in a line naming who.
     * <p>
     * Not vanilla's admin broadcast, which reaches operators only — a staff
     * administrator who is not one would never hear that a balance or the
     * bank had been changed — and repeats the actor's "you gave" to people
     * who gave nothing. The server log gets the same line, for when nobody
     * is online.
     */
    public static void announce(CommandSourceStack source, Component toActor, Component toOthers) {
        source.sendSuccess(() -> toActor, false);
        MinecraftServer server = source.getServer();
        ServerPlayer actor = source.getPlayer();
        Component notice = toOthers.copy().withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            if (other != actor && adminOrOperator(other.createCommandSourceStack())) {
                other.sendSystemMessage(notice);
            }
        }
        if (actor != null) {
            server.sendSystemMessage(toOthers);
        }
    }
}
