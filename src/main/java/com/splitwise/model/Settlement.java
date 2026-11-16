package com.splitwise.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/** A recorded payment from one member to another that pays down debt ("settle up"). */
@Entity
@Table(name = "settlements")
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private ExpenseGroup group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User payer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User payee;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Settlement() {
    }

    public Settlement(ExpenseGroup group, User payer, User payee, BigDecimal amount) {
        this.group = group;
        this.payer = payer;
        this.payee = payee;
        this.amount = amount;
    }

    public Long getId() {
        return id;
    }

    public ExpenseGroup getGroup() {
        return group;
    }

    public User getPayer() {
        return payer;
    }

    public User getPayee() {
        return payee;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
