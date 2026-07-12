package com.votewithblock.gateway.controller;

import com.votewithblock.gateway.dto.MonitoringDashboardDto;
import com.votewithblock.gateway.model.AlertStatus;
import com.votewithblock.gateway.model.AlertSeverity;
import com.votewithblock.gateway.model.OperationsAlert;
import com.votewithblock.gateway.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OperationsControllerTest {

    private OperationsController controller;

    private static class StubAlertService extends OperationsAlertService {
        StubAlertService() { super(null); }
        @Override public List<OperationsAlert> getActiveAlerts() {
            return List.of(OperationsAlert.builder().id(1L).severity(AlertSeverity.CRITICAL)
                    .status(AlertStatus.ACTIVE).source("DATABASE").message("DB degraded")
                    .createdAt(OffsetDateTime.now()).build());
        }
        @Override public Map<String, Long> getAlertStats() {
            return Map.of("active", 5L, "acknowledged", 3L, "resolved", 10L,
                    "critical", 2L, "high", 4L, "medium", 6L, "low", 6L);
        }
        @Override public OperationsAlert acknowledgeAlert(Long id, String by) {
            return OperationsAlert.builder().id(id).status(AlertStatus.ACKNOWLEDGED).build();
        }
        @Override public OperationsAlert resolveAlert(Long id, String by) {
            return OperationsAlert.builder().id(id).status(AlertStatus.RESOLVED).build();
        }
    }

    private static class StubHealthService extends SystemHealthService {
        StubHealthService() { super(null, null, null); }
        @Override public MonitoringDashboardDto getSystemHealth() {
            return MonitoringDashboardDto.builder().overallStatus("HEALTHY").build();
        }
    }

    private static class StubIncidentService extends ElectionIncidentService {
        StubIncidentService() { super(null); }
        @Override public Map<String, Long> getIncidentStats() {
            return Map.of("open", 5L, "acknowledged", 3L, "investigating", 2L, "resolved", 10L);
        }
    }

    private static class StubSecurityEventService extends SecurityEventService {
        StubSecurityEventService() { super(null); }
        @Override public Map<String, Long> getEventStats() {
            return Map.of("total24h", 50L, "failedLogin", 20L);
        }
    }

    private static class StubAuditService extends AuditAnalyticsService {
        StubAuditService() { super(null, null); }
        @Override public Map<String, Long> getAuditStats() {
            return Map.of("total", 100L, "successCount", 80L, "failCount", 20L);
        }
    }

    private static class StubElectionOpsService extends ElectionOperationsService {
        StubElectionOpsService() { super(null, null, null, null); }
        @Override public int calculateHealthScore(UUID id) { return 85; }
        @Override public String getHealthStatus(UUID id) { return "HEALTHY"; }
        @Override public List<Map<String, Object>> getOperationalWarnings(UUID id) {
            return List.of(Map.of("type", "HIGH_INCIDENT_COUNT", "severity", "HIGH", "message", "10 open incidents"));
        }
    }

    private static class StubRecoveryAuditService extends RecoveryAuditService {
        StubRecoveryAuditService() { super(null); }
        @Override public Map<String, Long> getAuditStats() {
            return Map.of("total24h", 10L, "total7d", 50L);
        }
    }

    private static class StubBackupCatalogService extends BackupCatalogService {
        StubBackupCatalogService() { super(null); }
        @Override public Map<String, Long> getBackupStats() {
            return Map.of("total", 5L, "verified", 4L, "unverified", 1L);
        }
    }

    private static class StubOutboxProcessorService extends OutboxProcessorService {
        StubOutboxProcessorService() { super(null, null); }
        @Override public long getPendingCount() { return 3L; }
        @Override public long getFailedCount() { return 1L; }
    }

    @BeforeEach
    void setUp() {
        controller = new OperationsController(
                new StubAlertService(),
                new StubSecurityEventService(),
                new StubIncidentService(),
                new StubAuditService(),
                new StubHealthService(),
                null,
                new StubElectionOpsService(),
                new StubRecoveryAuditService(),
                new StubBackupCatalogService(),
                new StubOutboxProcessorService());
    }

    private Principal principal() { return () -> "test-admin"; }

    @Test
    void getActiveAlerts_shouldReturnList() {
        var response = controller.getActiveAlerts();
        assertEquals(200, response.getStatusCodeValue());
        @SuppressWarnings("unchecked")
        List<OperationsAlert> body = (List<OperationsAlert>) response.getBody();
        assertEquals(1, body.size());
        assertEquals("DATABASE", body.get(0).getSource());
    }

    @Test
    void getAlertStats_shouldReturnStats() {
        var response = controller.getAlertStats();
        assertEquals(200, response.getStatusCodeValue());
        @SuppressWarnings("unchecked")
        Map<String, Long> body = (Map<String, Long>) response.getBody();
        assertEquals(5L, body.get("active"));
        assertEquals(2L, body.get("critical"));
    }

    @Test
    void acknowledgeAlert_shouldReturnUpdated() {
        var response = controller.acknowledgeAlert(1L, principal());
        assertEquals(200, response.getStatusCodeValue());
        assertEquals(AlertStatus.ACKNOWLEDGED, ((OperationsAlert) response.getBody()).getStatus());
    }

    @Test
    void resolveAlert_shouldReturnUpdated() {
        var response = controller.resolveAlert(1L, principal());
        assertEquals(200, response.getStatusCodeValue());
        assertEquals(AlertStatus.RESOLVED, ((OperationsAlert) response.getBody()).getStatus());
    }

    @Test
    void getSystemHealth_shouldReturnHealth() {
        var response = controller.getSystemHealth();
        assertEquals(200, response.getStatusCodeValue());
        assertEquals("HEALTHY", ((MonitoringDashboardDto) response.getBody()).getOverallStatus());
    }

    @Test
    void getIncidentStats_shouldReturnStats() {
        var response = controller.getIncidentStats();
        assertEquals(200, response.getStatusCodeValue());
        @SuppressWarnings("unchecked")
        Map<String, Long> body = (Map<String, Long>) response.getBody();
        assertEquals(5L, body.get("open"));
    }

    @Test
    void getSecurityEventStats_shouldReturnStats() {
        var response = controller.getSecurityEventStats();
        assertEquals(200, response.getStatusCodeValue());
        @SuppressWarnings("unchecked")
        Map<String, Long> body = (Map<String, Long>) response.getBody();
        assertEquals(50L, body.get("total24h"));
    }

    @Test
    void getAuditStats_shouldReturnStats() {
        var response = controller.getAuditStats();
        assertEquals(200, response.getStatusCodeValue());
        @SuppressWarnings("unchecked")
        Map<String, Long> body = (Map<String, Long>) response.getBody();
        assertEquals(100L, body.get("total"));
    }

    @Test
    void getElectionHealth_shouldReturnHealth() {
        UUID electionId = UUID.randomUUID();
        var response = controller.getElectionHealth(electionId);
        assertEquals(200, response.getStatusCodeValue());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals(85, body.get("healthScore"));
        assertEquals("HEALTHY", body.get("healthStatus"));
    }

    @Test
    void getElectionWarnings_shouldReturnWarnings() {
        UUID electionId = UUID.randomUUID();
        var response = controller.getElectionWarnings(electionId);
        assertEquals(200, response.getStatusCodeValue());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> body = (List<Map<String, Object>>) response.getBody();
        assertEquals(1, body.size());
    }
}
