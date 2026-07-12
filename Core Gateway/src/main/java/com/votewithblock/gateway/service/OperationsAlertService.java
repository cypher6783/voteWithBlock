package com.votewithblock.gateway.service;

import com.votewithblock.gateway.model.AlertSeverity;
import com.votewithblock.gateway.model.AlertStatus;
import com.votewithblock.gateway.model.OperationsAlert;
import com.votewithblock.gateway.repository.OperationsAlertRepository;
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
public class OperationsAlertService {

    private final OperationsAlertRepository alertRepository;

    @Transactional
    public OperationsAlert createAlert(AlertSeverity severity, String source, String message, String metadata) {
        OperationsAlert alert = OperationsAlert.builder()
                .severity(severity)
                .status(AlertStatus.ACTIVE)
                .source(source)
                .message(message)
                .metadata(metadata)
                .createdAt(OffsetDateTime.now())
                .build();
        log.info("Alert created: {} - {} - {}", severity, source, message);
        return alertRepository.save(alert);
    }

    @Transactional
    public OperationsAlert createCorrelatedAlert(AlertSeverity severity, String source, String message, 
                                                  Long parentAlertId, String metadata) {
        OperationsAlert parent = alertRepository.findById(parentAlertId).orElse(null);
        OperationsAlert alert = OperationsAlert.builder()
                .severity(severity)
                .status(AlertStatus.ACTIVE)
                .source(source)
                .message(message)
                .parentAlert(parent)
                .metadata(metadata)
                .createdAt(OffsetDateTime.now())
                .build();
        log.info("Correlated alert created: {} - {} (parent: {})", severity, source, parentAlertId);
        return alertRepository.save(alert);
    }

    @Transactional
    public OperationsAlert acknowledgeAlert(Long alertId, String acknowledgedBy) {
        OperationsAlert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found"));
        alert.setStatus(AlertStatus.ACKNOWLEDGED);
        alert.setAcknowledgedBy(acknowledgedBy);
        alert.setAcknowledgedAt(OffsetDateTime.now());
        log.info("Alert {} acknowledged by {}", alertId, acknowledgedBy);
        return alertRepository.save(alert);
    }

    @Transactional
    public OperationsAlert resolveAlert(Long alertId, String resolvedBy) {
        OperationsAlert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found"));
        alert.setStatus(AlertStatus.RESOLVED);
        alert.setResolvedBy(resolvedBy);
        alert.setResolvedAt(OffsetDateTime.now());
        log.info("Alert {} resolved by {}", alertId, resolvedBy);
        return alertRepository.save(alert);
    }

    public List<OperationsAlert> getActiveAlerts() {
        return alertRepository.findByStatusOrderByCreatedAtDesc(AlertStatus.ACTIVE);
    }

    public Page<OperationsAlert> getAlerts(AlertStatus status, int page, int size) {
        return alertRepository.findByStatus(status, PageRequest.of(page, size));
    }

    public Page<OperationsAlert> getAlertsBySource(String source, int page, int size) {
        return alertRepository.findBySource(source, PageRequest.of(page, size));
    }

    public Map<String, Long> getAlertStats() {
        return Map.of(
            "active", alertRepository.countByStatus(AlertStatus.ACTIVE),
            "acknowledged", alertRepository.countByStatus(AlertStatus.ACKNOWLEDGED),
            "resolved", alertRepository.countByStatus(AlertStatus.RESOLVED),
            "critical", alertRepository.countBySeverity(AlertSeverity.CRITICAL),
            "high", alertRepository.countBySeverity(AlertSeverity.HIGH),
            "medium", alertRepository.countBySeverity(AlertSeverity.MEDIUM),
            "low", alertRepository.countBySeverity(AlertSeverity.LOW)
        );
    }
}
