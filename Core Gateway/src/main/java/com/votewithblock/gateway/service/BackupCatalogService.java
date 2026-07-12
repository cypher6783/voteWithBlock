package com.votewithblock.gateway.service;

import com.votewithblock.gateway.model.BackupCatalog;
import com.votewithblock.gateway.repository.BackupCatalogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class BackupCatalogService {

    private final BackupCatalogRepository backupCatalogRepository;

    @Transactional
    public BackupCatalog recordBackup(String backupType, long sizeBytes, String sha256Hash,
                                       String storageLocation, int retentionDays) {
        BackupCatalog catalog = BackupCatalog.builder()
                .backupId("bk-" + UUID.randomUUID().toString().substring(0, 8))
                .backupType(backupType)
                .createdAt(OffsetDateTime.now())
                .sizeBytes(sizeBytes)
                .sha256Hash(sha256Hash)
                .storageLocation(storageLocation)
                .retentionDays(retentionDays)
                .build();

        log.info("Backup recorded: {} ({}) - {} bytes at {}", backupType, catalog.getBackupId(), sizeBytes, storageLocation);
        return backupCatalogRepository.save(catalog);
    }

    @Transactional
    public BackupCatalog verifyBackup(String backupId) {
        BackupCatalog catalog = backupCatalogRepository.findByBackupId(backupId)
                .orElseThrow(() -> new IllegalArgumentException("Backup not found: " + backupId));
        catalog.setVerified(true);
        catalog.setVerifiedAt(OffsetDateTime.now());
        log.info("Backup verified: {}", backupId);
        return backupCatalogRepository.save(catalog);
    }

    public Page<BackupCatalog> getBackups(String backupType, int page, int size) {
        if (backupType != null) {
            return backupCatalogRepository.findByBackupType(backupType, PageRequest.of(page, size));
        }
        return backupCatalogRepository.findAll(PageRequest.of(page, size));
    }

    public Map<String, Long> getBackupStats() {
        return Map.of(
            "total", backupCatalogRepository.count(),
            "verified", backupCatalogRepository.countByVerified(true),
            "unverified", backupCatalogRepository.countByVerified(false)
        );
    }

    public List<Object[]> getBackupTypeBreakdown() {
        return backupCatalogRepository.countByBackupTypeGrouped();
    }

    public List<BackupCatalog> getUnverifiedBackups() {
        return backupCatalogRepository.findByVerifiedFalse();
    }
}
