package com.github.arrivedbog593.economycore.engine;

import com.github.arrivedbog593.economycore.api.CreditMath;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Interest on savings, paid by tranches: each slice of a balance earns its
 * own rate, and whatever lies above the last slice earns nothing.
 * <p>
 * With "the first 10,000,000 at 2% and up to 15,000,000 at 1%", a balance of
 * 12,000,000 earns 2% of ten million and 1% of the two above it: 220,000.
 * That cap on what interest can pay is the point. A flat rate on any balance
 * makes the richest players richer fastest, and every credit it pays is
 * minted.
 * <p>
 * Rates are kept in millionths of a percent, so 1.25% and SkyBlock's 0.001%
 * are both exact, and what is paid is
 * rounded down: the bank never pays a fraction it does not have.
 *
 * @param tranches ordered by their upper bound, each above the last
 */
public record InterestTranches(List<Tranche> tranches) {

    /** One percent, in the units a rate is kept in: six decimals of a percent are exact. */
    public static final long PERCENT = 1_000_000;

    /**
     * One slice: from where the previous one ended up to {@code upTo}.
     *
     * @param upTo        the balance this slice ends at
     * @param rate its rate, in millionths of a percent: {@code 2 * PERCENT} is 2%
     */
    public record Tranche(long upTo, long rate) {
        public Tranche {
            if (upTo <= 0 || rate < 0) {
                throw new IllegalArgumentException("Bad tranche: up to " + upTo + " at " + rate);
            }
        }
    }

    public InterestTranches {
        tranches = List.copyOf(Objects.requireNonNull(tranches, "tranches"));
        for (int i = 1; i < tranches.size(); i++) {
            if (tranches.get(i).upTo() <= tranches.get(i - 1).upTo()) {
                throw new IllegalArgumentException("Tranches must rise: " + tranches);
            }
        }
    }

    /** What a balance earns in one payment. */
    public long interestOn(long balance) {
        long earned = 0;
        long from = 0;
        for (Tranche tranche : tranches) {
            if (balance <= from) {
                break;
            }
            long slice = Math.min(balance, tranche.upTo()) - from;
            earned += CreditMath.shareOf(slice, tranche.rate(), 100 * PERCENT, RoundingMode.FLOOR);
            from = tranche.upTo();
        }
        return earned;
    }

    /** The most one payment can be: what a balance at the top of the last tranche earns. */
    public long maxInterest() {
        return tranches.isEmpty() ? 0 : interestOn(tranches.getLast().upTo());
    }

    /** As {@link #parse} reads them, apart with commas: {@code "10000000:2,15000000:1"}. */
    public String toText() {
        StringBuilder text = new StringBuilder();
        for (Tranche tranche : tranches) {
            if (!text.isEmpty()) {
                text.append(',');
            }
            text.append(tranche.upTo()).append(':').append(percent(tranche.rate()));
        }
        return text.toString();
    }

    /** A rate as the percentage it is: two percent is "2", one and a quarter "1.25". */
    public static String percent(long rate) {
        return BigDecimal.valueOf(rate).divide(BigDecimal.valueOf(PERCENT)).stripTrailingZeros().toPlainString();
    }

    /**
     * Reads tranches written as {@code "upTo:percent"}, such as
     * {@code "10000000:2"} or {@code "500000:1.25"}, in any order.
     *
     * @return empty, with nothing paid, if any line cannot be read; a typo in
     *         a rate should not hand out money at a rate nobody meant
     */
    public static Optional<InterestTranches> parse(List<String> lines) {
        List<Tranche> read = new ArrayList<>();
        try {
            for (String line : lines) {
                String[] parts = line.trim().split(":");
                if (parts.length != 2) {
                    return Optional.empty();
                }
                long upTo = Long.parseLong(parts[0].trim().replace("_", "").replace(",", ""));
                long rate = new BigDecimal(parts[1].trim())
                        .multiply(BigDecimal.valueOf(PERCENT))
                        .longValueExact();
                read.add(new Tranche(upTo, rate));
            }
            read.sort(Comparator.comparingLong(Tranche::upTo));
            return Optional.of(new InterestTranches(read));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
