package com.votewithblock.gateway.service;

import com.votewithblock.gateway.model.AlertSeverity;
import com.votewithblock.gateway.model.AlertStatus;
import com.votewithblock.gateway.model.OperationsAlert;
import com.votewithblock.gateway.repository.OperationsAlertRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OperationsAlertServiceTest {

    @Mock
    private OperationsAlertRepository alertRepository;

    @InjectMocks
    private OperationsAlertService alertService;

    private OperationsAlert testAlert;

    @BeforeEach
    void setUp() {
        testAlert = OperationsAlert.builder()
                .id(1L)
                .severity(AlertSeverity.HIGH)
                .status(AlertStatus.ACTIVE)
                .source("FABRIC")
                .message("Fabric disconnected")
                .createdAt(OffsetDateTime.now())
                .build();
    }

    @Test
    void createAlert_shouldSaveAndReturnAlert() {
        when(alertRepository.save(any(OperationsAlert.class))).thenReturn(testAlert);

        OperationsAlert result = alertService.createAlert(
                AlertSeverity.HIGH, "FABRIC", "Fabric disconnected", null);

        assertNotNull(result);
        assertEquals(AlertSeverity.HIGH, result.getSeverity());
        assertEquals(AlertStatus.ACTIVE, result.getStatus());
        assertEquals("FABRIC", result.getSource());
        verify(alertRepository, times(1)).save(any(OperationsAlert.class));
    }

    @Test
    void acknowledgeAlert_shouldUpdateStatusAndReturn() {
        when(alertRepository.findById(1L)).thenReturn(Optional.of(testAlert));
        when(alertRepository.save(any(OperationsAlert.class))).thenReturn(testAlert);

        OperationsAlert result = alertService.acknowledgeAlert(1L, "admin1");

        assertEquals(AlertStatus.ACKNOWLEDGED, result.getStatus());
        assertEquals("admin1", result.getAcknowledgedBy());
        assertNotNull(result.getAcknowledgedAt());
    }

    @Test
    void acknowledgeAlert_shouldThrowWhenNotFound() {
        when(alertRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> alertService.acknowledgeAlert(999L, "admin1"));
    }

    @Test
    void resolveAlert_shouldUpdateStatusAndReturn() {
        when(alertRepository.findById(1L)).thenReturn(Optional.of(testAlert));
        when(alertRepository.save(any(OperationsAlert.class))).thenReturn(testAlert);

        OperationsAlert result = alertService.resolveAlert(1L, "admin1");

        assertEquals(AlertStatus.RESOLVED, result.getStatus());
        assertEquals("admin1", result.getResolvedBy());
        assertNotNull(result.getResolvedAt());
    }

    @Test
    void resolveAlert_shouldThrowWhenNotFound() {
        when(alertRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> alertService.resolveAlert(999L, "admin1"));
    }

    @Test
    void getAlertStats_shouldReturnCounts() {
        when(alertRepository.countByStatus(AlertStatus.ACTIVE)).thenReturn(5L);
        when(alertRepository.countByStatus(AlertStatus.ACKNOWLEDGED)).thenReturn(3L);
        when(alertRepository.countByStatus(AlertStatus.RESOLVED)).thenReturn(10L);
        when(alertRepository.countBySeverity(AlertSeverity.CRITICAL)).thenReturn(2L);
        when(alertRepository.countBySeverity(AlertSeverity.HIGH)).thenReturn(4L);
        when(alertRepository.countBySeverity(AlertSeverity.MEDIUM)).thenReturn(6L);
        when(alertRepository.countBySeverity(AlertSeverity.LOW)).thenReturn(6L);

        var stats = alertService.getAlertStats();

        assertEquals(5L, stats.get("active"));
        assertEquals(3L, stats.get("acknowledged"));
        assertEquals(10L, stats.get("resolved"));
        assertEquals(2L, stats.get("critical"));
        assertEquals(4L, stats.get("high"));
    }

    @Test
    void createCorrelatedAlert_shouldLinkParent() {
        when(alertRepository.findById(1L)).thenReturn(Optional.of(testAlert));
        when(alertRepository.save(any(OperationsAlert.class))).thenReturn(testAlert);

        OperationsAlert result = alertService.createCorrelatedAlert(
                AlertSeverity.CRITICAL, "DATABASE", "DB degraded", 1L, null);

        assertNotNull(result);
        verify(alertRepository, times(1)).save(any(OperationsAlert.class));
    }
}
