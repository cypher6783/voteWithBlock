package com.votewithblock.gateway.service;

import com.votewithblock.gateway.model.AdminActionLog;
import com.votewithblock.gateway.model.AuditExport;
import com.votewithblock.gateway.repository.AdminActionLogRepository;
import com.votewithblock.gateway.repository.AuditExportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuditAnalyticsService {

    private final AdminActionLogRepository adminActionLogRepository;
    private final AuditExportRepository auditExportRepository;

    public Page<AdminActionLog> getAuditLogs(String action, String adminUsernameHash, 
                                              String targetEntityType, String outcome,
                                              OffsetDateTime start, OffsetDateTime end,
                                              int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        
        if (adminUsernameHash != null && start != null && end != null) {
            return adminActionLogRepository.findByAdminUsernameHashAndPerformedAtBetween(
                adminUsernameHash, start, end, pageRequest);
        }
        if (targetEntityType != null) {
            return adminActionLogRepository.findByTargetEntityType(targetEntityType, pageRequest);
        }
        if (action != null) {
            return adminActionLogRepository.findByActionContaining(action, pageRequest);
        }
        if (outcome != null) {
            return adminActionLogRepository.findByOutcome(outcome, pageRequest);
        }
        if (start != null && end != null) {
            return adminActionLogRepository.findByPerformedAtBetween(start, end, pageRequest);
        }
        return adminActionLogRepository.findAll(pageRequest);
    }

    public AdminActionLog getAuditLog(Long id) {
        return adminActionLogRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Audit log not found"));
    }

    public Map<String, Long> getAuditStats() {
        return Map.of(
            "total", adminActionLogRepository.count(),
            "total24h", adminActionLogRepository.countByPerformedAtAfter(OffsetDateTime.now().minusHours(24)),
            "total7d", adminActionLogRepository.countByPerformedAtAfter(OffsetDateTime.now().minusDays(7)),
            "successCount", adminActionLogRepository.countByOutcome("SUCCESS"),
            "failCount", adminActionLogRepository.countByOutcome("FAIL")
        );
    }

    public List<Object[]> getActionBreakdown() {
        return adminActionLogRepository.countByActionGrouped();
    }

    public List<Object[]> getOutcomeBreakdown() {
        return adminActionLogRepository.countByOutcomeGrouped();
    }

    @Transactional
    public AuditExport exportAuditLogs(String generatedBy, OffsetDateTime start, OffsetDateTime end) {
        List<AdminActionLog> logs;
        if (start != null && end != null) {
            logs = adminActionLogRepository.findByPerformedAtBetween(start, end, PageRequest.of(0, Integer.MAX_VALUE)).getContent();
        } else {
            logs = adminActionLogRepository.findTop100ByOrderByPerformedAtDesc();
        }

        StringBuilder content = new StringBuilder();
        content.append("id,admin_hash,action,target_type,target_hash,outcome,ip_hash,performed_at,metadata\n");
        for (AdminActionLog log : logs) {
            content.append(String.format("%d,%s,%s,%s,%s,%s,%s,%s,%s\n",
                log.getId(),
                log.getAdminUsernameHash(),
                log.getAction(),
                log.getTargetEntityType(),
                log.getTargetEntityIdHash(),
                log.getOutcome(),
                log.getIpHash(),
                log.getPerformedAt(),
                log.getMetadata()));
        }

        String sha256Hash = computeSha256(content.toString());

        AuditExport export = AuditExport.builder()
                .generatedAt(OffsetDateTime.now())
                .generatedBy(generatedBy)
                .recordCount(logs.size())
                .sha256Hash(sha256Hash)
                .build();
        
        return auditExportRepository.save(export);
    }

    private String computeSha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute SHA-256", e);
        }
    }
}
