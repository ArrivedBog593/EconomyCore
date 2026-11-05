package com.github.arrivedbog593.economycore.platform;

import com.github.arrivedbog593.economycore.EconomyCore;
import com.github.arrivedbog593.economycore.api.CreditFormat;
import com.github.arrivedbog593.economycore.api.CreditMath;
import com.github.arrivedbog593.economycore.api.TransactionType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.math.RoundingMode;

/**
 * Takes a share of a player's purse when they die, if the server asks for
 * one: what makes the bank worth walking to.
 * <p>
 * Only the purse, and only what is free in it. Savings are the safe place by
 * definition, and credits promised at a game table are owed to a round that
 * has not settled; taking them would leave the round unable to pay, and it
 * would be handed back as if it had never happened.
 * <p>
 * What the player keeps is rounded to the nearest credit, halves up: with
 * half of 15 to lose, they keep 8 and lose 7. Rounding what is kept rather
 * than what is lost is what puts a half in the player's favor.
 * <p>
 * What is lost is the server's income, like the shop's sales: into the bank
 * while it takes income, out of circulation otherwise. Not recoverable: a
 * crash rolls the world back to before the death, and the credits must come
 * back with it.
 * <p>
 * Nothing is taken under {@code keepInventory}, which says deaths on this
 * server cost nothing, and nothing when another mod cancels the death — a
 * totem, say — which is why this listens last.
 */
@EventBusSubscriber(modid = EconomyCore.MOD_ID)
public final class DeathLoss {

    /** The most that can be set: the whole free purse. */
    public static final int MAX_PERCENT = 100;

    private DeathLoss() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        int percent = EconomyData.get(player.server).deathLossPercent();
        if (percent <= 0 || player.serverLevel().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
            return;
        }
        long lost = lossFrom(Commitments.spendable(player.server, player.getUUID()), percent);
        if (lost <= 0) {
            return;
        }
        CreditStorage storage = CreditStorage.get(player.server);
        if (!storage.withdraw(player.getUUID(), lost)) {
            return;
        }
        String detail = "death of " + player.getGameProfile().getName();
        EconomyEvents.record(storage, TransactionType.DEATH_LOSS, player.getUUID(), -lost,
                storage.balanceOf(player.getUUID()), detail);
        ServerIncome.collect(storage, TransactionType.DEATH_LOSS, lost, detail);
        player.sendSystemMessage(Component.translatable("economycore.death_loss", CreditFormat.of(lost)));
    }

    /** The share of a free purse that dying costs: whatever is not kept, the kept part rounded half up. */
    static long lossFrom(long free, int percent) {
        long kept = CreditMath.percentOf(free, CreditMath.PERCENT - percent, RoundingMode.HALF_UP);
        return free - kept;
    }
}
