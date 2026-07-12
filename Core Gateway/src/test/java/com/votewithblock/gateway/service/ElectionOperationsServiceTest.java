package com.votewithblock.gateway.service;

import com.votewithblock.gateway.model.Election;
import com.votewithblock.gateway.model.ElectionStatus;
import com.votewithblock.gateway.model.IncidentSeverity;
import com.votewithblock.gateway.model.IncidentStatus;
import com.votewithblock.gateway.model.AlertStatus;
import com.votewithblock.gateway.repository.ElectionIncidentRepository;
import com.votewithblock.gateway.repository.OperationsAlertRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ElectionOperationsServiceTest {

    private ElectionIncidentRepository incidentRepository;
    private OperationsAlertRepository alertRepository;
    private ElectionOperationsService electionOpsService;

    @BeforeEach
    void setUp() {
        incidentRepository = mock(ElectionIncidentRepository.class);
        alertRepository = mock(OperationsAlertRepository.class);
        electionOpsService = new ElectionOperationsService(
                null, incidentRepository, alertRepository, null);
    }

    @Test
    void calculateHealthScore_shouldReturn100WhenNoIssues() {
        when(incidentRepository.countByElectionIdAndStatus(any(UUID.class), eq(IncidentStatus.OPEN))).thenReturn(0L);
        when(incidentRepository.countBySeverity(IncidentSeverity.CRITICAL)).thenReturn(0L);
        when(incidentRepository.countBySeverity(IncidentSeverity.HIGH)).thenReturn(0L);
        when(alertRepository.countByStatus(AlertStatus.ACTIVE)).thenReturn(0L);

        int score = electionOpsService.calculateHealthScore(UUID.randomUUID());

        assertEquals(100, score);
    }

    @Test
    void calculateHealthScore_shouldDeductForIncidents() {
        when(incidentRepository.countByElectionIdAndStatus(any(UUID.class), eq(IncidentStatus.OPEN))).thenReturn(3L);
        when(incidentRepository.countBySeverity(IncidentSeverity.CRITICAL)).thenReturn(1L);
        when(incidentRepository.countBySeverity(IncidentSeverity.HIGH)).thenReturn(2L);
        when(alertRepository.countByStatus(AlertStatus.ACTIVE)).thenReturn(0L);

        int score = electionOpsService.calculateHealthScore(UUID.randomUUID());

        assertEquals(60, score);
    }

    @Test
    void calculateHealthScore_shouldNotGoBelowZero() {
        when(incidentRepository.countByElectionIdAndStatus(any(UUID.class), eq(IncidentStatus.OPEN))).thenReturn(10L);
        when(incidentRepository.countBySeverity(IncidentSeverity.CRITICAL)).thenReturn(10L);
        when(incidentRepository.countBySeverity(IncidentSeverity.HIGH)).thenReturn(10L);
        when(alertRepository.countByStatus(AlertStatus.ACTIVE)).thenReturn(10L);

        int score = electionOpsService.calculateHealthScore(UUID.randomUUID());

        assertEquals(0, score);
    }

    @Test
    void calculateHealthScore_shouldCapAt100() {
        when(incidentRepository.countByElectionIdAndStatus(any(UUID.class), eq(IncidentStatus.OPEN))).thenReturn(0L);
        when(incidentRepository.countBySeverity(IncidentSeverity.CRITICAL)).thenReturn(0L);
        when(incidentRepository.countBySeverity(IncidentSeverity.HIGH)).thenReturn(0L);
        when(alertRepository.countByStatus(AlertStatus.ACTIVE)).thenReturn(0L);

        int score = electionOpsService.calculateHealthScore(UUID.randomUUID());

        assertTrue(score <= 100);
    }

    @Test
    void getHealthStatus_shouldReturnHealthyWhenScoreHigh() {
        when(incidentRepository.countByElectionIdAndStatus(any(UUID.class), eq(IncidentStatus.OPEN))).thenReturn(0L);
        when(incidentRepository.countBySeverity(IncidentSeverity.CRITICAL)).thenReturn(0L);
        when(incidentRepository.countBySeverity(IncidentSeverity.HIGH)).thenReturn(0L);
        when(alertRepository.countByStatus(AlertStatus.ACTIVE)).thenReturn(0L);

        String status = electionOpsService.getHealthStatus(UUID.randomUUID());

        assertEquals("HEALTHY", status);
    }

    @Test
    void getHealthStatus_shouldReturnCriticalWhenScoreLow() {
        when(incidentRepository.countByElectionIdAndStatus(any(UUID.class), eq(IncidentStatus.OPEN))).thenReturn(10L);
        when(incidentRepository.countBySeverity(IncidentSeverity.CRITICAL)).thenReturn(5L);
        when(incidentRepository.countBySeverity(IncidentSeverity.HIGH)).thenReturn(5L);
        when(alertRepository.countByStatus(AlertStatus.ACTIVE)).thenReturn(5L);

        String status = electionOpsService.getHealthStatus(UUID.randomUUID());

        assertEquals("CRITICAL", status);
    }

    @Test
    void getHealthStatus_shouldReturnDegradedWhenScoreMedium() {
        when(incidentRepository.countByElectionIdAndStatus(any(UUID.class), eq(IncidentStatus.OPEN))).thenReturn(3L);
        when(incidentRepository.countBySeverity(IncidentSeverity.CRITICAL)).thenReturn(1L);
        when(incidentRepository.countBySeverity(IncidentSeverity.HIGH)).thenReturn(1L);
        when(alertRepository.countByStatus(AlertStatus.ACTIVE)).thenReturn(2L);

        String status = electionOpsService.getHealthStatus(UUID.randomUUID());

        assertEquals("DEGRADED", status);
    }
}
