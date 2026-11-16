package com.splitwise.dto;

import java.math.BigDecimal;
import java.util.List;

public final class BalanceDtos {

    private BalanceDtos() {
    }

    /** Positive balance = the user is owed money; negative = the user owes money. */
    public record UserBalance(UserResponse user, BigDecimal balance) {
    }

    public record Debt(UserResponse from, UserResponse to, BigDecimal amount) {
    }

    /**
     * @param balances        net balance per member
     * @param pairwiseDebts   who owes whom, netted per pair, before simplification
     * @param simplifiedDebts the minimal-ish set of payments that settles the group
     */
    public record GroupBalancesResponse(Long groupId, List<UserBalance> balances, List<Debt> pairwiseDebts,
                                        List<Debt> simplifiedDebts) {
    }

    public record GroupBalanceSummary(Long groupId, String groupName, BigDecimal balance) {
    }

    /** The caller's position across every group they belong to. */
    public record MyBalanceResponse(BigDecimal totalBalance, List<GroupBalanceSummary> groups) {
    }
}
