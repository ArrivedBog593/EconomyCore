package com.github.arrivedbog593.economycore.api;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.OptionalLong;

/**
 * How a credit amount is written for a person to read.
 * <p>
 * Shared because it was already duplicated in the commands and in the screens,
 * and a third copy had just been about to appear in a place that showed a bare
 * {@code 138888} next to a properly grouped balance.
 * <p>
 * Fixed to {@link Locale#ROOT} rather than the default. The default is the
 * server's, not the reader's, so a server hosted in one country would group
 * digits one way for every player on it regardless of where they are — an
 * inconsistency that looks like a bug and cannot be fixed by the player.
 * One predictable format everywhere is the lesser evil, and the separator carries
 * no meaning here beyond making long numbers readable.
 */
public final class CreditFormat {

    private CreditFormat() {
    }

    /** Groups thousands: {@code 138888} becomes {@code 138,888}. */
    public static String of(long credits) {
        return String.format(Locale.ROOT, "%,d", credits);
    }

    /**
     * Reads an amount the way a player types one: digits, perhaps a few
     * decimals, and a {@code k} for thousands or an {@code m} for millions,
     * as SkyBlock takes them. {@code "1.5k"} is 1,500. Commas are allowed.
     * Whatever falls below a whole credit is dropped, never rounded up, so
     * nobody is charged a credit they did not type.
     *
     * @return empty for anything that is not an amount, or is negative
     */
    public static OptionalLong parse(String text) {
        if (text == null) {
            return OptionalLong.empty();
        }
        String cleaned = text.trim().replace(",", "").toLowerCase(Locale.ROOT);
        long multiplier = 1;
        if (cleaned.endsWith("k")) {
            multiplier = 1_000;
        } else if (cleaned.endsWith("m")) {
            multiplier = 1_000_000;
        }
        String digits = multiplier == 1 ? cleaned : cleaned.substring(0, cleaned.length() - 1);
        if (!digits.matches("\\d+(\\.\\d*)?|\\.\\d+")) {
            return OptionalLong.empty();
        }
        try {
            return OptionalLong.of(new BigDecimal(digits).multiply(BigDecimal.valueOf(multiplier))
                    .setScale(0, RoundingMode.FLOOR).longValueExact());
        } catch (ArithmeticException e) {
            return OptionalLong.empty();
        }
    }
}
