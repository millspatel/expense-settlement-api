package com.splitwise.core;

import com.splitwise.core.DebtSimplifier.Transfer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DebtSimplifierTest {

    private static final long A = 1, B = 2, C = 3, D = 4;

    private static Map<Long, BigDecimal> balances(Object... idAmountPairs) {
        Map<Long, BigDecimal> map = new LinkedHashMap<>();
        for (int i = 0; i < idAmountPairs.length; i += 2) {
            map.put((Long) idAmountPairs[i], new BigDecimal((String) idAmountPairs[i + 1]));
        }
        return map;
    }

    @Test
    void chainCollapsesToSinglePayment() {
        // A owes B 100, B owes C 100  =>  A pays C 100
        List<Transfer> transfers = DebtSimplifier.simplify(balances(A, "-100", B, "0", C, "100"));
        assertThat(transfers).containsExactly(new Transfer(A, C, new BigDecimal("100.00")));
    }

    @Test
    void settledGroupNeedsNoPayments() {
        assertThat(DebtSimplifier.simplify(balances(A, "0", B, "0"))).isEmpty();
    }

    @Test
    void oneCreditorManyDebtors() {
        // A paid 300 dinner for A, B, C, D (75 each)
        List<Transfer> transfers = DebtSimplifier.simplify(balances(A, "225", B, "-75", C, "-75", D, "-75"));
        assertThat(transfers).hasSize(3).allSatisfy(t -> {
            assertThat(t.to()).isEqualTo(A);
            assertThat(t.amount()).isEqualByComparingTo("75");
        });
    }

    @Test
    void largestDebtorPaysLargestCreditorFirst() {
        List<Transfer> transfers = DebtSimplifier.simplify(balances(A, "-70", B, "-30", C, "60", D, "40"));
        assertThat(transfers.getFirst()).isEqualTo(new Transfer(A, C, new BigDecimal("60.00")));
        assertSettles(balances(A, "-70", B, "-30", C, "60", D, "40"), transfers);
        assertThat(transfers).hasSizeLessThanOrEqualTo(3);
    }

    @Test
    void rejectsBalancesThatDoNotSumToZero() {
        assertThatThrownBy(() -> DebtSimplifier.simplify(balances(A, "-10", B, "5")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void randomGroupsAlwaysSettleWithAtMostNMinusOnePayments() {
        Random random = new Random(42);
        for (int round = 0; round < 500; round++) {
            int n = 2 + random.nextInt(10);
            Map<Long, BigDecimal> net = new HashMap<>();
            long total = 0;
            for (long id = 1; id < n; id++) {
                long cents = random.nextLong(-100_000, 100_000);
                net.put(id, BigDecimal.valueOf(cents, 2));
                total += cents;
            }
            net.put((long) n, BigDecimal.valueOf(-total, 2)); // force zero sum

            List<Transfer> transfers = DebtSimplifier.simplify(net);
            assertThat(transfers.size()).isLessThanOrEqualTo(n - 1);
            assertSettles(net, transfers);
        }
    }

    /** Applying the transfers must bring every balance to exactly zero. */
    private static void assertSettles(Map<Long, BigDecimal> net, List<Transfer> transfers) {
        Map<Long, BigDecimal> after = new HashMap<>(net);
        for (Transfer t : transfers) {
            assertThat(t.amount()).isPositive();
            after.merge(t.from(), t.amount(), BigDecimal::add);
            after.merge(t.to(), t.amount().negate(), BigDecimal::add);
        }
        after.values().forEach(v -> assertThat(v).isEqualByComparingTo("0"));
    }
}
