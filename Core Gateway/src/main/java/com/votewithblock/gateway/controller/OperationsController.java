package com.votewithblock.gateway.controller;

import com.votewithblock.gateway.model.AlertStatus;
import com.votewithblock.gateway.model.IncidentSeverity;
import com.votewithblock.gateway.model.IncidentStatus;
import com.votewithblock.gateway.model.SecurityEventType;
import com.votewithblock.gateway.service.AuditAnalyticsService;
import com.votewithblock.gateway.service.BackupCatalogService;
import com.votewithblock.gateway.service.ElectionIncidentService;
import com.votewithblock.gateway.service.ElectionOperationsService;
import com.votewithblock.gateway.service.OperationsAlertService;
import com.votewithblock.gateway.service.OperationsMetricsService;
import com.votewithblock.gateway.service.OutboxProcessorService;
import com.votewithblock.gateway.service.RecoveryAuditService;
import com.votewithblock.gateway.service.SecurityEventService;
import com.votewithblock.gateway.service.SystemHealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/operations")
@RequiredArgsConstructor
public class OperationsController {

    private final OperationsAlertService alertService;
    private final SecurityEventService securityEventService;
    private final ElectionIncidentService incidentService;
    private final AuditAnalyticsService auditService;
    private final SystemHealthService healthService;
    private final OperationsMetricsService metricsService;
    private final ElectionOperationsService electionOpsService;
    private final RecoveryAuditService recoveryAuditService;
    private final BackupCatalogService backupCatalogService;
    private final OutboxProcessorService outboxProcessorService;

    @GetMapping("/alerts")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ELECTION_ADMIN', 'AUDITOR')")
    public ResponseEntity<?> getAlerts(
            @RequestParam(defaultValue = "ACTIVE") AlertStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(alertService.getAlerts(status, page, size));
    }

    @GetMapping("/alerts/active")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ELECTION_ADMIN', 'AUDITOR')")
    public ResponseEntity<?> getActiveAlerts() {
        return ResponseEntity.ok(alertService.getActiveAlerts());
    }

    @GetMapping("/alerts/stats")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ELECTION_ADMIN', 'AUDITOR')")
    public ResponseEntity<?> getAlertStats() {
        return ResponseEntity.ok(alertService.getAlertStats());
    }

    @PutMapping("/alerts/{id}/acknowledge")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ELECTION_ADMIN')")
    public ResponseEntity<?> acknowledgeAlert(@PathVariable Long id, Principal principal) {
        return ResponseEntity.ok(alertService.acknowledgeAlert(id, principal.getName()));
    }

    @PutMapping("/alerts/{id}/resolve")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ELECTION_ADMIN')")
    public ResponseEntity<?> resolveAlert(@PathVariable Long id, Principal principal) {
        return ResponseEntity.ok(alertService.resolveAlert(id, principal.getName()));
    }

    @GetMapping("/security/events")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ELECTION_ADMIN', 'AUDITOR')")
    public ResponseEntity<?> getSecurityEvents(
            @RequestParam(required = false) SecurityEventType eventType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (eventType != null) {
            return ResponseEntity.ok(securityEventService.getEvents(eventType, page, size));
        }
        return ResponseEntity.ok(securityEventService.getEventsInRange(
            OffsetDateTime.now().minusDays(7), OffsetDateTime.now(), page, size));
    }

    @GetMapping("/security/events/stats")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ELECTION_ADMIN', 'AUDITOR')")
    public ResponseEntity<?> getSecurityEventStats() {
        return ResponseEntity.ok(securityEventService.getEventStats());
    }

    @GetMapping("/security/metrics")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ELECTION_ADMIN', 'AUDITOR')")
    public ResponseEntity<?> getSecurityMetrics() {
        return ResponseEntity.ok(Map.of(
            "events24h", securityEventService.getEventStats().get("total24h"),
            "events7d", securityEventService.getEventStats().get("total7d")
        ));
    }

    @GetMapping("/audit/logs")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ELECTION_ADMIN', 'AUDITOR')")
    public ResponseEntity<?> getAuditLogs(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String adminUsernameHash,
            @RequestParam(required = false) String targetEntityType,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime end,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(auditService.getAuditLogs(
            action, adminUsernameHash, targetEntityType, outcome, start, end, page, size));
    }

    @GetMapping("/audit/logs/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ELECTION_ADMIN', 'AUDITOR')")
    public ResponseEntity<?> getAuditLog(@PathVariable Long id) {
        return ResponseEntity.ok(auditService.getAuditLog(id));
    }

    @GetMapping("/audit/stats")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ELECTION_ADMIN', 'AUDITOR')")
    public ResponseEntity<?> getAuditStats() {
        return ResponseEntity.ok(auditService.getAuditStats());
    }

    @GetMapping("/audit/breakdown/actions")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ELECTION_ADMIN', 'AUDITOR')")
    public ResponseEntity<?> getActionBreakdown() {
        return ResponseEntity.ok(auditService.getActionBreakdown());
    }

    @GetMapping("/audit/breakdown/outcomes")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ELECTION_ADMIN', 'AUDITOR')")
    public ResponseEntity<?> getOutcomeBreakdown() {
        return ResponseEntity.ok(auditService.getOutcomeBreakdown());
    }

    @PostMapping("/audit/export")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> exportAuditLogs(
            Principal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime end) {
        return ResponseEntity.ok(auditService.exportAuditLogs(principal.getName(), start, end));
    }

    @GetMapping("/health")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ELECTION_ADMIN', 'AUDITOR')")
    public ResponseEntity<?> getSystemHealth() {
        return ResponseEntity.ok(healthService.getSystemHealth());
    }

    @GetMapping("/health/{service}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ELECTION_ADMIN', 'AUDITOR')")
    public ResponseEntity<?> getServiceHealth(@PathVariable String service) {
        var health = healthService.getSystemHealth();
        return switch (service.toLowerCase()) {
            case "gateway" -> ResponseEntity.ok(health.getGatewayHealth());
            case "ai", "ai-service" -> ResponseEntity.ok(health.getAiServiceHealth());
            case "database", "db" -> ResponseEntity.ok(health.getDatabaseHealth());
            case "fabric", "blockchain" -> ResponseEntity.ok(health.getFabricHealth());
            default -> ResponseEntity.badRequest().body(Map.of("error", "Unknown service: " + service));
        };
    }

    @GetMapping("/elections/{id}/status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ELECTION_ADMIN', 'AUDITOR')")
    public ResponseEntity<?> getElectionStatus(@PathVariable UUID id) {
        return ResponseEntity.ok(electionOpsService.getElectionStatus(id));
    }

    @GetMapping("/elections/{id}/timeline")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ELECTION_ADMIN', 'AUDITOR')")
    public ResponseEntity<?> getElectionTimeline(@PathVariable UUID id) {
        return ResponseEntity.ok(electionOpsService.getElectionTimeline(id));
    }

    @GetMapping("/elections/{id}/health")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ELECTION_ADMIN', 'AUDITOR')")
    public ResponseEntity<?> getElectionHealth(@PathVariable UUID id) {
        return ResponseEntity.ok(Map.of(
            "electionId", id,
            "healthScore", electionOpsService.calculateHealthScore(id),
            "healthStatus", electionOpsService.getHealthStatus(id)
        ));
    }

    @GetMapping("/elections/{id}/velocity")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ELECTION_ADMIN', 'AUDITOR')")
    public ResponseEntity<?> getElectionVelocity(@PathVariable UUID id) {
        return ResponseEntity.ok(electionOpsService.getVotingVelocity(id));
    }

    @GetMapping("/elections/{id}/warnings")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ELECTION_ADMIN', 'AUDITOR')")
    public ResponseEntity<?> getElectionWarnings(@PathVariable UUID id) {
        return ResponseEntity.ok(electionOpsService.getOperationalWarnings(id));
    }

    @GetMapping("/incidents/stats")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ELECTION_ADMIN', 'AUDITOR')")
    public ResponseEntity<?> getIncidentStats() {
        return ResponseEntity.ok(incidentService.getIncidentStats());
    }

    @GetMapping("/metrics")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ELECTION_ADMIN', 'AUDITOR')")
    public ResponseEntity<?> getMetrics(
            @RequestParam(required = false) String metricType,
            @RequestParam(required = false) UUID electionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime end) {
        if (start == null) start = OffsetDateTime.now().minusHours(24);
        if (end == null) end = OffsetDateTime.now();
        return ResponseEntity.ok(metricsService.getMetricsHistory(metricType, electionId, start, end));
    }

    @GetMapping("/recovery/logs")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ELECTION_ADMIN', 'AUDITOR')")
    public ResponseEntity<?> getRecoveryLogs(
            @RequestParam(required = false) String actionType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(recoveryAuditService.getAuditLogs(actionType, page, size));
    }

    @GetMapping("/recovery/stats")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ELECTION_ADMIN', 'AUDITOR')")
    public ResponseEntity<?> getRecoveryStats() {
        return ResponseEntity.ok(Map.of(
            "stats", recoveryAuditService.getAuditStats(),
            "actionBreakdown", recoveryAuditService.getActionBreakdown(),
            "componentBreakdown", recoveryAuditService.getComponentBreakdown()
        ));
    }

    @GetMapping("/backup/logs")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ELECTION_ADMIN', 'AUDITOR')")
    public ResponseEntity<?> getBackupLogs(
            @RequestParam(required = false) String backupType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(backupCatalogService.getBackups(backupType, page, size));
    }

    @GetMapping("/backup/stats")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ELECTION_ADMIN', 'AUDITOR')")
    public ResponseEntity<?> getBackupStats() {
        return ResponseEntity.ok(Map.of(
            "stats", backupCatalogService.getBackupStats(),
            "typeBreakdown", backupCatalogService.getBackupTypeBreakdown()
        ));
    }

    @GetMapping("/outbox/stats")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ELECTION_ADMIN', 'AUDITOR')")
    public ResponseEntity<?> getOutboxStats() {
        return ResponseEntity.ok(Map.of(
            "pending", outboxProcessorService.getPendingCount(),
            "failed", outboxProcessorService.getFailedCount()
        ));
    }
}
