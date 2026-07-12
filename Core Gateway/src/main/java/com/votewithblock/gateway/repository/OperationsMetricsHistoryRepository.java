package com.votewithblock.gateway.repository;

import com.votewithblock.gateway.model.OperationsMetricsHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface OperationsMetricsHistoryRepository extends JpaRepository<OperationsMetricsHistory, Long> {
    List<OperationsMetricsHistory> findByMetricTypeAndRecordedAtBetween(
        String metricType, OffsetDateTime start, OffsetDateTime end);
    List<OperationsMetricsHistory> findByElectionIdAndRecordedAtBetween(
        UUID electionId, OffsetDateTime start, OffsetDateTime end);
    List<OperationsMetricsHistory> findByMetricTypeAndElectionIdAndRecordedAtBetween(
        String metricType, UUID electionId, OffsetDateTime start, OffsetDateTime end);
}
