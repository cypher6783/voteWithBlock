package com.votewithblock.gateway.repository;

import com.votewithblock.gateway.model.BackupCatalog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BackupCatalogRepository extends JpaRepository<BackupCatalog, Long> {

    Optional<BackupCatalog> findByBackupId(String backupId);

    Page<BackupCatalog> findByBackupType(String backupType, PageRequest pageRequest);

    List<BackupCatalog> findByVerifiedFalse();

    @Query("SELECT b FROM BackupCatalog b WHERE b.verifiedAt < :cutoffDate")
    List<BackupCatalog> findExpiredBackups(java.time.OffsetDateTime cutoffDate);

    long countByVerified(boolean verified);

    @Query("SELECT b.backupType, COUNT(b) FROM BackupCatalog b GROUP BY b.backupType")
    List<Object[]> countByBackupTypeGrouped();
}
