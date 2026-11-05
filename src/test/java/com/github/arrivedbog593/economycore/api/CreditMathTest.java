package com.github.arrivedbog593.economycore.api;

import org.junit.jupiter.api.Test;

import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CreditMathTest {

    @Test
    void halfUpRoundsToTheNearestCreditWithHalvesUp() {
        // 50% of 15 is 7.5; 70% of 101 is 70.7; 30% of 101 is 30.3.
        assertEquals(8, CreditMath.percentOf(15, 50, RoundingMode.HALF_UP));
        assertEquals(71, CreditMath.percentOf(101, 70, RoundingMode.HALF_UP));
        assertEquals(30, CreditMath.percentOf(101, 30, RoundingMode.HALF_UP));
    }

    @Test
    void aFeeRoundedUpCannotBeDodgedBySplittingASale() {
        // 1.25% of 30 is 0.375: nothing to the nearest credit, one rounded up.
        assertEquals(0, CreditMath.basisPointsOf(30, 125, RoundingMode.HALF_UP));
        assertEquals(1, CreditMath.basisPointsOf(30, 125, RoundingMode.CEILING));
        assertEquals(125, CreditMath.basisPointsOf(10_000, 125, RoundingMode.CEILING), "exact stays exact");
    }

    @Test
    void aSurchargeOnTopOfAPriceIsAPercentOverAHundred() {
        // 110% of 101 is 111.1, which up is 112.
        assertEquals(112, CreditMath.percentOf(101, 110, RoundingMode.CEILING));
        assertEquals(110, CreditMath.percentOf(100, 110, RoundingMode.CEILING), "exact stays exact");
    }

    @Test
    void theLargestBalanceTimesAnyShareDoesNotOverflow() {
        long max = Credits.MAX_BALANCE;
        assertEquals(max, CreditMath.basisPointsOf(max, CreditMath.BASIS_POINTS, RoundingMode.FLOOR));
        assertThrows(ArithmeticException.class,
                () -> CreditMath.shareOf(Long.MAX_VALUE, 2, 1, RoundingMode.FLOOR));
    }

    @Test
    void aDenominatorMustBePositive() {
        assertThrows(IllegalArgumentException.class,
                () -> CreditMath.shareOf(10, 1, 0, RoundingMode.FLOOR));
    }
}
