package com.splitwise.controller;

import com.splitwise.dto.BalanceDtos.GroupBalancesResponse;
import com.splitwise.dto.ExpenseDtos.*;
import com.splitwise.dto.GroupDtos.AddMemberRequest;
import com.splitwise.dto.GroupDtos.CreateGroupRequest;
import com.splitwise.dto.GroupDtos.GroupResponse;
import com.splitwise.security.UserPrincipal;
import com.splitwise.service.BalanceService;
import com.splitwise.service.ExpenseService;
import com.splitwise.service.GroupService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final GroupService groupService;
    private final ExpenseService expenseService;
    private final BalanceService balanceService;

    public GroupController(GroupService groupService, ExpenseService expenseService, BalanceService balanceService) {
        this.groupService = groupService;
        this.expenseService = expenseService;
        this.balanceService = balanceService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GroupResponse create(@AuthenticationPrincipal UserPrincipal me, @Valid @RequestBody CreateGroupRequest request) {
        return groupService.create(me.id(), request);
    }

    @GetMapping
    public List<GroupResponse> list(@AuthenticationPrincipal UserPrincipal me) {
        return groupService.listForUser(me.id());
    }

    @GetMapping("/{groupId}")
    public GroupResponse get(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long groupId) {
        return groupService.get(me.id(), groupId);
    }

    @PostMapping("/{groupId}/members")
    public GroupResponse addMember(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long groupId,
                                   @Valid @RequestBody AddMemberRequest request) {
        return groupService.addMember(me.id(), groupId, request.email());
    }

    @PostMapping("/{groupId}/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseResponse addExpense(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long groupId,
                                      @Valid @RequestBody CreateExpenseRequest request) {
        return expenseService.addExpense(me.id(), groupId, request);
    }

    /** Expense history, newest first. */
    @GetMapping("/{groupId}/expenses")
    public List<ExpenseResponse> listExpenses(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long groupId) {
        return expenseService.listExpenses(me.id(), groupId);
    }

    @PostMapping("/{groupId}/settlements")
    @ResponseStatus(HttpStatus.CREATED)
    public SettlementResponse settleUp(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long groupId,
                                       @Valid @RequestBody SettlementRequest request) {
        return expenseService.settleUp(me.id(), groupId, request);
    }

    @GetMapping("/{groupId}/settlements")
    public List<SettlementResponse> listSettlements(@AuthenticationPrincipal UserPrincipal me,
                                                    @PathVariable Long groupId) {
        return expenseService.listSettlements(me.id(), groupId);
    }

    /** Net balances, pairwise debts, and the simplified list of payments that settles the group. */
    @GetMapping("/{groupId}/balances")
    public GroupBalancesResponse balances(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long groupId) {
        return balanceService.groupBalances(me.id(), groupId);
    }
}
