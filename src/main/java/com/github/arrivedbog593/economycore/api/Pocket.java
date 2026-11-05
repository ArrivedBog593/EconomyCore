package com.github.arrivedbog593.economycore.api;

import java.util.Locale;

/**
 * Where a player keeps credits.
 * <p>
 * Every player has two. The purse is what they carry: everything that
 * spends — the shop, the exchange, a game table, a payment to another
 * player — spends from it, and it is the purse a server can take a share of
 * when a player dies. Savings are what they put away: safe, but not
 * spendable until they are taken back out into the purse.
 * <p>
 * The server's own account, {@link Bank#ACCOUNT}, has a purse and nothing
 * else.
 */
public enum Pocket {

    /** What a player carries and spends from. The pocket everything means when it names none. */
    PURSE,

    /** What a player has put away, out of reach of spending and of dying. */
    SAVINGS;

    /** The name written to the log and the save, never changed once worlds have used it. */
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Parses a code from the log, or null if it names no pocket. */
    public static Pocket fromCode(String code) {
        for (Pocket pocket : values()) {
            if (pocket.code().equals(code)) {
                return pocket;
            }
        }
        return null;
    }
}
