package com.splitwise.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

/** A group of people sharing expenses ("group" is a reserved word in SQL, hence the name). */
@Entity
@Table(name = "expense_groups")
public class ExpenseGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User createdBy;

    @ManyToMany
    @JoinTable(name = "group_members",
            joinColumns = @JoinColumn(name = "group_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id"))
    @OrderBy("id")
    private Set<User> members = new LinkedHashSet<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected ExpenseGroup() {
    }

    public ExpenseGroup(String name, User createdBy) {
        this.name = name;
        this.createdBy = createdBy;
        this.members.add(createdBy);
    }

    public boolean hasMember(Long userId) {
        return members.stream().anyMatch(m -> m.getId().equals(userId));
    }

    public void addMember(User user) {
        members.add(user);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public Set<User> getMembers() {
        return members;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
