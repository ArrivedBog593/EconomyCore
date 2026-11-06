package com.github.arrivedbog593.economycore.platform;

import com.github.arrivedbog593.economycore.EconomyCore;
import com.github.arrivedbog593.economycore.api.CreditFormat;
import com.github.arrivedbog593.economycore.api.Pocket;
import com.github.arrivedbog593.economycore.api.TransactionType;
import com.github.arrivedbog593.economycore.engine.BankMovement;
import com.github.arrivedbog593.economycore.engine.CreditAccount;
import com.github.arrivedbog593.economycore.engine.InterestTranches;
import com.github.arrivedbog593.economycore.platform.menu.BankMenu;
import com.github.arrivedbog593.economycore.platform.network.BankStatePayload;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Pays interest on savings, if the server switches it on.
 * <p>
 * On a clock of real hours rather than game days, so a server that sleeps
 * through its nights pays no faster, and to everybody with savings whether
 * they are online or not: their balance is in the world's data, not on them.
 * The time it is next due is saved with the world. A server that was off when
 * it fell due pays once on starting, not once for each period it missed; a
 * server switched off for a week is not a savings account.
 * <p>
 * New money, minted. Not replayed after a crash, unlike other movements with
 * no items in them: the due time is saved with the world as well, so a crash
 * that loses a payment rolls the due time back with it, and the payment is
 * made again when the server starts. Replaying it too would pay it twice.
 */
@EventBusSubscriber(modid = EconomyCore.MOD_ID)
public final class SavingsInterest {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** How often the clock is looked at: every ten seconds is close enough for hours. */
    private static final int CHECK_EVERY_TICKS = 200;

    private static int ticks;

    private SavingsInterest() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++ticks < CHECK_EVERY_TICKS) {
            return;
        }
        ticks = 0;
        if (!isOn()) {
            return;
        }
        MinecraftServer server = event.getServer();
        EconomyData data = EconomyData.get(server);
        long now = System.currentTimeMillis();
        long period = periodMillis();
        long due = data.nextInterestAt();
        // Never scheduled, or the period was shortened since: start the clock
        // from now rather than paying at once or waiting out the old period.
        if (due <= 0 || due > now + period) {
            data.setNextInterestAt(now + period);
            return;
        }
        if (now < due) {
            return;
        }
        data.setNextInterestAt(now + period);
        pay(server);
    }

    /** Whether the config switches interest on. */
    public static boolean isOn() {
        return EconomyConfig.SPEC.isLoaded() && EconomyConfig.INTEREST_ENABLED.get();
    }

    public static long periodMillis() {
        return EconomyConfig.INTEREST_PERIOD_HOURS.get() * 3_600_000L;
    }

    /** The tranches the config sets, or empty if it cannot be read, in which case nothing is paid. */
    public static Optional<InterestTranches> tranches() {
        if (!EconomyConfig.SPEC.isLoaded()) {
            return Optional.empty();
        }
        return InterestTranches.parse(List.copyOf(EconomyConfig.INTEREST_TRANCHES.get()));
    }

    /** How long until the next payment, or -1 while interest is off or not yet scheduled. */
    public static long millisUntilNext(MinecraftServer server) {
        long due = EconomyData.get(server).nextInterestAt();
        return isOn() && due > 0 ? Math.max(0, due - System.currentTimeMillis()) : -1;
    }

    private static void pay(MinecraftServer server) {
        Optional<InterestTranches> read = tranches();
        if (read.isEmpty()) {
            LOGGER.warn("[Economy] Interest fell due but interest.tranches in the server config cannot "
                    + "be read; nothing was paid. Each line must look like \"10000000:2\".");
            return;
        }
        InterestTranches tranches = read.get();
        CreditStorage storage = CreditStorage.get(server);
        long now = System.currentTimeMillis();
        int paid = 0;
        long total = 0;
        for (UUID saver : storage.savers()) {
            long balance = storage.balanceOf(saver, Pocket.SAVINGS);
            // Whatever would breach the cap is not paid, rather than the whole payment refused.
            long earned = Math.min(tranches.interestOn(balance), CreditAccount.MAX_BALANCE - balance);
            if (earned <= 0 || !storage.deposit(saver, Pocket.SAVINGS, earned)) {
                continue;
            }
            EconomyEvents.record(storage, TransactionType.SAVINGS_INTEREST, saver, Pocket.SAVINGS, earned,
                    storage.balanceOf(saver, Pocket.SAVINGS), "interest");
            storage.recordBankMovement(saver, new BankMovement(now, BankMovement.Kind.INTEREST, earned));
            ServerPlayer player = server.getPlayerList().getPlayer(saver);
            if (player != null) {
                player.sendSystemMessage(Component.translatable("economycore.interest.paid",
                        CreditFormat.of(earned)));
                if (player.containerMenu instanceof BankMenu) {
                    BankStatePayload.send(player);
                }
            }
            paid++;
            total += earned;
        }
        LOGGER.info("[Economy] Paid {} credits of interest to {} saver(s).", total, paid);
    }
}
