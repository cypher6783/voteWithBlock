package com.votewithblock.gateway.service;

import com.votewithblock.gateway.model.ElectionIncident;
import com.votewithblock.gateway.model.IncidentSeverity;
import com.votewithblock.gateway.model.IncidentStatus;
import com.votewithblock.gateway.repository.ElectionIncidentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ElectionIncidentService {

    private final ElectionIncidentRepository incidentRepository;

    public Map<String, Long> getIncidentStats() {
        return Map.of(
            "open", incidentRepository.countByStatus(IncidentStatus.OPEN),
            "acknowledged", incidentRepository.countByStatus(IncidentStatus.ACKNOWLEDGED),
            "investigating", incidentRepository.countByStatus(IncidentStatus.INVESTIGATING),
            "resolved", incidentRepository.countByStatus(IncidentStatus.RESOLVED),
            "critical", incidentRepository.countBySeverity(IncidentSeverity.CRITICAL),
            "high", incidentRepository.countBySeverity(IncidentSeverity.HIGH),
            "medium", incidentRepository.countBySeverity(IncidentSeverity.MEDIUM),
            "low", incidentRepository.countBySeverity(IncidentSeverity.LOW)
        );
    }

    public long getOpenIncidentCount(UUID electionId) {
        return incidentRepository.countByElectionIdAndStatus(electionId, IncidentStatus.OPEN);
    }

    public Page<ElectionIncident> getIncidentsByStatus(IncidentStatus status, int page, int size) {
        return incidentRepository.findByStatus(status, PageRequest.of(page, size));
    }

    public Page<ElectionIncident> getIncidentsInRange(OffsetDateTime start, OffsetDateTime end, int page, int size) {
        return incidentRepository.findByCreatedAtBetween(start, end, PageRequest.of(page, size));
    }
}
