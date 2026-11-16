package com.splitwise.core;

import com.splitwise.core.SplitCalculator.Participant;
import com.splitwise.model.SplitType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SplitCalculatorTest {

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    private static List<Participant> users(Long... ids) {
        return java.util.Arrays.stream(ids).map(id -> new Participant(id, null)).toList();
    }

    private static BigDecimal sum(Map<Long, BigDecimal> shares) {
        return shares.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void equalSplitDividesEvenly() {
        Map<Long, BigDecimal> shares = SplitCalculator.split(bd("300"), SplitType.EQUAL, users(1L, 2L, 3L));
        assertThat(shares).containsExactly(
                Map.entry(1L, bd("100.00")), Map.entry(2L, bd("100.00")), Map.entry(3L, bd("100.00")));
    }

    @Test
    void equalSplitHandsOutLeftoverPaise() {
        Map<Long, BigDecimal> shares = SplitCalculator.split(bd("100"), SplitType.EQUAL, users(1L, 2L, 3L));
        assertThat(shares.values()).containsExactly(bd("33.34"), bd("33.33"), bd("33.33"));
        assertThat(sum(shares)).isEqualByComparingTo("100");
    }

    @Test
    void percentageSplit() {
        Map<Long, BigDecimal> shares = SplitCalculator.split(bd("1000"), SplitType.PERCENTAGE, List.of(
                new Participant(1L, bd("50")), new Participant(2L, bd("30")), new Participant(3L, bd("20"))));
        assertThat(shares.values()).containsExactly(bd("500.00"), bd("300.00"), bd("200.00"));
    }

    @Test
    void percentageSplitAlwaysSumsToTotal() {
        Map<Long, BigDecimal> shares = SplitCalculator.split(bd("10"), SplitType.PERCENTAGE, List.of(
                new Participant(1L, bd("33.33")), new Participant(2L, bd("33.33")), new Participant(3L, bd("33.34"))));
        assertThat(sum(shares)).isEqualByComparingTo("10");
    }

    @Test
    void percentagesMustAddUpTo100() {
        assertThatThrownBy(() -> SplitCalculator.split(bd("100"), SplitType.PERCENTAGE, List.of(
                new Participant(1L, bd("50")), new Participant(2L, bd("40")))))
                .isInstanceOf(InvalidSplitException.class)
                .hasMessageContaining("100");
    }

    @Test
    void exactSplit() {
        Map<Long, BigDecimal> shares = SplitCalculator.split(bd("250.50"), SplitType.EXACT, List.of(
                new Participant(1L, bd("100.25")), new Participant(2L, bd("150.25"))));
        assertThat(shares.values()).containsExactly(bd("100.25"), bd("150.25"));
    }

    @Test
    void exactAmountsMustAddUpToTotal() {
        assertThatThrownBy(() -> SplitCalculator.split(bd("100"), SplitType.EXACT, List.of(
                new Participant(1L, bd("60")), new Participant(2L, bd("30")))))
                .isInstanceOf(InvalidSplitException.class)
                .hasMessageContaining("100.00");
    }

    @Test
    void rejectsDuplicateParticipants() {
        assertThatThrownBy(() -> SplitCalculator.split(bd("100"), SplitType.EQUAL, users(1L, 1L)))
                .isInstanceOf(InvalidSplitException.class);
    }

    @Test
    void rejectsMoreThanTwoDecimals() {
        assertThatThrownBy(() -> SplitCalculator.split(bd("10.001"), SplitType.EQUAL, users(1L)))
                .isInstanceOf(InvalidSplitException.class);
    }

    @Test
    void rejectsNonPositiveAmount() {
        assertThatThrownBy(() -> SplitCalculator.split(bd("0"), SplitType.EQUAL, users(1L)))
                .isInstanceOf(InvalidSplitException.class);
    }
}
