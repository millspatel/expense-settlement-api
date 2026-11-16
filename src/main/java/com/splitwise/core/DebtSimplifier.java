package com.splitwise.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Debt simplification: given everyone's net balance in a group, produce a short list of
 * payments that settles every balance.
 *
 * <p>Example: A owes B ₹100 and B owes C ₹100. Net balances are A = -100, B = 0, C = +100,
 * so a single payment "A pays C ₹100" settles everything.
 *
 * <p>Algorithm (greedy, O(n log n)):
 * <ol>
 *   <li>Collapse all expenses into one net balance per person (positive = is owed money,
 *       negative = owes money). Who paid whom originally no longer matters.</li>
 *   <li>Put creditors and debtors into two max-heaps keyed by amount.</li>
 *   <li>Repeatedly match the largest debtor with the largest creditor and transfer
 *       {@code min(debt, credit)}. At least one of them reaches zero each round, so the
 *       result has at most {@code n - 1} payments.</li>
 * </ol>
 * Finding the true minimum number of payments is NP-hard (it reduces to subset-sum);
 * this greedy approach is what real apps use and is optimal or near-optimal in practice.
 */
public final class DebtSimplifier {

    private DebtSimplifier() {
    }

    /** "from pays to this amount". */
    public record Transfer(Long from, Long to, BigDecimal amount) {
    }

    private record Party(Long userId, long cents) {
    }

    /**
     * @param netBalances userId -> net balance (positive = owed money, negative = owes money).
     *                    Must sum to zero.
     */
    public static List<Transfer> simplify(Map<Long, BigDecimal> netBalances) {
        Comparator<Party> largestFirst = Comparator.comparingLong(Party::cents).reversed()
                .thenComparing(Party::userId);
        PriorityQueue<Party> creditors = new PriorityQueue<>(largestFirst);
        PriorityQueue<Party> debtors = new PriorityQueue<>(largestFirst);

        long total = 0;
        for (Map.Entry<Long, BigDecimal> e : netBalances.entrySet()) {
            long cents = e.getValue().movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
            total += cents;
            if (cents > 0) creditors.add(new Party(e.getKey(), cents));
            else if (cents < 0) debtors.add(new Party(e.getKey(), -cents));
        }
        if (total != 0) {
            throw new IllegalArgumentException("Balances must sum to zero, got " + BigDecimal.valueOf(total, 2));
        }

        List<Transfer> transfers = new ArrayList<>();
        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            Party creditor = creditors.poll();
            Party debtor = debtors.poll();
            long settled = Math.min(creditor.cents(), debtor.cents());
            transfers.add(new Transfer(debtor.userId(), creditor.userId(), BigDecimal.valueOf(settled, 2)));

            if (creditor.cents() > settled) creditors.add(new Party(creditor.userId(), creditor.cents() - settled));
            if (debtor.cents() > settled) debtors.add(new Party(debtor.userId(), debtor.cents() - settled));
        }
        return transfers;
    }
}
