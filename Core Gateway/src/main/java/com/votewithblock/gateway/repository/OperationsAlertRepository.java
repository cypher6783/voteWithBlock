package com.votewithblock.gateway.repository;

import com.votewithblock.gateway.model.AlertSeverity;
import com.votewithblock.gateway.model.AlertStatus;
import com.votewithblock.gateway.model.OperationsAlert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OperationsAlertRepository extends JpaRepository<OperationsAlert, Long> {
    List<OperationsAlert> findByStatusOrderByCreatedAtDesc(AlertStatus status);
    Page<OperationsAlert> findByStatus(AlertStatus status, Pageable pageable);
    Page<OperationsAlert> findBySource(String source, Pageable pageable);
    long countByStatus(AlertStatus status);
    long countBySeverity(AlertSeverity severity);
    List<OperationsAlert> findTop10ByStatusOrderByCreatedAtDesc(AlertStatus status);
}
