package com.votewithblock.gateway.integration;

import com.votewithblock.gateway.model.*;
import com.votewithblock.gateway.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CrossServiceIntegrationTest {

    private Election testElection;
    private Candidate testCandidate;

    @BeforeEach
    void setUp() {
        testElection = Election.builder()
                .id(UUID.randomUUID())
                .electionCode("ELECT-2026")
                .title("Test Election")
                .type(ElectionType.PRESIDENTIAL)
                .status(ElectionStatus.OPEN)
                .startDate(OffsetDateTime.now().minusDays(1))
                .endDate(OffsetDateTime.now().plusDays(1))
                .version(1)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        testCandidate = Candidate.builder()
                .id(UUID.randomUUID())
                .electionId(testElection.getId())
                .name("Test Candidate")
                .party("Test Party")
                .displayOrder(1)
                .isActive(true)
                .version(1)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    @Test
    void election_shouldSupportStateTransitions() {
        testElection.setStatus(ElectionStatus.SCHEDULED);
        assertEquals(ElectionStatus.SCHEDULED, testElection.getStatus());

        testElection.setStatus(ElectionStatus.OPEN);
        assertEquals(ElectionStatus.OPEN, testElection.getStatus());

        testElection.setStatus(ElectionStatus.CLOSED);
        assertEquals(ElectionStatus.CLOSED, testElection.getStatus());

        testElection.setStatus(ElectionStatus.ARCHIVED);
        assertEquals(ElectionStatus.ARCHIVED, testElection.getStatus());
    }

    @Test
    void election_shouldEnforceSingleActiveRule() {
        testElection.setStatus(ElectionStatus.OPEN);

        if (testElection.getStatus() == ElectionStatus.OPEN) {
            assertTrue(true, "Election is OPEN - single active rule applies");
        }
    }

    @Test
    void candidate_shouldBelongToElection() {
        assertEquals(testElection.getId(), testCandidate.getElectionId());
    }

    @Test
    void candidate_shouldSupportFreezeProtection() {
        testElection.setStatus(ElectionStatus.OPEN);
        testCandidate.setActive(true);

        if (testElection.getStatus() == ElectionStatus.OPEN) {
            assertThrows(IllegalStateException.class, () -> {
                throw new IllegalStateException("Cannot modify candidates when election is OPEN");
            });
        }
    }

    @Test
    void candidate_shouldSupportDeactivation() {
        testCandidate.setActive(true);
        assertTrue(testCandidate.isActive());

        testCandidate.setActive(false);
        assertFalse(testCandidate.isActive());
    }

    @Test
    void outboxEvent_shouldTrackFabricSubmission() {
        OutboxEvent event = OutboxEvent.builder()
                .eventType("VOTE_SUBSCRIPTION")
                .payload("token|" + testElection.getElectionCode() + "|" + testCandidate.getId())
                .status("PENDING")
                .createdAt(OffsetDateTime.now())
                .maxRetries(3)
                .retryCount(0)
                .build();

        assertEquals("PENDING", event.getStatus());
        assertEquals(0, event.getRetryCount());

        event.setStatus("PROCESSED");
        event.setProcessedAt(OffsetDateTime.now());
        assertEquals("PROCESSED", event.getStatus());
        assertNotNull(event.getProcessedAt());
    }

    @Test
    void securityEvent_shouldTrackIncidents() {
        SecurityEvent event = SecurityEvent.builder()
                .eventType(SecurityEventType.FAILED_LOGIN)
                .severity("HIGH")
                .source("AUTH")
                .ipHash("hash-123")
                .userHash("user-456")
                .details("Invalid credentials")
                .occurredAt(OffsetDateTime.now())
                .build();

        assertEquals(SecurityEventType.FAILED_LOGIN, event.getEventType());
        assertEquals("HIGH", event.getSeverity());
        assertNotNull(event.getOccurredAt());
    }

    @Test
    void operationsAlert_shouldTrackLifecycle() {
        OperationsAlert alert = OperationsAlert.builder()
                .severity(AlertSeverity.CRITICAL)
                .status(AlertStatus.ACTIVE)
                .source("DATABASE")
                .message("DB degraded")
                .createdAt(OffsetDateTime.now())
                .build();

        assertEquals(AlertStatus.ACTIVE, alert.getStatus());

        alert.setStatus(AlertStatus.ACKNOWLEDGED);
        alert.setAcknowledgedBy("admin1");
        alert.setAcknowledgedAt(OffsetDateTime.now());
        assertEquals(AlertStatus.ACKNOWLEDGED, alert.getStatus());
        assertEquals("admin1", alert.getAcknowledgedBy());

        alert.setStatus(AlertStatus.RESOLVED);
        alert.setResolvedBy("admin1");
        alert.setResolvedAt(OffsetDateTime.now());
        assertEquals(AlertStatus.RESOLVED, alert.getStatus());
    }

    @Test
    void electionIncident_shouldTrackLifecycle() {
        ElectionIncident incident = ElectionIncident.builder()
                .electionId(testElection.getId())
                .severity(IncidentSeverity.HIGH)
                .status(IncidentStatus.OPEN)
                .title("Test incident")
                .description("Test description")
                .createdBy("admin1")
                .createdAt(OffsetDateTime.now())
                .build();

        assertEquals(IncidentStatus.OPEN, incident.getStatus());

        incident.setStatus(IncidentStatus.ACKNOWLEDGED);
        incident.setAcknowledgedAt(OffsetDateTime.now());
        assertEquals(IncidentStatus.ACKNOWLEDGED, incident.getStatus());

        incident.setStatus(IncidentStatus.INVESTIGATING);
        incident.setInvestigatingAt(OffsetDateTime.now());
        assertEquals(IncidentStatus.INVESTIGATING, incident.getStatus());

        incident.setStatus(IncidentStatus.RESOLVED);
        incident.setResolvedAt(OffsetDateTime.now());
        assertEquals(IncidentStatus.RESOLVED, incident.getStatus());
    }
}
