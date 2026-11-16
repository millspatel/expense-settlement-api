package com.splitwise.dto;

import com.splitwise.model.Expense;
import com.splitwise.model.Settlement;
import com.splitwise.model.SplitType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class ExpenseDtos {

    private ExpenseDtos() {
    }

    /**
     * @param paidByUserId optional, defaults to the caller
     * @param splits       for EQUAL: optional list of participants (value ignored), defaults to all members;
     *                     for PERCENTAGE: value is the percentage; for EXACT: value is the amount
     */
    public record CreateExpenseRequest(
            @NotBlank @Size(max = 200) String description,
            @NotNull @Positive @Digits(integer = 12, fraction = 2) BigDecimal amount,
            Long paidByUserId,
            @NotNull SplitType splitType,
            List<@Valid SplitRequest> splits) {
    }

    public record SplitRequest(@NotNull Long userId, BigDecimal value) {
    }

    public record ShareResponse(UserResponse user, BigDecimal amount) {
    }

    public record ExpenseResponse(Long id, String description, BigDecimal amount, UserResponse paidBy,
                                  SplitType splitType, List<ShareResponse> splits, Instant createdAt) {

        public static ExpenseResponse from(Expense e) {
            return new ExpenseResponse(e.getId(), e.getDescription(), e.getAmount(), UserResponse.from(e.getPaidBy()),
                    e.getSplitType(),
                    e.getSplits().stream().map(s -> new ShareResponse(UserResponse.from(s.getUser()), s.getAmount())).toList(),
                    e.getCreatedAt());
        }
    }

    /** @param payerUserId optional, defaults to the caller */
    public record SettlementRequest(
            Long payerUserId,
            @NotNull Long payeeUserId,
            @NotNull @Positive @Digits(integer = 12, fraction = 2) BigDecimal amount) {
    }

    public record SettlementResponse(Long id, UserResponse payer, UserResponse payee, BigDecimal amount,
                                     Instant createdAt) {

        public static SettlementResponse from(Settlement s) {
            return new SettlementResponse(s.getId(), UserResponse.from(s.getPayer()), UserResponse.from(s.getPayee()),
                    s.getAmount(), s.getCreatedAt());
        }
    }
}
