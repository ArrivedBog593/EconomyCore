package com.github.arrivedbog593.economycore.engine;

import java.util.Objects;

/**
 * One line of a player's recent bank history, for the banker to show.
 * <p>
 * Not the record of truth; the transaction log is. This is the handful of
 * lines a player asks about at the counter, kept with their balance so it is
 * there without reading log files that rotate every run.
 *
 * @param time   when, in milliseconds since the epoch
 * @param kind   what happened
 * @param amount how much, always positive
 */
public record BankMovement(long time, Kind kind, long amount) {

    /** How many each player keeps; older ones fall off. */
    public static final int KEPT = 10;

    public enum Kind {
        DEPOSIT, WITHDRAWAL, INTEREST;

        /** By position, for the network and the save; anything unknown is a deposit. */
        public static Kind byOrdinal(int ordinal) {
            Kind[] all = values();
            return ordinal >= 0 && ordinal < all.length ? all[ordinal] : DEPOSIT;
        }
    }

    public BankMovement {
        Objects.requireNonNull(kind, "kind");
    }
}
