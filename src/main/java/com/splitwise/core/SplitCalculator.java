package com.splitwise.core;

import com.splitwise.model.SplitType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Turns an expense amount and a split description into each participant's exact share.
 *
 * <p>All maths is done in whole paise/cents ({@code long}) so shares always add up to the
 * total exactly. When an amount can't be divided evenly (e.g. ₹100 / 3) the leftover
 * paise are handed out one at a time so nobody is off by more than 0.01.
 */
public final class SplitCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private SplitCalculator() {
    }

    /** One participant in a split. {@code value} is a percentage, an exact amount, or unused for EQUAL. */
    public record Participant(Long userId, BigDecimal value) {
    }

    /**
     * @return userId -> share (scale 2), in the same order as {@code participants}
     * @throws InvalidSplitException if the input doesn't describe a valid split
     */
    public static Map<Long, BigDecimal> split(BigDecimal amount, SplitType type, List<Participant> participants) {
        long totalCents = toCents(amount, "amount");
        if (totalCents <= 0) {
            throw new InvalidSplitException("Amount must be greater than zero");
        }
        if (participants == null || participants.isEmpty()) {
            throw new InvalidSplitException("At least one participant is required");
        }
        Set<Long> seen = new HashSet<>();
        for (Participant p : participants) {
            if (p.userId() == null || !seen.add(p.userId())) {
                throw new InvalidSplitException("Participants must be distinct users");
            }
        }

        long[] cents = switch (type) {
            case EQUAL -> equal(totalCents, participants.size());
            case PERCENTAGE -> percentage(totalCents, participants);
            case EXACT -> exact(totalCents, participants);
        };

        Map<Long, BigDecimal> result = new LinkedHashMap<>();
        for (int i = 0; i < participants.size(); i++) {
            result.put(participants.get(i).userId(), BigDecimal.valueOf(cents[i], 2));
        }
        return result;
    }

    private static long[] equal(long totalCents, int n) {
        long[] shares = new long[n];
        long base = totalCents / n;
        long remainder = totalCents % n;
        for (int i = 0; i < n; i++) {
            shares[i] = base + (i < remainder ? 1 : 0);
        }
        return shares;
    }

    private static long[] percentage(long totalCents, List<Participant> participants) {
        BigDecimal sum = BigDecimal.ZERO;
        for (Participant p : participants) {
            if (p.value() == null || p.value().signum() <= 0) {
                throw new InvalidSplitException("Every percentage must be greater than zero");
            }
            sum = sum.add(p.value());
        }
        if (sum.compareTo(HUNDRED) != 0) {
            throw new InvalidSplitException("Percentages must add up to 100, got " + sum.stripTrailingZeros().toPlainString());
        }

        // Largest-remainder method: floor every share, then give the leftover paise
        // to the participants whose shares were rounded down the most.
        int n = participants.size();
        long[] shares = new long[n];
        BigDecimal[] fractions = new BigDecimal[n];
        long allocated = 0;
        BigDecimal total = BigDecimal.valueOf(totalCents);
        for (int i = 0; i < n; i++) {
            BigDecimal exactShare = total.multiply(participants.get(i).value()).divide(HUNDRED, 10, RoundingMode.HALF_UP);
            BigDecimal floor = exactShare.setScale(0, RoundingMode.FLOOR);
            shares[i] = floor.longValueExact();
            fractions[i] = exactShare.subtract(floor);
            allocated += shares[i];
        }
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        Arrays.sort(order, (a, b) -> {
            int c = fractions[b].compareTo(fractions[a]);
            return c != 0 ? c : Integer.compare(a, b);
        });
        for (int k = 0; k < totalCents - allocated; k++) {
            shares[order[k]]++;
        }
        return shares;
    }

    private static long[] exact(long totalCents, List<Participant> participants) {
        long[] shares = new long[participants.size()];
        long sum = 0;
        for (int i = 0; i < shares.length; i++) {
            BigDecimal value = participants.get(i).value();
            if (value == null || value.signum() < 0) {
                throw new InvalidSplitException("Every exact amount must be zero or more");
            }
            shares[i] = toCents(value, "exact amount");
            sum += shares[i];
        }
        if (sum != totalCents) {
            throw new InvalidSplitException("Exact amounts must add up to " + BigDecimal.valueOf(totalCents, 2)
                    + ", got " + BigDecimal.valueOf(sum, 2));
        }
        return shares;
    }

    static long toCents(BigDecimal value, String field) {
        if (value == null) {
            throw new InvalidSplitException(field + " is required");
        }
        try {
            return value.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        } catch (ArithmeticException e) {
            throw new InvalidSplitException(field + " can have at most 2 decimal places");
        }
    }
}
