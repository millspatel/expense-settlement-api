package com.splitwise.service;

import com.splitwise.core.SplitCalculator;
import com.splitwise.core.SplitCalculator.Participant;
import com.splitwise.dto.ExpenseDtos.*;
import com.splitwise.exception.ApiException;
import com.splitwise.model.*;
import com.splitwise.repository.ExpenseRepository;
import com.splitwise.repository.SettlementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ExpenseService {

    private final GroupService groupService;
    private final ExpenseRepository expenses;
    private final SettlementRepository settlements;

    public ExpenseService(GroupService groupService, ExpenseRepository expenses, SettlementRepository settlements) {
        this.groupService = groupService;
        this.expenses = expenses;
        this.settlements = settlements;
    }

    @Transactional
    public ExpenseResponse addExpense(Long callerId, Long groupId, CreateExpenseRequest request) {
        ExpenseGroup group = groupService.requireMembership(callerId, groupId);
        Map<Long, User> members = membersById(group);

        Long payerId = request.paidByUserId() != null ? request.paidByUserId() : callerId;
        User payer = requireMember(members, payerId);

        List<Participant> participants;
        if (request.splits() == null || request.splits().isEmpty()) {
            if (request.splitType() != SplitType.EQUAL) {
                throw ApiException.badRequest("'splits' is required for " + request.splitType() + " splits");
            }
            participants = members.keySet().stream().map(id -> new Participant(id, null)).toList();
        } else {
            participants = request.splits().stream().map(s -> new Participant(s.userId(), s.value())).toList();
        }
        participants.forEach(p -> requireMember(members, p.userId()));

        Map<Long, BigDecimal> shares = SplitCalculator.split(request.amount(), request.splitType(), participants);

        Expense expense = new Expense(group, request.description().trim(), request.amount().setScale(2),
                payer, members.get(callerId), request.splitType());
        shares.forEach((userId, share) -> expense.addSplit(members.get(userId), share));
        return ExpenseResponse.from(expenses.save(expense));
    }

    @Transactional(readOnly = true)
    public List<ExpenseResponse> listExpenses(Long callerId, Long groupId) {
        groupService.requireMembership(callerId, groupId);
        return expenses.findAllByGroupIdWithSplits(groupId).stream().map(ExpenseResponse::from).toList();
    }

    @Transactional
    public SettlementResponse settleUp(Long callerId, Long groupId, SettlementRequest request) {
        ExpenseGroup group = groupService.requireMembership(callerId, groupId);
        Map<Long, User> members = membersById(group);
        User payer = requireMember(members, request.payerUserId() != null ? request.payerUserId() : callerId);
        User payee = requireMember(members, request.payeeUserId());
        if (payer.getId().equals(payee.getId())) {
            throw ApiException.badRequest("Payer and payee must be different users");
        }
        Settlement saved = settlements.save(new Settlement(group, payer, payee, request.amount().setScale(2)));
        return SettlementResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<SettlementResponse> listSettlements(Long callerId, Long groupId) {
        groupService.requireMembership(callerId, groupId);
        return settlements.findAllByGroupId(groupId).stream().map(SettlementResponse::from).toList();
    }

    private static Map<Long, User> membersById(ExpenseGroup group) {
        return group.getMembers().stream().collect(Collectors.toMap(User::getId, Function.identity(),
                (a, b) -> a, java.util.LinkedHashMap::new));
    }

    private static User requireMember(Map<Long, User> members, Long userId) {
        User user = members.get(userId);
        if (user == null) {
            throw ApiException.badRequest("User " + userId + " is not a member of this group");
        }
        return user;
    }
}
