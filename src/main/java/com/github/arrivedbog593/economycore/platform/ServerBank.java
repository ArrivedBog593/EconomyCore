package com.github.arrivedbog593.economycore.platform;

import com.github.arrivedbog593.economycore.engine.CreditAccount;
import com.github.arrivedbog593.economycore.engine.TransactionType;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * The server's own money: one account the server owns, apart from every
 * player's.
 * <p>
 * Off unless something wants it. A server with nothing that pays out of a
 * bank has no use for one, and a bank nobody spends would only pile up the
 * credits that would otherwise leave circulation. A mod that pays out of it —
 * a casino covering its tables — switches it on with {@link #enable} while it
 * loads; a server owner can also keep it on in the config. While it is on, the
 * server's income is paid into it, unless the config says that income should
 * leave circulation anyway (see {@link ServerIncome}).
 * <p>
 * An ordinary account in the ledger under a fixed id, which is all it takes
 * for crash recovery to look after it the way it looks after a player's. The
 * all-zero id is the one TableGames' house always used, so its old logs still
 * recover into it.
 * <p>
 * One bank for the whole server, shared by every mod that switches it on. What
 * one of them pays out is not there for the others: a mod that needs to keep
 * part of it untouchable says so with {@link #promise}, and withdrawals by
 * staff stop short of the total promised.
 */
public final class ServerBank {

    public static final UUID ACCOUNT = new UUID(0L, 0L);

    private static final Set<String> USERS = ConcurrentHashMap.newKeySet();
    private static final Map<String, LongSupplier> PROMISES = new ConcurrentHashMap<>();

    private ServerBank() {
    }

    /**
     * Switches the bank on, on behalf of a mod that pays out of it. Called
     * while the mod loads; there is no switching it off again.
     */
    public static void enable(String modId) {
        USERS.add(Objects.requireNonNull(modId, "modId"));
    }

    /**
     * Tells the bank how much of it a mod has promised and may yet have to pay
     * — a casino's live bets, say. Asked whenever staff take money out, so
     * that a withdrawal never leaves the bank unable to pay what it owes.
     */
    public static void promise(String modId, LongSupplier promised) {
        PROMISES.put(Objects.requireNonNull(modId, "modId"),
                Objects.requireNonNull(promised, "promised"));
    }

    /** Whether the bank exists on this server: a mod asked for it, or the config keeps it on. */
    public static boolean isOn() {
        return !USERS.isEmpty() || EconomyConfig.BANK_ALWAYS_ON.getAsBoolean();
    }

    /** The mods that switched the bank on, for telling an owner why it is. */
    public static Set<String> usedBy() {
        return Set.copyOf(USERS);
    }

    /** Everything every mod has promised out of the bank right now. */
    public static long promised() {
        long total = 0;
        for (LongSupplier promise : PROMISES.values()) {
            total = Math.addExact(total, Math.max(0, promise.getAsLong()));
        }
        return total;
    }

    public static long balance(CreditStorage storage) {
        return storage.balanceOf(ACCOUNT);
    }

    /**
     * Pays into the bank. Never refused: what would take it past the balance
     * cap is not kept.
     *
     * @return how much was kept
     */
    public static long credit(CreditStorage storage, long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Negative bank credit: " + amount);
        }
        long room = Math.max(0, CreditAccount.MAX_BALANCE - balance(storage));
        long kept = Math.min(amount, room);
        if (kept > 0) {
            storage.deposit(ACCOUNT, kept);
        }
        return kept;
    }

    /**
     * Pays out of the bank.
     *
     * @return false if the bank cannot cover it, in which case nothing changes
     *         and the caller must refuse the payout rather than create credits
     *         from nothing
     */
    public static boolean debit(CreditStorage storage, long amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Negative bank debit: " + amount);
        }
        return storage.withdraw(ACCOUNT, amount);
    }

    /** Logs a movement of the bank, with the balance it left. */
    public static void record(CreditStorage storage, TransactionType type, long delta, String detail) {
        EconomyEvents.record(storage, type, ACCOUNT, delta, balance(storage), detail);
    }
}
