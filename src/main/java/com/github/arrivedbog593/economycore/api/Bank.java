package com.github.arrivedbog593.economycore.api;

import com.github.arrivedbog593.economycore.platform.CreditStorage;
import com.github.arrivedbog593.economycore.platform.EconomyData;
import com.github.arrivedbog593.economycore.platform.ServerBank;
import net.minecraft.server.MinecraftServer;

import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * The server's own account, for a mod that pays out of it.
 * <p>
 * Off unless a mod asks for it. A mod that pays out of the bank — a casino
 * covering its tables — switches it on with {@link #enable} while it loads,
 * and moves money in and out of it with a {@link Settlement}, naming
 * {@link #ACCOUNT}. One bank serves every mod that uses it, so a mod that
 * needs part of it kept back says how much with {@link #promise}.
 */
public final class Bank {

    /** The bank's account, for a {@link Settlement} to move. */
    public static final UUID ACCOUNT = ServerBank.ACCOUNT;

    private Bank() {
    }

    /** Switches the bank on, on behalf of a mod that pays out of it. Call while loading. */
    public static void enable(String modId) {
        ServerBank.enable(modId);
    }

    /**
     * How much of the bank this mod may yet have to pay — live bets, say —
     * asked whenever staff take money out, so a withdrawal never leaves the
     * bank unable to pay what it owes.
     */
    public static void promise(String modId, LongSupplier promised) {
        ServerBank.promise(modId, promised);
    }

    public static long balance(MinecraftServer server) {
        return ServerBank.balance(CreditStorage.get(server));
    }

    /**
     * The percentage the exchange keeps when buying items back, which is paid
     * into the bank while it is on. Zero when the exchange keeps nothing.
     */
    public static int exchangeSpreadPercent(MinecraftServer server) {
        return EconomyData.get(server).spreadPercent();
    }
}
