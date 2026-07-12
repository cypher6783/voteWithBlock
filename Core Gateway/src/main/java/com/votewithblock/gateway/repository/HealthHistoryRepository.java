package com.votewithblock.gateway.repository;

import com.votewithblock.gateway.model.HealthHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface HealthHistoryRepository extends JpaRepository<HealthHistory, Long> {

    List<HealthHistory> findByComponentAndRecordedAtBetween(
        String component, OffsetDateTime start, OffsetDateTime end);

    List<HealthHistory> findByRecordedAtBetween(OffsetDateTime start, OffsetDateTime end);

    @Query("SELECT h FROM HealthHistory h WHERE h.component = :component AND h.recordedAt >= :since ORDER BY h.recordedAt DESC")
    List<HealthHistory> findRecentByComponent(@Param("component") String component, @Param("since") OffsetDateTime since);

    void deleteByRecordedAtBefore(OffsetDateTime cutoff);
}
