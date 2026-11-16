package com.splitwise.service;

import com.splitwise.core.DebtSimplifier;
import com.splitwise.dto.BalanceDtos.*;
import com.splitwise.dto.UserResponse;
import com.splitwise.model.*;
import com.splitwise.repository.ExpenseRepository;
import com.splitwise.repository.GroupRepository;
import com.splitwise.repository.SettlementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
public class BalanceService {

    private final GroupService groupService;
    private final GroupRepository groups;
    private final ExpenseRepository expenses;
    private final SettlementRepository settlements;

    public BalanceService(GroupService groupService, GroupRepository groups, ExpenseRepository expenses,
                          SettlementRepository settlements) {
        this.groupService = groupService;
        this.groups = groups;
        this.expenses = expenses;
        this.settlements = settlements;
    }

    @Transactional(readOnly = true)
    public GroupBalancesResponse groupBalances(Long callerId, Long groupId) {
        ExpenseGroup group = groupService.requireMembership(callerId, groupId);
        Map<Long, UserResponse> people = new LinkedHashMap<>();
        group.getMembers().forEach(u -> people.put(u.getId(), UserResponse.from(u)));

        List<Expense> groupExpenses = expenses.findAllByGroupIdWithSplits(groupId);
        List<Settlement> groupSettlements = settlements.findAllByGroupId(groupId);

        Map<Long, BigDecimal> net = netBalances(people.keySet(), groupExpenses, groupSettlements);

        List<UserBalance> balances = net.entrySet().stream()
                .map(e -> new UserBalance(people.get(e.getKey()), e.getValue()))
                .toList();
        List<Debt> pairwise = pairwiseDebts(groupExpenses, groupSettlements).stream()
                .map(t -> new Debt(people.get(t.from()), people.get(t.to()), t.amount()))
                .toList();
        List<Debt> simplified = DebtSimplifier.simplify(net).stream()
                .map(t -> new Debt(people.get(t.from()), people.get(t.to()), t.amount()))
                .toList();

        return new GroupBalancesResponse(groupId, balances, pairwise, simplified);
    }

    @Transactional(readOnly = true)
    public MyBalanceResponse myBalance(Long callerId) {
        List<GroupBalanceSummary> summaries = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        for (ExpenseGroup group : groups.findAllByMemberId(callerId)) {
            Set<Long> memberIds = new LinkedHashSet<>();
            group.getMembers().forEach(u -> memberIds.add(u.getId()));
            Map<Long, BigDecimal> net = netBalances(memberIds,
                    expenses.findAllByGroupIdWithSplits(group.getId()),
                    settlements.findAllByGroupId(group.getId()));
            BigDecimal mine = net.getOrDefault(callerId, BigDecimal.ZERO.setScale(2));
            summaries.add(new GroupBalanceSummary(group.getId(), group.getName(), mine));
            total = total.add(mine);
        }
        return new MyBalanceResponse(total, summaries);
    }

    /**
     * Net balance per user: what they paid (or sent in settlements) minus what they consumed (or received).
     * Positive = the group owes them; negative = they owe the group. Always sums to zero.
     */
    static Map<Long, BigDecimal> netBalances(Collection<Long> memberIds, List<Expense> expenses,
                                             List<Settlement> settlements) {
        Map<Long, BigDecimal> net = new LinkedHashMap<>();
        memberIds.forEach(id -> net.put(id, BigDecimal.ZERO.setScale(2)));
        for (Expense e : expenses) {
            net.merge(e.getPaidBy().getId(), e.getAmount(), BigDecimal::add);
            for (ExpenseSplit s : e.getSplits()) {
                net.merge(s.getUser().getId(), s.getAmount().negate(), BigDecimal::add);
            }
        }
        for (Settlement s : settlements) {
            net.merge(s.getPayer().getId(), s.getAmount(), BigDecimal::add);
            net.merge(s.getPayee().getId(), s.getAmount().negate(), BigDecimal::add);
        }
        return net;
    }

    /** Direct debts between each pair of people (A->B and B->A cancel out), without simplification. */
    static List<DebtSimplifier.Transfer> pairwiseDebts(List<Expense> expenses, List<Settlement> settlements) {
        // key = (low id, high id); positive value = low owes high, negative = high owes low
        Map<List<Long>, BigDecimal> pairs = new TreeMap<>(Comparator.<List<Long>, Long>comparing(k -> k.get(0))
                .thenComparing(k -> k.get(1)));
        for (Expense e : expenses) {
            Long payer = e.getPaidBy().getId();
            for (ExpenseSplit s : e.getSplits()) {
                addDebt(pairs, s.getUser().getId(), payer, s.getAmount());
            }
        }
        for (Settlement s : settlements) {
            // paying someone back reduces what you owe them
            addDebt(pairs, s.getPayer().getId(), s.getPayee().getId(), s.getAmount().negate());
        }
        List<DebtSimplifier.Transfer> result = new ArrayList<>();
        pairs.forEach((key, amount) -> {
            if (amount.signum() > 0) result.add(new DebtSimplifier.Transfer(key.get(0), key.get(1), amount));
            else if (amount.signum() < 0) result.add(new DebtSimplifier.Transfer(key.get(1), key.get(0), amount.negate()));
        });
        return result;
    }

    private static void addDebt(Map<List<Long>, BigDecimal> pairs, Long debtor, Long creditor, BigDecimal amount) {
        if (debtor.equals(creditor)) return;
        if (debtor < creditor) pairs.merge(List.of(debtor, creditor), amount, BigDecimal::add);
        else pairs.merge(List.of(creditor, debtor), amount.negate(), BigDecimal::add);
    }
}
