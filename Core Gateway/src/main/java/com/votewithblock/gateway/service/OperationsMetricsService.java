package com.votewithblock.gateway.service;

import com.votewithblock.gateway.model.OperationsMetricsHistory;
import com.votewithblock.gateway.repository.OperationsMetricsHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
@RequiredArgsConstructor
public class OperationsMetricsService {

    private final OperationsMetricsHistoryRepository metricsHistoryRepository;
    private final DashboardMetricsService dashboardMetricsService;
    private final SystemHealthService systemHealthService;
    private final ElectionIncidentService incidentService;

    private final AtomicLong securityMetricsRecorded = new AtomicLong(0);
    private final AtomicLong electionMetricsRecorded = new AtomicLong(0);
    private final AtomicLong incidentMetricsRecorded = new AtomicLong(0);
    private final AtomicLong healthMetricsRecorded = new AtomicLong(0);

    @Transactional
    public void recordMetric(String metricType, String metricName, Long metricValue, UUID electionId) {
        OperationsMetricsHistory history = OperationsMetricsHistory.builder()
                .metricType(metricType)
                .metricName(metricName)
                .metricValue(metricValue)
                .electionId(electionId)
                .recordedAt(OffsetDateTime.now())
                .build();
        metricsHistoryRepository.save(history);
    }

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void persistMetrics() {
        try {
            var biometricMetrics = dashboardMetricsService.getBiometricMetrics();
            recordMetric("SECURITY", "successful_verifications", biometricMetrics.getSuccessfulVerifications(), null);
            recordMetric("SECURITY", "failed_verifications", biometricMetrics.getFailedVerifications(), null);
            recordMetric("SECURITY", "lockouts", biometricMetrics.getLockouts(), null);
            securityMetricsRecorded.incrementAndGet();

            var securityMetrics = dashboardMetricsService.getSecurityMetrics();
            recordMetric("SECURITY", "login_failures", securityMetrics.getLoginFailures(), null);
            recordMetric("SECURITY", "admin_login_failures", securityMetrics.getAdminLoginFailures(), null);
            recordMetric("SECURITY", "rate_limit_events", securityMetrics.getRateLimitEvents(), null);
            recordMetric("SECURITY", "suspicious_activities", securityMetrics.getSuspiciousActivities(), null);

            var healthMetrics = systemHealthService.getSystemHealth();
            long healthValue = "HEALTHY".equals(healthMetrics.getOverallStatus()) ? 1L : 0L;
            recordMetric("HEALTH", "overall_status", healthValue, null);
            healthMetricsRecorded.incrementAndGet();

            log.debug("Metrics persisted: security={}, health={}", 
                securityMetricsRecorded.get(), healthMetricsRecorded.get());
        } catch (Exception e) {
            log.error("Failed to persist metrics", e);
        }
    }

    public List<OperationsMetricsHistory> getMetricsHistory(String metricType, UUID electionId, 
                                                            OffsetDateTime start, OffsetDateTime end) {
        if (electionId != null) {
            return metricsHistoryRepository.findByMetricTypeAndElectionIdAndRecordedAtBetween(
                metricType, electionId, start, end);
        }
        return metricsHistoryRepository.findByMetricTypeAndRecordedAtBetween(metricType, start, end);
    }

    public List<OperationsMetricsHistory> getElectionMetrics(UUID electionId, OffsetDateTime start, OffsetDateTime end) {
        return metricsHistoryRepository.findByElectionIdAndRecordedAtBetween(electionId, start, end);
    }
}
