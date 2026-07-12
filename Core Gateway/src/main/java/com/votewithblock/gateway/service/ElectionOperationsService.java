package com.votewithblock.gateway.service;

import com.votewithblock.gateway.model.Election;
import com.votewithblock.gateway.model.ElectionIncident;
import com.votewithblock.gateway.model.IncidentSeverity;
import com.votewithblock.gateway.model.IncidentStatus;
import com.votewithblock.gateway.model.OperationsAlert;
import com.votewithblock.gateway.model.AlertSeverity;
import com.votewithblock.gateway.repository.ElectionIncidentRepository;
import com.votewithblock.gateway.repository.OperationsAlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ElectionOperationsService {

    private final ElectionService electionService;
    private final ElectionIncidentRepository incidentRepository;
    private final OperationsAlertRepository alertRepository;
    private final ElectionMetricsService electionMetricsService;

    public Map<String, Object> getElectionStatus(UUID electionId) {
        Election election = electionService.getElection(electionId);
        long openIncidents = incidentRepository.countByElectionIdAndStatus(electionId, IncidentStatus.OPEN);
        long criticalIncidents = incidentRepository.countBySeverity(IncidentSeverity.CRITICAL);

        return Map.of(
            "electionId", election.getId(),
            "title", election.getTitle(),
            "status", election.getStatus(),
            "startDate", election.getStartDate(),
            "endDate", election.getEndDate(),
            "openIncidents", openIncidents,
            "criticalIncidents", criticalIncidents,
            "healthScore", calculateHealthScore(electionId)
        );
    }

    public Map<String, Object> getElectionTimeline(UUID electionId) {
        Election election = electionService.getElection(electionId);
        Instant now = Instant.now();

        Long secondsUntilStart = null;
        Long secondsUntilEnd = null;
        Double progressPercentage = null;

        if ("SCHEDULED".equals(election.getStatus())) {
            secondsUntilStart = ChronoUnit.SECONDS.between(now, election.getStartDate());
        } else if ("OPEN".equals(election.getStatus())) {
            secondsUntilEnd = ChronoUnit.SECONDS.between(now, election.getEndDate());
            long totalDuration = ChronoUnit.SECONDS.between(election.getStartDate(), election.getEndDate());
            long elapsed = totalDuration - secondsUntilEnd;
            progressPercentage = totalDuration > 0 ? (double) elapsed / totalDuration * 100 : 0.0;
        }

        return Map.of(
            "electionId", electionId,
            "status", election.getStatus(),
            "startDate", election.getStartDate(),
            "endDate", election.getEndDate(),
            "secondsUntilStart", secondsUntilStart,
            "secondsUntilEnd", secondsUntilEnd,
            "progressPercentage", progressPercentage
        );
    }

    public int calculateHealthScore(UUID electionId) {
        long openIncidents = incidentRepository.countByElectionIdAndStatus(electionId, IncidentStatus.OPEN);
        long criticalIncidents = incidentRepository.countBySeverity(IncidentSeverity.CRITICAL);
        long highIncidents = incidentRepository.countBySeverity(IncidentSeverity.HIGH);
        long activeAlerts = alertRepository.countByStatus(com.votewithblock.gateway.model.AlertStatus.ACTIVE);

        int score = 100;
        score -= (int)(criticalIncidents * 20);
        score -= (int)(highIncidents * 10);
        score -= (int)(activeAlerts * 5);
        return Math.max(0, Math.min(100, score));
    }

    public String getHealthStatus(UUID electionId) {
        int score = calculateHealthScore(electionId);
        if (score >= 80) return "HEALTHY";
        if (score >= 50) return "DEGRADED";
        return "CRITICAL";
    }

    public Map<String, Object> getVotingVelocity(UUID electionId) {
        var metrics = electionMetricsService.getPublicMetrics(
            electionService.getElection(electionId).getElectionCode());

        return Map.of(
            "electionId", electionId,
            "totalVotesCast", metrics.getTotalVotesCast(),
            "turnoutPercentage", metrics.getTurnoutPercentage(),
            "registeredVoters", metrics.getRegisteredVoters(),
            "countdownSeconds", metrics.getCountdownSeconds()
        );
    }

    public List<Map<String, Object>> getOperationalWarnings(UUID electionId) {
        List<Map<String, Object>> warnings = new ArrayList<>();
        Election election = electionService.getElection(electionId);

        long openIncidents = incidentRepository.countByElectionIdAndStatus(electionId, IncidentStatus.OPEN);
        if (openIncidents > 5) {
            warnings.add(Map.of(
                "type", "HIGH_INCIDENT_COUNT",
                "severity", "HIGH",
                "message", openIncidents + " open incidents require attention"
            ));
        }

        long criticalIncidents = incidentRepository.countBySeverity(IncidentSeverity.CRITICAL);
        if (criticalIncidents > 0) {
            warnings.add(Map.of(
                "type", "CRITICAL_INCIDENT",
                "severity", "CRITICAL",
                "message", criticalIncidents + " critical incidents active"
            ));
        }

        long activeAlerts = alertRepository.countByStatus(com.votewithblock.gateway.model.AlertStatus.ACTIVE);
        if (activeAlerts > 3) {
            warnings.add(Map.of(
                "type", "ALERT_SPIKE",
                "severity", "MEDIUM",
                "message", activeAlerts + " active alerts"
            ));
        }

        if ("OPEN".equals(election.getStatus())) {
            long hoursUntilClose = ChronoUnit.HOURS.between(Instant.now(), election.getEndDate());
            if (hoursUntilClose <= 2 && hoursUntilClose > 0) {
                warnings.add(Map.of(
                    "type", "ELECTION_ENDING_SOON",
                    "severity", "MEDIUM",
                    "message", "Election closes in " + hoursUntilClose + " hours"
                ));
            }
        }

        return warnings;
    }
}
