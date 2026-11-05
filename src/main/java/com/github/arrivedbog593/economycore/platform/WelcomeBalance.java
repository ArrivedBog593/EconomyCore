package com.github.arrivedbog593.economycore.platform;

import com.github.arrivedbog593.economycore.EconomyCore;
import com.github.arrivedbog593.economycore.api.CreditFormat;
import com.github.arrivedbog593.economycore.api.TransactionType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Starts a newcomer off with the credits the server owner set.
 * <p>
 * Paid once, the first time a player joins a world: a player with no time
 * played there and no account yet. Time played is what says they are new;
 * an account alone would not, since a player who joined last month and
 * never traded has none. The account is the guard against paying twice: if
 * the economy has already touched their account, they are not new to it,
 * whatever their stats say.
 * <p>
 * Logged like any other movement, so crash recovery replays it with the
 * rest.
 */
@EventBusSubscriber(modid = EconomyCore.MOD_ID)
public final class WelcomeBalance {

    private WelcomeBalance() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        long amount = EconomyConfig.WELCOME_BALANCE.getAsLong();
        if (amount <= 0) {
            return;
        }
        if (player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) > 0) {
            return;
        }
        CreditStorage storage = CreditStorage.get(player.server);
        if (storage.hasAccount(player.getUUID()) || !storage.deposit(player.getUUID(), amount)) {
            return;
        }
        long balance = storage.balanceOf(player.getUUID());
        EconomyEvents.record(storage, TransactionType.WELCOME, player.getUUID(), amount, balance,
                "first join");
        player.sendSystemMessage(Component.translatable("economycore.welcome", CreditFormat.of(amount)));
    }
}
