package com.votewithblock.gateway.service;

import com.votewithblock.gateway.model.RecoveryAuditLog;
import com.votewithblock.gateway.repository.RecoveryAuditLogRepository;
import com.votewithblock.gateway.util.HashUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class RecoveryAuditService {

    private final RecoveryAuditLogRepository recoveryAuditLogRepository;

    @Transactional
    public RecoveryAuditLog logRecoveryAction(String actionType, String performedBy,
                                               String targetComponent, String details,
                                               String outcome, Long durationMs) {
        String content = String.join("|", actionType, performedBy, targetComponent,
                details, outcome, String.valueOf(System.currentTimeMillis()));
        String sha256Hash = HashUtil.sha256Hex(content);

        RecoveryAuditLog auditLog = RecoveryAuditLog.builder()
                .actionType(actionType)
                .performedBy(performedBy)
                .performedAt(OffsetDateTime.now())
                .targetComponent(targetComponent)
                .details(details)
                .outcome(outcome)
                .durationMs(durationMs)
                .sha256Hash(sha256Hash)
                .build();

        log.info("Recovery audit: {} on {} by {} - {} ({})", actionType, targetComponent, performedBy, outcome, details);
        return recoveryAuditLogRepository.save(auditLog);
    }

    @Transactional
    public RecoveryAuditLog logRecoveryAction(String actionType, String performedBy,
                                               String targetComponent, String details,
                                               String outcome, Long durationMs, String metadata) {
        String content = String.join("|", actionType, performedBy, targetComponent,
                details, outcome, String.valueOf(System.currentTimeMillis()));
        String sha256Hash = HashUtil.sha256Hex(content);

        RecoveryAuditLog auditLog = RecoveryAuditLog.builder()
                .actionType(actionType)
                .performedBy(performedBy)
                .performedAt(OffsetDateTime.now())
                .targetComponent(targetComponent)
                .details(details)
                .outcome(outcome)
                .durationMs(durationMs)
                .metadata(metadata)
                .sha256Hash(sha256Hash)
                .build();

        log.info("Recovery audit: {} on {} by {} - {} ({})", actionType, targetComponent, performedBy, outcome, details);
        return recoveryAuditLogRepository.save(auditLog);
    }

    public Page<RecoveryAuditLog> getAuditLogs(String actionType, int page, int size) {
        if (actionType != null) {
            return recoveryAuditLogRepository.findByActionType(actionType, PageRequest.of(page, size));
        }
        return recoveryAuditLogRepository.findAll(PageRequest.of(page, size));
    }

    public Page<RecoveryAuditLog> getAuditLogsInRange(OffsetDateTime start, OffsetDateTime end, int page, int size) {
        return recoveryAuditLogRepository.findByPerformedAtBetween(start, end, PageRequest.of(page, size));
    }

    public Map<String, Long> getAuditStats() {
        return Map.of(
            "total24h", recoveryAuditLogRepository.countByPerformedAtAfter(OffsetDateTime.now().minusHours(24)),
            "total7d", recoveryAuditLogRepository.countByPerformedAtAfter(OffsetDateTime.now().minusDays(7))
        );
    }

    public List<Object[]> getActionBreakdown() {
        return recoveryAuditLogRepository.countByActionTypeGrouped();
    }

    public List<Object[]> getComponentBreakdown() {
        return recoveryAuditLogRepository.countByTargetComponentGrouped();
    }
}
