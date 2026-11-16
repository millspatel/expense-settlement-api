package com.splitwise.service;

import com.splitwise.dto.ExpenseDtos.CreateExpenseRequest;
import com.splitwise.dto.ExpenseDtos.ExpenseResponse;
import com.splitwise.dto.ExpenseDtos.SettlementRequest;
import com.splitwise.exception.ApiException;
import com.splitwise.model.Expense;
import com.splitwise.model.ExpenseGroup;
import com.splitwise.model.ExpenseSplit;
import com.splitwise.model.SplitType;
import com.splitwise.model.User;
import com.splitwise.repository.ExpenseRepository;
import com.splitwise.repository.SettlementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Unit tests for {@link ExpenseService}: GroupService and both repositories are Mockito mocks. */
@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    @Mock
    private GroupService groupService;
    @Mock
    private ExpenseRepository expenses;
    @Mock
    private SettlementRepository settlements;

    @InjectMocks
    private ExpenseService expenseService;

    private ExpenseGroup trip;

    private static User user(long id) {
        User u = new User("User " + id, "user" + id + "@example.com", "hash");
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    @BeforeEach
    void setUp() {
        trip = new ExpenseGroup("Goa Trip", user(1L));
        trip.addMember(user(2L));
        trip.addMember(user(3L));
        ReflectionTestUtils.setField(trip, "id", 10L);
    }

    @Test
    void equalSplitAcrossAllMembersSumsExactlyToTotal() {
        when(groupService.requireMembership(1L, 10L)).thenReturn(trip);
        when(expenses.save(any(Expense.class))).thenAnswer(inv -> inv.getArgument(0));

        ExpenseResponse response = expenseService.addExpense(1L, 10L,
                new CreateExpenseRequest("Dinner", new BigDecimal("100"), null, SplitType.EQUAL, null));

        ArgumentCaptor<Expense> captor = ArgumentCaptor.forClass(Expense.class);
        verify(expenses).save(captor.capture());
        Expense saved = captor.getValue();
        assertThat(saved.getPaidBy().getId()).isEqualTo(1L);
        assertThat(saved.getSplits()).extracting(ExpenseSplit::getAmount)
                .containsExactly(new BigDecimal("33.34"), new BigDecimal("33.33"), new BigDecimal("33.33"));
        assertThat(saved.getSplits().stream().map(ExpenseSplit::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("100.00");
        assertThat(response.amount()).isEqualByComparingTo("100.00");
    }

    @Test
    void payerOutsideGroupIsRejectedAndNothingIsSaved() {
        when(groupService.requireMembership(1L, 10L)).thenReturn(trip);

        assertThatThrownBy(() -> expenseService.addExpense(1L, 10L,
                new CreateExpenseRequest("Dinner", new BigDecimal("100"), 99L, SplitType.EQUAL, null)))
                .isInstanceOf(ApiException.class)
                .extracting("status").isEqualTo(HttpStatus.BAD_REQUEST);

        verify(expenses, never()).save(any());
    }

    @Test
    void nonMemberCannotTouchRepositories() {
        when(groupService.requireMembership(99L, 10L))
                .thenThrow(ApiException.forbidden("You are not a member of this group"));

        assertThatThrownBy(() -> expenseService.listExpenses(99L, 10L))
                .isInstanceOf(ApiException.class)
                .extracting("status").isEqualTo(HttpStatus.FORBIDDEN);

        verifyNoInteractions(expenses, settlements);
    }

    @Test
    void settlingWithYourselfIsRejected() {
        when(groupService.requireMembership(1L, 10L)).thenReturn(trip);

        assertThatThrownBy(() -> expenseService.settleUp(1L, 10L,
                new SettlementRequest(null, 1L, new BigDecimal("50"))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("different users");

        verify(settlements, never()).save(any());
    }
}
