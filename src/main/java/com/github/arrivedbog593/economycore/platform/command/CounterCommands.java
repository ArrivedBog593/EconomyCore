package com.github.arrivedbog593.economycore.platform.command;

import com.github.arrivedbog593.economycore.api.MerchantPass;
import com.github.arrivedbog593.economycore.platform.EconomyConfig;
import com.github.arrivedbog593.economycore.platform.menu.Counter;
import com.github.arrivedbog593.economycore.platform.menu.MenuAnchor;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * {@code /exchange}, {@code /shop} and {@code /bank}: the counters, opened from
 * wherever the player is standing.
 * <p>
 * One of three doors, with the block and its clerk. Each server decides in
 * its config whether this one is open; a server that wants players to walk
 * to a counter closes it, and the command disappears from their tab list
 * rather than refusing them after they typed it.
 */
public final class CounterCommands {

    private CounterCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("exchange")
                .requires(source -> mayOpen(source, Counter.EXCHANGE))
                .executes(context -> open(context.getSource(), Counter.EXCHANGE)));
        dispatcher.register(Commands.literal("shop")
                .requires(source -> mayOpen(source, Counter.SHOP))
                .executes(context -> open(context.getSource(), Counter.SHOP)));
        // A player's own bank, not the server's: that one is staff work, under
        // /economycore bank.
        dispatcher.register(Commands.literal("bank")
                .requires(source -> mayOpen(source, Counter.BANK))
                .executes(context -> open(context.getSource(), Counter.BANK)));
    }

    /**
     * Whether this sender may open a counter by command.
     * <p>
     * Only the server's switch for now. This is the one place to ask, so a
     * later rule — the command as a perk for some players and not others —
     * goes here and nowhere else.
     * <p>
     * That rule is the merchant's pass: with one running, a player may open
     * every counter from anywhere, whatever the switches say.
     */
    public static boolean mayOpen(CommandSourceStack source, Counter counter) {
        ServerPlayer player = source.getPlayer();
        if (player == null || !EconomyConfig.SPEC.isLoaded()) {
            return false;
        }
        boolean open = switch (counter) {
            case EXCHANGE -> EconomyConfig.EXCHANGE_COMMAND.get();
            case SHOP -> EconomyConfig.SHOP_COMMAND.get();
            case BANK -> EconomyConfig.BANK_COMMAND.get();
        };
        return open || MerchantPass.isActive(source.getServer(), player.getUUID());
    }

    private static int open(CommandSourceStack source, Counter counter) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return 0;
        }
        counter.open(player, MenuAnchor.ANYWHERE);
        return 1;
    }

    /**
     * Resends everybody's command list when the server config changes.
     * <p>
     * The list a client autocompletes from is sent once, at login. Without
     * this, switching a command off left it in every tab list until the next
     * login, and switching one on left it missing.
     */
    public static void onConfigReloaded(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() != EconomyConfig.SPEC) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        // Config reloads arrive on the file watcher's thread.
        server.execute(() -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                server.getCommands().sendCommands(player);
            }
        });
    }
}
