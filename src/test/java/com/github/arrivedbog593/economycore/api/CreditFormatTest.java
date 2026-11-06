package com.github.arrivedbog593.economycore.api;

import org.junit.jupiter.api.Test;

import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreditFormatTest {

    @Test
    void amountsAreReadTheWayTheyAreTyped() {
        assertEquals(OptionalLong.of(1500), CreditFormat.parse("1500"));
        assertEquals(OptionalLong.of(1500), CreditFormat.parse("1,500"));
        assertEquals(OptionalLong.of(1500), CreditFormat.parse("1.5k"));
        assertEquals(OptionalLong.of(2_000_000), CreditFormat.parse("2M"));
        assertEquals(OptionalLong.of(500), CreditFormat.parse(".5k"));
    }

    @Test
    void whatFallsBelowACreditIsDroppedNotRoundedUp() {
        assertEquals(OptionalLong.of(1), CreditFormat.parse("1.99"));
        assertEquals(OptionalLong.of(1234), CreditFormat.parse("1.2345k"));
    }

    @Test
    void anythingElseIsNotAnAmount() {
        assertTrue(CreditFormat.parse("").isEmpty());
        assertTrue(CreditFormat.parse("k").isEmpty());
        assertTrue(CreditFormat.parse("-5").isEmpty());
        assertTrue(CreditFormat.parse("1.5b").isEmpty());
        assertTrue(CreditFormat.parse("lots").isEmpty());
        assertTrue(CreditFormat.parse("99999999999999999999m").isEmpty(), "too big for a long");
    }
}
