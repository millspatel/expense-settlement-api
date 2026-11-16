package com.splitwise.repository;

import com.splitwise.model.ExpenseGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GroupRepository extends JpaRepository<ExpenseGroup, Long> {

    @Query("select g from ExpenseGroup g join g.members m where m.id = :userId order by g.id")
    List<ExpenseGroup> findAllByMemberId(@Param("userId") Long userId);
}
