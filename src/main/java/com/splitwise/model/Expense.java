package com.splitwise.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "expenses")
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private ExpenseGroup group;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User paidBy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User createdBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SplitType splitType;

    @OneToMany(mappedBy = "expense", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<ExpenseSplit> splits = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Expense() {
    }

    public Expense(ExpenseGroup group, String description, BigDecimal amount,
                   User paidBy, User createdBy, SplitType splitType) {
        this.group = group;
        this.description = description;
        this.amount = amount;
        this.paidBy = paidBy;
        this.createdBy = createdBy;
        this.splitType = splitType;
    }

    public void addSplit(User user, BigDecimal share) {
        splits.add(new ExpenseSplit(this, user, share));
    }

    public Long getId() {
        return id;
    }

    public ExpenseGroup getGroup() {
        return group;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public User getPaidBy() {
        return paidBy;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public SplitType getSplitType() {
        return splitType;
    }

    public List<ExpenseSplit> getSplits() {
        return splits;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
