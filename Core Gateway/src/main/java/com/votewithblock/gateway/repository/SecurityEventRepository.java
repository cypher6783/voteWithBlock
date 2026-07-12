package com.votewithblock.gateway.repository;

import com.votewithblock.gateway.model.SecurityEvent;
import com.votewithblock.gateway.model.SecurityEventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface SecurityEventRepository extends JpaRepository<SecurityEvent, Long> {
    Page<SecurityEvent> findByEventType(SecurityEventType eventType, Pageable pageable);
    Page<SecurityEvent> findByOccurredAtBetween(OffsetDateTime start, OffsetDateTime end, Pageable pageable);
    Page<SecurityEvent> findByEventTypeAndOccurredAtBetween(SecurityEventType eventType, OffsetDateTime start, OffsetDateTime end, Pageable pageable);
    long countByEventType(SecurityEventType eventType);
    long countByOccurredAtAfter(OffsetDateTime after);
    
    @Query("SELECT s.eventType, COUNT(s) FROM SecurityEvent s GROUP BY s.eventType")
    List<Object[]> countByEventTypeGrouped();
    
    @Query("SELECT s FROM SecurityEvent s ORDER BY s.occurredAt DESC")
    List<SecurityEvent> findTop100ByOrderByOccurredAtDesc(Pageable pageable);
}
