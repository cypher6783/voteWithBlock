package com.votewithblock.gateway.controller;

import com.votewithblock.gateway.model.ElectionIncident;
import com.votewithblock.gateway.model.IncidentSeverity;
import com.votewithblock.gateway.model.IncidentStatus;
import com.votewithblock.gateway.repository.ElectionIncidentRepository;
import com.votewithblock.gateway.repository.IncidentAssignmentHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ElectionIncidentControllerTest {

    private ElectionIncidentRepository incidentRepository;
    private IncidentAssignmentHistoryRepository assignmentHistoryRepository;
    private ElectionIncidentController controller;
    private final Principal principal = () -> "test-admin";

    @BeforeEach
    void setUp() {
        incidentRepository = mock(ElectionIncidentRepository.class);
        assignmentHistoryRepository = mock(IncidentAssignmentHistoryRepository.class);
        controller = new ElectionIncidentController(incidentRepository, assignmentHistoryRepository);
    }

    private ElectionIncident buildIncident() {
        return ElectionIncident.builder()
                .id(1L)
                .electionId(UUID.randomUUID())
                .severity(IncidentSeverity.HIGH)
                .status(IncidentStatus.OPEN)
                .title("Test Incident")
                .description("Test description")
                .createdBy("admin1")
                .createdAt(OffsetDateTime.now())
                .build();
    }

    @Test
    void createIncident_shouldSetStatusAndSave() {
        ElectionIncident incident = buildIncident();
        when(incidentRepository.save(any(ElectionIncident.class))).thenReturn(incident);

        var response = controller.createIncident(incident.getElectionId(), incident, principal);

        assertEquals(200, response.getStatusCodeValue());
        verify(incidentRepository).save(any(ElectionIncident.class));
    }

    @Test
    void acknowledgeIncident_shouldUpdateStatus() {
        ElectionIncident incident = buildIncident();
        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));
        when(incidentRepository.save(any(ElectionIncident.class))).thenReturn(incident);

        var response = controller.acknowledgeIncident(1L, principal);

        assertEquals(200, response.getStatusCodeValue());
        verify(incidentRepository).save(any(ElectionIncident.class));
    }

    @Test
    void acknowledgeIncident_shouldThrowWhenNotFound() {
        when(incidentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> controller.acknowledgeIncident(999L, principal));
    }

    @Test
    void investigateIncident_shouldUpdateStatus() {
        ElectionIncident incident = buildIncident();
        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));
        when(incidentRepository.save(any(ElectionIncident.class))).thenReturn(incident);

        var response = controller.investigateIncident(1L, principal);

        assertEquals(200, response.getStatusCodeValue());
    }

    @Test
    void resolveIncident_shouldUpdateStatus() {
        ElectionIncident incident = buildIncident();
        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));
        when(incidentRepository.save(any(ElectionIncident.class))).thenReturn(incident);

        var response = controller.resolveIncident(1L, principal);

        assertEquals(200, response.getStatusCodeValue());
    }

    @Test
    void getIncident_shouldReturnIncident() {
        ElectionIncident incident = buildIncident();
        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));

        var response = controller.getIncident(1L);

        assertEquals(200, response.getStatusCodeValue());
        assertEquals("Test Incident", response.getBody().getTitle());
    }

    @Test
    void getIncident_shouldThrowWhenNotFound() {
        when(incidentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> controller.getIncident(999L));
    }

    @Test
    void assignIncident_shouldSaveHistory() {
        ElectionIncident incident = buildIncident();
        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));
        when(incidentRepository.save(any(ElectionIncident.class))).thenReturn(incident);

        var body = java.util.Map.of("assignedTo", "new-admin", "reason", "Reassignment");
        var response = controller.assignIncident(1L, body, principal);

        assertEquals(200, response.getStatusCodeValue());
        verify(incidentRepository).save(any(ElectionIncident.class));
        verify(assignmentHistoryRepository).save(any());
    }
}
