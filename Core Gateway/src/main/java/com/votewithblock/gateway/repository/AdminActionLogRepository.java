package com.votewithblock.gateway.repository;

import com.votewithblock.gateway.model.AdminActionLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface AdminActionLogRepository extends JpaRepository<AdminActionLog, Long> {
    Page<AdminActionLog> findByActionContaining(String action, Pageable pageable);
    Page<AdminActionLog> findByAdminUsernameHash(String adminUsernameHash, Pageable pageable);
    Page<AdminActionLog> findByTargetEntityType(String targetEntityType, Pageable pageable);
    Page<AdminActionLog> findByOutcome(String outcome, Pageable pageable);
    Page<AdminActionLog> findByPerformedAtBetween(OffsetDateTime start, OffsetDateTime end, Pageable pageable);
    Page<AdminActionLog> findByAdminUsernameHashAndPerformedAtBetween(
        String adminUsernameHash, OffsetDateTime start, OffsetDateTime end, Pageable pageable);
    Page<AdminActionLog> findByTargetEntityTypeAndTargetEntityIdHash(
        String targetType, String targetIdHash, Pageable pageable);
    long countByAction(String action);
    long countByOutcome(String outcome);
    long countByPerformedAtAfter(OffsetDateTime after);
    
    @Query("SELECT a.action, COUNT(a) FROM AdminActionLog a GROUP BY a.action")
    List<Object[]> countByActionGrouped();
    
    @Query("SELECT a.outcome, COUNT(a) FROM AdminActionLog a GROUP BY a.outcome")
    List<Object[]> countByOutcomeGrouped();
    
    List<AdminActionLog> findTop100ByOrderByPerformedAtDesc();
}
