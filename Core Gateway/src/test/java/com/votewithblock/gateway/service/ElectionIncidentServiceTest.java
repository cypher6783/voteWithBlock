package com.votewithblock.gateway.service;

import com.votewithblock.gateway.model.IncidentSeverity;
import com.votewithblock.gateway.model.IncidentStatus;
import com.votewithblock.gateway.repository.ElectionIncidentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ElectionIncidentServiceTest {

    @Mock
    private ElectionIncidentRepository incidentRepository;

    @InjectMocks
    private ElectionIncidentService incidentService;

    @Test
    void getIncidentStats_shouldReturnCountsByStatusAndSeverity() {
        when(incidentRepository.countByStatus(IncidentStatus.OPEN)).thenReturn(5L);
        when(incidentRepository.countByStatus(IncidentStatus.ACKNOWLEDGED)).thenReturn(3L);
        when(incidentRepository.countByStatus(IncidentStatus.INVESTIGATING)).thenReturn(2L);
        when(incidentRepository.countByStatus(IncidentStatus.RESOLVED)).thenReturn(10L);
        when(incidentRepository.countBySeverity(IncidentSeverity.CRITICAL)).thenReturn(1L);
        when(incidentRepository.countBySeverity(IncidentSeverity.HIGH)).thenReturn(3L);
        when(incidentRepository.countBySeverity(IncidentSeverity.MEDIUM)).thenReturn(5L);
        when(incidentRepository.countBySeverity(IncidentSeverity.LOW)).thenReturn(6L);

        var stats = incidentService.getIncidentStats();

        assertEquals(5L, stats.get("open"));
        assertEquals(3L, stats.get("acknowledged"));
        assertEquals(2L, stats.get("investigating"));
        assertEquals(10L, stats.get("resolved"));
        assertEquals(1L, stats.get("critical"));
        assertEquals(3L, stats.get("high"));
    }

    @Test
    void getOpenIncidentCount_shouldReturnCountForElection() {
        UUID electionId = UUID.randomUUID();
        when(incidentRepository.countByElectionIdAndStatus(electionId, IncidentStatus.OPEN)).thenReturn(3L);

        long count = incidentService.getOpenIncidentCount(electionId);

        assertEquals(3L, count);
        verify(incidentRepository).countByElectionIdAndStatus(electionId, IncidentStatus.OPEN);
    }
}
