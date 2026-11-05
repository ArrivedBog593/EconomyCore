package com.github.arrivedbog593.economycore.engine;

import com.github.arrivedbog593.economycore.api.Pocket;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * What to restore after a crash, worked out from the log.
 * <p>
 * Balances live in memory and reach the disk only when the world saves, which by
 * default is every five minutes. A crash therefore loses every credit
 * movement since the last save. The log, written line by line, still has
 * them.
 * <p>
 * Filtering is per transaction, not per player. Each unapplied movement is
 * judged on its own: purely numeric ones are replayed, ones that moved items
 * are dropped. A player who received a grant and also converted some ingots
 * gets the grant back and loses the conversion, which is exactly right —
 * their ingots came back with the world rollback, so the credits for them
 * must not.
 * <p>
 * The result is a set of deltas to add to the saved balances rather than
 * absolute values. Absolutes cannot work once individual entries are
 * dropped: the balance recorded on a later line already includes the
 * conversion being discarded.
 * <p>
 * Idempotent in practice because the sequence number is saved in the same
 * file as the balances. If the process dies again before saving, both revert
 * together and the same lines are replayed onto the same starting point.
 *
 * @param deltas        what to add to each purse
 * @param savingsDeltas what to add to each player's savings
 */
public record RecoveryPlan(Map<UUID, Long> deltas,
                           Map<UUID, Long> savingsDeltas,
                           long highestSequence,
                           int consideredCount,
                           int appliedCount,
                           int discardedCount) {

    public RecoveryPlan {
        deltas = Map.copyOf(Objects.requireNonNull(deltas, "deltas"));
        savingsDeltas = Map.copyOf(Objects.requireNonNull(savingsDeltas, "savingsDeltas"));
    }

    /** Nothing to restore. */
    public static RecoveryPlan empty(long highestSequence) {
        return new RecoveryPlan(Map.of(), Map.of(), highestSequence, 0, 0, 0);
    }

    public boolean isEmpty() {
        return deltas.isEmpty() && savingsDeltas.isEmpty();
    }

    /** What to add to one pocket of each account. */
    public Map<UUID, Long> deltas(Pocket pocket) {
        return pocket == Pocket.SAVINGS ? savingsDeltas : deltas;
    }

    /** How many accounts would move, counting a player once even if both their pockets do. */
    public int affectedCount() {
        Set<UUID> owners = new HashSet<>(deltas.keySet());
        owners.addAll(savingsDeltas.keySet());
        return owners.size();
    }

    /**
     * Works out what can be safely restored.
     *
     * @param lastAppliedSequence the highest sequence already on disk; every
     *                            line at or below it is already reflected in
     *                            the saved balances
     * @param records             log lines from the run that crashed, in any
     *                            order
     */
    public static RecoveryPlan build(long lastAppliedSequence, List<TransactionRecord> records) {
        Objects.requireNonNull(records, "records");

        List<TransactionRecord> pending = new ArrayList<>();
        long highest = lastAppliedSequence;
        for (TransactionRecord record : records) {
            highest = Math.max(highest, record.sequence());
            if (record.sequence() > lastAppliedSequence) {
                pending.add(record);
            }
        }
        if (pending.isEmpty()) {
            return RecoveryPlan.empty(highest);
        }

        Map<UUID, Long> deltas = new LinkedHashMap<>();
        Map<UUID, Long> savingsDeltas = new LinkedHashMap<>();
        int applied = 0;
        int discarded = 0;

        for (TransactionRecord record : pending) {
            if (!record.type().isRecoverable()) {
                discarded++;
                continue;
            }
            if (record.isNobody()) {
                // Credits destroyed when nobody received them. There is no
                // account to give them back to, and none should exist.
                continue;
            }
            applied++;
            // Every account the same way, a player's or one a mod keeps for
            // itself: an account is an id and a balance. Each pocket on its
            // own, since each has its own balance to restore.
            Map<UUID, Long> pocket = record.pocket() == Pocket.SAVINGS ? savingsDeltas : deltas;
            pocket.merge(record.owner(), record.delta(), Long::sum);
        }

        // An account whose only movements canceled out needs no restoring.
        deltas.entrySet().removeIf(entry -> entry.getValue() == 0);
        savingsDeltas.entrySet().removeIf(entry -> entry.getValue() == 0);

        return new RecoveryPlan(deltas, savingsDeltas, highest,
                pending.size(), applied, discarded);
    }
}