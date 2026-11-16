package com.splitwise.repository;

import com.splitwise.model.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    /** Loads a group's expenses with payer and splits in one query (avoids N+1). Newest first. */
    @Query("""
            select distinct e from Expense e
            join fetch e.paidBy
            left join fetch e.splits s
            left join fetch s.user
            where e.group.id = :groupId
            order by e.createdAt desc, e.id desc
            """)
    List<Expense> findAllByGroupIdWithSplits(@Param("groupId") Long groupId);
}
