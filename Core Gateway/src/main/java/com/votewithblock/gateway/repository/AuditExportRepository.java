package com.votewithblock.gateway.repository;

import com.votewithblock.gateway.model.AuditExport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface AuditExportRepository extends JpaRepository<AuditExport, Long> {
    List<AuditExport> findByGeneratedByOrderByGeneratedAtDesc(String generatedBy);
    List<AuditExport> findByGeneratedAtBetween(OffsetDateTime start, OffsetDateTime end);
}
