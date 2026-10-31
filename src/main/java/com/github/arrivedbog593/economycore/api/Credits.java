package com.github.arrivedbog593.economycore.api;

import com.github.arrivedbog593.economycore.engine.CreditAccount;
import com.github.arrivedbog593.economycore.platform.Commitments;
import com.github.arrivedbog593.economycore.platform.CreditStorage;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;

/**
 * Players' credits, as another mod may read them.
 * <p>
 * Reading only. Credits move through a {@link Settlement}, which checks,
 * moves and logs a whole set of them at once; there is deliberately no way
 * here to move one alone, because a movement that is not logged is one crash
 * recovery cannot see.
 */
public final class Credits {

    /** The most any account may hold. */
    public static final long MAX_BALANCE = CreditAccount.MAX_BALANCE;

    private Credits() {
    }

    /** Whether an account holding {@code balance} could take {@code amount} more. */
    public static boolean canHold(long balance, long amount) {
        return CreditAccount.canHold(balance, amount);
    }

    /** What this player holds, promised credits included. */
    public static long balance(MinecraftServer server, UUID playerId) {
        return CreditStorage.get(server).balanceOf(playerId);
    }

    /**
     * What this player may spend: their balance less everything they have
     * promised. Anything that takes credits should ask this, not the balance.
     */
    public static long spendable(MinecraftServer server, UUID playerId) {
        return Commitments.spendable(server, playerId);
    }

    /** The server-wide record of promises, to report a mod's own into. */
    public static PlayerCommitments commitments() {
        return Commitments.players();
    }
}
