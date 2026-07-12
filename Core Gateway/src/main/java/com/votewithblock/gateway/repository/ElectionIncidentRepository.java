package com.votewithblock.gateway.repository;

import com.votewithblock.gateway.model.ElectionIncident;
import com.votewithblock.gateway.model.IncidentSeverity;
import com.votewithblock.gateway.model.IncidentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ElectionIncidentRepository extends JpaRepository<ElectionIncident, Long> {
    List<ElectionIncident> findByElectionIdOrderByCreatedAtDesc(UUID electionId);
    Page<ElectionIncident> findByStatus(IncidentStatus status, Pageable pageable);
    Page<ElectionIncident> findBySeverity(IncidentSeverity severity, Pageable pageable);
    Page<ElectionIncident> findByAssignedTo(String assignedTo, Pageable pageable);
    Page<ElectionIncident> findByCreatedAtBetween(OffsetDateTime start, OffsetDateTime end, Pageable pageable);
    long countByStatus(IncidentStatus status);
    long countBySeverity(IncidentSeverity severity);
    long countByElectionIdAndStatus(UUID electionId, IncidentStatus status);
    
    @Query("SELECT i.severity, COUNT(i) FROM ElectionIncident i GROUP BY i.severity")
    List<Object[]> countBySeverityGrouped();
    
    @Query("SELECT i.status, COUNT(i) FROM ElectionIncident i GROUP BY i.status")
    List<Object[]> countByStatusGrouped();
}
