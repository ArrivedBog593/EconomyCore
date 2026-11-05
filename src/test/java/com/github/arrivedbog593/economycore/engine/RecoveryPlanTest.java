package com.github.arrivedbog593.economycore.engine;

import com.github.arrivedbog593.economycore.api.Pocket;
import com.github.arrivedbog593.economycore.api.TransactionType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecoveryPlanTest {

    private static final UUID ALICE = UUID.nameUUIDFromBytes("alice".getBytes());
    private static final UUID BOB = UUID.nameUUIDFromBytes("bob".getBytes());

    // A game's own movements, registered here the way a mod built on the
    // economy registers them. Purely numeric, so replayable.
    private static final TransactionType BET = TransactionType.register("bet", true);
    private static final TransactionType PAYOUT = TransactionType.register("payout", true);
    private static final TransactionType RAKE = TransactionType.register("rake", true);

    private static TransactionRecord entry(long seq, TransactionType type,
                                           UUID owner, long delta, long balance) {
        return new TransactionRecord(seq, "2026-08-26T00:00:00Z", type, owner, delta, balance, "");
    }

    // --- Line round trip ---------------------------------------------------

    @Test
    void aRecordSurvivesWritingAndReading() {
        TransactionRecord original = new TransactionRecord(
                42, "2026-08-26T12:00:00Z", PAYOUT,
                ALICE, 350, 1350, "roulette table 3");

        assertEquals(original, TransactionRecord.parse(original.toLine()).orElseThrow());
    }

    @Test
    void tabsInDetailCannotBreakTheFormat() {
        TransactionRecord record = new TransactionRecord(
                1, "t", BET, ALICE, -10, 90, "a\tb\tc");
        assertEquals(7, record.toLine().split("\t", -1).length);
        assertTrue(TransactionRecord.parse(record.toLine()).isPresent());
    }

    @Test
    void malformedLinesAreIgnoredRatherThanThrown() {
        // A crash can truncate the final line mid-write, so this is expected.
        assertTrue(TransactionRecord.parse("").isEmpty());
        assertTrue(TransactionRecord.parse("47\t2026").isEmpty());
        assertTrue(TransactionRecord.parse("notanumber\tt\tbet\t"
                + ALICE + "\t-10\t90\t").isEmpty());
        assertTrue(TransactionRecord.parse("1\tt\tunknown_type\t"
                + ALICE + "\t-10\t90\t").isEmpty());
    }

    @Test
    void parseHandlesAnEmptyDetailField() {
        Optional<TransactionRecord> parsed = TransactionRecord.parse(
                "5\t2026-08-26T00:00:00Z\tbet\t" + ALICE + "\t-10\t90\t");
        assertTrue(parsed.isPresent());
        assertEquals("", parsed.get().detail());
    }

    // --- Deciding what to restore -------------------------------------------

    @Test
    void nothingToDoWhenTheLogMatchesTheSave() {
        RecoveryPlan plan = RecoveryPlan.build(10, List.of(
                entry(9, BET, ALICE, -50, 950),
                entry(10, PAYOUT, ALICE, 100, 1050)));

        assertTrue(plan.isEmpty());
        assertEquals(0, plan.consideredCount());
        assertEquals(10, plan.highestSequence());
    }

    @Test
    void unappliedMovementsBecomeADelta() {
        RecoveryPlan plan = RecoveryPlan.build(10, List.of(
                entry(11, BET, ALICE, -50, 950),
                entry(12, PAYOUT, ALICE, 200, 1150)));

        assertEquals(150, plan.deltas().get(ALICE), "the two movements sum");
        assertEquals(12, plan.highestSequence());
        assertEquals(2, plan.appliedCount());
    }

    @Test
    void outOfOrderLinesStillSumCorrectly() {
        RecoveryPlan plan = RecoveryPlan.build(0, List.of(
                entry(3, PAYOUT, ALICE, 100, 300),
                entry(1, PAYOUT, ALICE, 100, 100),
                entry(2, PAYOUT, ALICE, 100, 200)));

        assertEquals(300, plan.deltas().get(ALICE));
    }

    @Test
    void movementsThatCancelOutLeaveNothingToDo() {
        RecoveryPlan plan = RecoveryPlan.build(0, List.of(
                entry(1, BET, ALICE, -100, 900),
                entry(2, PAYOUT, ALICE, 100, 1000)));

        assertTrue(plan.isEmpty());
        assertEquals(2, plan.appliedCount(), "they were still considered");
    }

    // --- The item rule --------------------------------------------------------

    @Test
    void aConversionIsDroppedButTheRestIsKept() {
        // The whole point of switching to per-transaction filtering: the grant
        // comes back, the conversion does not, and the player keeps the ingots
        // the rollback returned to them.
        RecoveryPlan plan = RecoveryPlan.build(10, List.of(
                entry(11, TransactionType.ADMIN_GIVE, ALICE, 5000, 5000),
                entry(12, TransactionType.CONVERT_IN, ALICE, 81, 5081)));

        assertEquals(5000, plan.deltas().get(ALICE));
        assertEquals(1, plan.appliedCount());
        assertEquals(1, plan.discardedCount());
    }

    @Test
    void aRedeemIsDroppedSoTheCreditsRevertWithTheItems() {
        RecoveryPlan plan = RecoveryPlan.build(10, List.of(
                entry(11, TransactionType.CONVERT_OUT, ALICE, -10_000, 0)));

        assertTrue(plan.isEmpty(), "the items vanished with the rollback, so must the cost");
        assertEquals(1, plan.discardedCount());
    }

    @Test
    void aShopPurchaseIsDropped() {
        RecoveryPlan plan = RecoveryPlan.build(0, List.of(
                entry(1, TransactionType.SHOP_PURCHASE, ALICE, -20_000, 0)));

        assertTrue(plan.isEmpty());
        assertEquals(1, plan.discardedCount());
    }

    @Test
    void onePlayerBeingFilteredDoesNotAffectAnother() {
        RecoveryPlan plan = RecoveryPlan.build(10, List.of(
                entry(11, TransactionType.CONVERT_OUT, ALICE, -500, 500),
                entry(12, PAYOUT, BOB, 300, 1300)));

        assertEquals(300, plan.deltas().get(BOB));
        assertFalse(plan.deltas().containsKey(ALICE));
        assertEquals(1, plan.affectedCount());
    }

    // --- Accounts a mod keeps, and credits nobody keeps --------------------------

    @Test
    void aModsOwnAccountIsRestoredLikeAnyOther() {
        // A casino's bankroll, say, kept under an id of its own. Recovery has
        // no idea whose it is and needs none: an account is an id and a balance.
        UUID bank = new UUID(0L, 0L);
        RecoveryPlan plan = RecoveryPlan.build(10, List.of(
                entry(11, RAKE, bank, 50, 5050),
                entry(12, PAYOUT, ALICE, 200, 1200)));

        assertEquals(50, plan.deltas().get(bank));
        assertEquals(200, plan.deltas().get(ALICE));
        assertEquals(2, plan.affectedCount());
    }

    @Test
    void creditsThatLeftCirculationAreNeverRestored() {
        // Income with nobody to receive it was destroyed, on purpose. Even a
        // type that is otherwise safe to replay must not bring it back.
        RecoveryPlan plan = RecoveryPlan.build(10, List.of(
                entry(11, RAKE, TransactionRecord.NOBODY, 75, 0)));

        assertTrue(plan.isEmpty());
        assertFalse(plan.deltas().containsKey(TransactionRecord.NOBODY));
    }

    @Test
    void incomeThatMovedItemsIsDroppedWhoeverReceivedIt() {
        UUID bank = new UUID(0L, 0L);
        RecoveryPlan plan = RecoveryPlan.build(10, List.of(
                entry(11, TransactionType.SHOP_PURCHASE, bank, 500, 5500)));

        assertTrue(plan.isEmpty());
    }

    // --- Purse and savings ---------------------------------------------------------

    @Test
    void aSavingsLineSurvivesWritingAndReading() {
        TransactionRecord original = new TransactionRecord(
                42, "2026-11-04T12:00:00Z", TransactionType.SAVINGS_DEPOSIT,
                ALICE, 500, 500, "savings_deposit", Pocket.SAVINGS);

        assertEquals(8, original.toLine().split("\t", -1).length);
        assertEquals(original, TransactionRecord.parse(original.toLine()).orElseThrow());
    }

    @Test
    void aPurseLineKeepsTheSevenFieldsOlderLogsHave() {
        // So a log from before savings reads as all purse, and a version from
        // before savings still reads every purse line of a newer log.
        TransactionRecord purse = entry(1, BET, ALICE, -10, 90);
        assertEquals(7, purse.toLine().split("\t", -1).length);
        assertEquals(Pocket.PURSE, TransactionRecord.parse(
                "1\tt\tbet\t" + ALICE + "\t-10\t90\t").orElseThrow().pocket());
    }

    @Test
    void aLineNamingNoKnownPocketIsIgnored() {
        assertTrue(TransactionRecord.parse("1\tt\tbet\t" + ALICE + "\t-10\t90\t\twallet").isEmpty());
    }

    @Test
    void eachPocketIsRestoredOnItsOwn() {
        // A deposit is two lines for one player: the purse down, savings up.
        // Summed together they would cancel out and restore nothing.
        RecoveryPlan plan = RecoveryPlan.build(10, List.of(
                entry(11, PAYOUT, ALICE, 1000, 1000),
                new TransactionRecord(12, "t", TransactionType.SAVINGS_DEPOSIT,
                        ALICE, -600, 400, "", Pocket.PURSE),
                new TransactionRecord(13, "t", TransactionType.SAVINGS_DEPOSIT,
                        ALICE, 600, 600, "", Pocket.SAVINGS)));

        assertEquals(400, plan.deltas(Pocket.PURSE).get(ALICE));
        assertEquals(600, plan.deltas(Pocket.SAVINGS).get(ALICE));
        assertEquals(1, plan.affectedCount(), "one player, however many pockets");
    }

    @Test
    void savingsAreDroppedLikeAnyOtherMovementOfItems() {
        TransactionType bought = TransactionType.SHOP_PURCHASE;
        RecoveryPlan plan = RecoveryPlan.build(10, List.of(
                new TransactionRecord(11, "t", bought, ALICE, -50, 0, "", Pocket.SAVINGS)));

        assertTrue(plan.isEmpty());
    }

    // --- Edges -------------------------------------------------------------------

    @Test
    void anEmptyLogYieldsAnEmptyPlan() {
        RecoveryPlan plan = RecoveryPlan.build(47, List.of());
        assertTrue(plan.isEmpty());
        assertEquals(47, plan.highestSequence(), "the sequence must not go backwards");
    }

    @Test
    void everyRecoverableTypeIsItemFree() {
        // The flag is the whole safety argument, so pin it down. The economy's
        // own types by name: other tests register types of their own, and a mod
        // built on the economy answers for the ones it adds.
        for (TransactionType type : List.of(TransactionType.ADMIN_GIVE, TransactionType.ADMIN_TAKE,
                TransactionType.TRANSFER, TransactionType.CONVERT_IN, TransactionType.CONVERT_OUT,
                TransactionType.SHOP_PURCHASE, TransactionType.SPREAD)) {
            boolean touchesItems = type == TransactionType.CONVERT_IN
                    || type == TransactionType.CONVERT_OUT
                    || type == TransactionType.SHOP_PURCHASE
                    // Rides on a CONVERT_OUT: the fee is only owed because an
                    // item changed hands, and that item reverts with the
                    // world. Replaying it would charge for a trade that no
                    // longer happened.
                    || type == TransactionType.SPREAD;
            assertEquals(!touchesItems, type.isRecoverable(),
                    type + " recoverability does not match whether it moves items");
        }
    }

    @Test
    void everyTypeCodeParsesBack() {
        for (TransactionType type : TransactionType.values()) {
            assertEquals(type, TransactionType.fromCode(type.code()));
        }
        assertNull(TransactionType.fromCode("nonsense"));
    }
}
