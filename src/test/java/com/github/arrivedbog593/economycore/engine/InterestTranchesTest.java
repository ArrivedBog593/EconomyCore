package com.github.arrivedbog593.economycore.engine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InterestTranchesTest {

    private static final InterestTranches SKYBLOCK =
            InterestTranches.parse(List.of("10000000:2", "15000000:1")).orElseThrow();

    @Test
    void eachSliceEarnsItsOwnRate() {
        assertEquals(20_000, SKYBLOCK.interestOn(1_000_000));
        assertEquals(220_000, SKYBLOCK.interestOn(12_000_000), "2% of ten million, 1% of two");
    }

    @Test
    void nothingAboveTheLastTrancheEarns() {
        assertEquals(250_000, SKYBLOCK.interestOn(15_000_000));
        assertEquals(250_000, SKYBLOCK.interestOn(900_000_000));
        assertEquals(250_000, SKYBLOCK.maxInterest());
    }

    @Test
    void fractionsOfACreditAreNotPaid() {
        // 2% of 49 is 0.98.
        assertEquals(0, SKYBLOCK.interestOn(49));
        assertEquals(1, SKYBLOCK.interestOn(50));
        assertEquals(0, SKYBLOCK.interestOn(0));
    }

    @Test
    void ratesMayHaveDecimalsAndComeInAnyOrder() {
        InterestTranches tranches = InterestTranches.parse(List.of("2000:0.5", "1000:1.25")).orElseThrow();
        assertEquals(1000, tranches.tranches().getFirst().upTo());
        // 1.25% of 1,000 and 0.5% of the next 1,000.
        assertEquals(12 + 5, tranches.interestOn(2000));
    }

    @Test
    void anUnreadableLinePaysNothingRatherThanGuessing() {
        assertTrue(InterestTranches.parse(List.of("10000000:2", "lots:1")).isEmpty());
        assertTrue(InterestTranches.parse(List.of("10000000")).isEmpty());
        assertTrue(InterestTranches.parse(List.of("10000000:2", "10000000:1")).isEmpty(), "two tranches cannot end at once");
    }
}
