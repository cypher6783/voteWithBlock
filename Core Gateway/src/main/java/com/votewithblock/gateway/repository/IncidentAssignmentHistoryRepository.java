package com.votewithblock.gateway.repository;

import com.votewithblock.gateway.model.IncidentAssignmentHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IncidentAssignmentHistoryRepository extends JpaRepository<IncidentAssignmentHistory, Long> {
    List<IncidentAssignmentHistory> findByIncidentIdOrderByAssignedAtDesc(Long incidentId);
}
