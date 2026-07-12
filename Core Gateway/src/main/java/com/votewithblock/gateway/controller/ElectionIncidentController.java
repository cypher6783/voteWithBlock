package com.votewithblock.gateway.controller;

import com.votewithblock.gateway.model.ElectionIncident;
import com.votewithblock.gateway.model.IncidentAssignmentHistory;
import com.votewithblock.gateway.model.IncidentSeverity;
import com.votewithblock.gateway.model.IncidentStatus;
import com.votewithblock.gateway.repository.ElectionIncidentRepository;
import com.votewithblock.gateway.repository.IncidentAssignmentHistoryRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/incidents")
@RequiredArgsConstructor
public class ElectionIncidentController {

    private final ElectionIncidentRepository incidentRepository;
    private final IncidentAssignmentHistoryRepository assignmentHistoryRepository;

    @PostMapping("/election/{electionId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ELECTION_ADMIN')")
    public ResponseEntity<ElectionIncident> createIncident(
            @PathVariable UUID electionId,
            @Valid @RequestBody ElectionIncident incident,
            Principal principal) {
        incident.setElectionId(electionId);
        incident.setCreatedBy(principal.getName());
        incident.setCreatedAt(OffsetDateTime.now());
        incident.setStatus(IncidentStatus.OPEN);
        return ResponseEntity.ok(incidentRepository.save(incident));
    }

    @PutMapping("/{id}/acknowledge")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ELECTION_ADMIN')")
    public ResponseEntity<ElectionIncident> acknowledgeIncident(
            @PathVariable Long id,
            Principal principal) {
        ElectionIncident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Incident not found"));
        incident.setStatus(IncidentStatus.ACKNOWLEDGED);
        incident.setAcknowledgedAt(OffsetDateTime.now());
        return ResponseEntity.ok(incidentRepository.save(incident));
    }

    @PutMapping("/{id}/investigate")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ELECTION_ADMIN')")
    public ResponseEntity<ElectionIncident> investigateIncident(
            @PathVariable Long id,
            Principal principal) {
        ElectionIncident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Incident not found"));
        incident.setStatus(IncidentStatus.INVESTIGATING);
        incident.setInvestigatingAt(OffsetDateTime.now());
        return ResponseEntity.ok(incidentRepository.save(incident));
    }

    @PutMapping("/{id}/resolve")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ELECTION_ADMIN')")
    public ResponseEntity<ElectionIncident> resolveIncident(
            @PathVariable Long id,
            Principal principal) {
        ElectionIncident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Incident not found"));
        incident.setStatus(IncidentStatus.RESOLVED);
        incident.setResolvedAt(OffsetDateTime.now());
        return ResponseEntity.ok(incidentRepository.save(incident));
    }

    @PutMapping("/{id}/assign")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ElectionIncident> assignIncident(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            Principal principal) {
        ElectionIncident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Incident not found"));
        
        String previousAssignee = incident.getAssignedTo();
        String newAssignee = body.get("assignedTo");
        String reason = body.get("reason");
        
        incident.setAssignedTo(newAssignee);
        incident = incidentRepository.save(incident);
        
        IncidentAssignmentHistory history = IncidentAssignmentHistory.builder()
                .incidentId(id)
                .assignedFrom(previousAssignee)
                .assignedTo(newAssignee)
                .assignedBy(principal.getName())
                .assignedAt(OffsetDateTime.now())
                .reason(reason)
                .build();
        assignmentHistoryRepository.save(history);
        
        return ResponseEntity.ok(incident);
    }

    @GetMapping("/election/{electionId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ELECTION_ADMIN', 'AUDITOR')")
    public ResponseEntity<?> getIncidents(@PathVariable UUID electionId) {
        return ResponseEntity.ok(incidentRepository.findByElectionIdOrderByCreatedAtDesc(electionId));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ELECTION_ADMIN', 'AUDITOR')")
    public ResponseEntity<Page<ElectionIncident>> getAllIncidents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(required = false) IncidentSeverity severity) {
        PageRequest pageRequest = PageRequest.of(page, size);
        if (status != null) {
            return ResponseEntity.ok(incidentRepository.findByStatus(status, pageRequest));
        }
        if (severity != null) {
            return ResponseEntity.ok(incidentRepository.findBySeverity(severity, pageRequest));
        }
        return ResponseEntity.ok(incidentRepository.findAll(pageRequest));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ELECTION_ADMIN', 'AUDITOR')")
    public ResponseEntity<ElectionIncident> getIncident(@PathVariable Long id) {
        return ResponseEntity.ok(incidentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Incident not found")));
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ELECTION_ADMIN', 'AUDITOR')")
    public ResponseEntity<?> getAssignmentHistory(@PathVariable Long id) {
        return ResponseEntity.ok(assignmentHistoryRepository.findByIncidentIdOrderByAssignedAtDesc(id));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ELECTION_ADMIN', 'AUDITOR')")
    public ResponseEntity<?> getIncidentStats() {
        return ResponseEntity.ok(Map.of(
            "byStatus", incidentRepository.countByStatusGrouped(),
            "bySeverity", incidentRepository.countBySeverityGrouped()
        ));
    }
}
