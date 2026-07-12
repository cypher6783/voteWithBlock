package com.votewithblock.gateway.repository;

import com.votewithblock.gateway.model.RecoveryAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface RecoveryAuditLogRepository extends JpaRepository<RecoveryAuditLog, Long> {

    Page<RecoveryAuditLog> findByActionType(String actionType, PageRequest pageRequest);

    Page<RecoveryAuditLog> findByTargetComponent(String targetComponent, PageRequest pageRequest);

    Page<RecoveryAuditLog> findByPerformedAtBetween(OffsetDateTime start, OffsetDateTime end, PageRequest pageRequest);

    Page<RecoveryAuditLog> findByActionTypeAndPerformedAtBetween(String actionType, OffsetDateTime start, OffsetDateTime end, PageRequest pageRequest);

    @Query("SELECT r.actionType, COUNT(r) FROM RecoveryAuditLog r GROUP BY r.actionType")
    List<Object[]> countByActionTypeGrouped();

    @Query("SELECT r.targetComponent, COUNT(r) FROM RecoveryAuditLog r GROUP BY r.targetComponent")
    List<Object[]> countByTargetComponentGrouped();

    long countByPerformedAtAfter(OffsetDateTime after);
}
