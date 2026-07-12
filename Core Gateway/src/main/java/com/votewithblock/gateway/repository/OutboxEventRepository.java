package com.votewithblock.gateway.repository;

import com.votewithblock.gateway.model.OutboxEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(String status);

    Page<OutboxEvent> findByStatus(String status, PageRequest pageRequest);

    @Query("SELECT o FROM OutboxEvent o WHERE o.status = 'PENDING' AND o.retryCount < o.maxRetries ORDER BY o.createdAt ASC")
    List<OutboxEvent> findProcessableEvents();

    long countByStatus(String status);

    @Query("SELECT o.eventType, COUNT(o) FROM OutboxEvent o GROUP BY o.eventType")
    List<Object[]> countByEventTypeGrouped();
}
