package com.github.arrivedbog593.economycore.engine;

import com.github.arrivedbog593.economycore.api.Pocket;
import com.github.arrivedbog593.economycore.api.TransactionType;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One line of the transaction log.
 * <p>
 * Tab separated so it can be grepped, sorted or opened in a spreadsheet with
 * no tooling, and parsed back without a JSON dependency.
 * <p>
 * Each line carries the resulting balance rather than only the delta. That
 * makes replay idempotent: applying the same line twice lands on the same
 * number, so a recovery that runs twice cannot compound.
 * <p>
 * A movement of a player's savings has an eighth field naming the pocket; one
 * of the purse has none. Logs written before savings existed are all purse,
 * so they read the same as ever, and a version of the mod from before then
 * skips savings lines rather than mistaking them for purse ones.
 *
 * @param sequence  monotonic counter, the anchor for crash recovery
 * @param timestamp ISO-8601 instant, for humans reading the file
 * @param type      what the movement was for
 * @param owner     whose account moved; {@link #NOBODY} for credits that left
 *                  circulation
 * @param delta     signed change
 * @param balance   the balance of that pocket after this movement
 * @param detail    free text: the item, the table, the operator
 * @param pocket    which of the owner's pockets moved
 */
public record TransactionRecord(long sequence, String timestamp, TransactionType type,
                                UUID owner, long delta, long balance, String detail,
                                Pocket pocket) {

    /**
     * Owner of credits that left circulation: income the server took in
     * with nobody registered to receive it. Never restored by recovery,
     * since it belongs to no account.
     * <p>
     * Not the all-zero UUID, which is the server bank's account.
     */
    public static final UUID NOBODY = new UUID(0L, 1L);

    private static final char SEPARATOR = '\t';
    private static final int FIELD_COUNT = 7;

    public TransactionRecord {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(pocket, "pocket");
        timestamp = timestamp == null ? "" : timestamp;
        detail = detail == null ? "" : detail;
    }

    /** A movement of a purse. */
    public TransactionRecord(long sequence, String timestamp, TransactionType type,
                             UUID owner, long delta, long balance, String detail) {
        this(sequence, timestamp, type, owner, delta, balance, detail, Pocket.PURSE);
    }

    public boolean isNobody() {
        return NOBODY.equals(owner);
    }

    /** Serializes to a single log line, without a trailing newline. */
    public String toLine() {
        String line = String.join(String.valueOf(SEPARATOR),
                Long.toString(sequence),
                timestamp,
                type.code(),
                owner.toString(),
                Long.toString(delta),
                Long.toString(balance),
                sanitize(detail));
        return pocket == Pocket.PURSE ? line : line + SEPARATOR + pocket.code();
    }

    /**
     * Parses a log line.
     * <p>
     * Returns empty rather than throwing on anything malformed. A log can be
     * truncated mid-write by the very crash it is meant to help recover from,
     * so a broken last line is expected, not exceptional.
     */
    public static Optional<TransactionRecord> parse(String line) {
        if (line == null || line.isBlank()) {
            return Optional.empty();
        }
        String[] parts = line.split(String.valueOf(SEPARATOR), -1);
        if (parts.length != FIELD_COUNT && parts.length != FIELD_COUNT + 1) {
            return Optional.empty();
        }
        try {
            long sequence = Long.parseLong(parts[0]);
            TransactionType type = TransactionType.fromCode(parts[2]);
            if (type == null) {
                return Optional.empty();
            }
            Pocket pocket = parts.length == FIELD_COUNT ? Pocket.PURSE : Pocket.fromCode(parts[FIELD_COUNT]);
            if (pocket == null) {
                return Optional.empty();
            }
            return Optional.of(new TransactionRecord(
                    sequence,
                    parts[1],
                    type,
                    UUID.fromString(parts[3]),
                    Long.parseLong(parts[4]),
                    Long.parseLong(parts[5]),
                    parts[6],
                    pocket));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static String sanitize(String text) {
        return text.replace(SEPARATOR, ' ').replace('\n', ' ').replace('\r', ' ');
    }
}
