package com.github.arrivedbog593.economycore.api;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Shares of an amount of credits, in whole credits.
 * <p>
 * Credits are whole numbers: a balance is a {@code long}, never a fraction.
 * A percentage of one rarely comes out whole, so every share has to be
 * rounded, and which way is a decision rather than a detail:
 * <ul>
 *   <li>{@link RoundingMode#HALF_UP}, to the nearest credit and halves up,
 *       for what a player keeps or loses at once — what is left after
 *       dying.</li>
 *   <li>{@link RoundingMode#CEILING}, up, for a fee charged on many small
 *       trades — a bazaar's tax, an auction's cut. Rounded to the nearest, a
 *       fee of 1.25% on anything worth less than 40 credits is nothing, and
 *       a seller who splits one big sale into many small ones never pays
 *       it.</li>
 *   <li>{@link RoundingMode#FLOOR}, down, for what is paid out of a fee —
 *       so a payout never exceeds what it is a share of.</li>
 * </ul>
 * Exact whatever the size of the numbers: worked in decimal, so nothing
 * overflows and nothing is lost to floating point on the way.
 */
public final class CreditMath {

    /** A whole, in percent. */
    public static final int PERCENT = 100;

    /** A whole, in basis points: hundredths of a percent, so 1.25% is 125. */
    public static final int BASIS_POINTS = 10_000;

    private CreditMath() {
    }

    /**
     * {@code percent}% of {@code amount}, rounded as asked.
     *
     * @param percent may exceed 100, for a price with a surcharge on top
     */
    public static long percentOf(long amount, long percent, RoundingMode rounding) {
        return shareOf(amount, percent, PERCENT, rounding);
    }

    /** A share in basis points, for rates with decimals: 125 is 1.25%. */
    public static long basisPointsOf(long amount, long basisPoints, RoundingMode rounding) {
        return shareOf(amount, basisPoints, BASIS_POINTS, rounding);
    }

    /**
     * {@code amount × numerator ÷ denominator}, rounded as asked.
     *
     * @throws ArithmeticException      if the result does not fit a {@code long}
     * @throws IllegalArgumentException if the denominator is not positive
     */
    public static long shareOf(long amount, long numerator, long denominator, RoundingMode rounding) {
        Objects.requireNonNull(rounding, "rounding");
        if (denominator <= 0) {
            throw new IllegalArgumentException("Denominator must be positive: " + denominator);
        }
        return BigDecimal.valueOf(amount)
                .multiply(BigDecimal.valueOf(numerator))
                .divide(BigDecimal.valueOf(denominator), 0, rounding)
                .longValueExact();
    }
}
