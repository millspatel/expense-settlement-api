package com.splitwise.repository;

import com.splitwise.model.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    @Query("""
            select s from Settlement s
            join fetch s.payer
            join fetch s.payee
            where s.group.id = :groupId
            order by s.createdAt desc, s.id desc
            """)
    List<Settlement> findAllByGroupId(@Param("groupId") Long groupId);
}
