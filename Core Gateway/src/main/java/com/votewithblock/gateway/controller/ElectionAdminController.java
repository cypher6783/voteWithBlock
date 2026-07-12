package com.votewithblock.gateway.controller;

import com.votewithblock.gateway.model.Election;
import com.votewithblock.gateway.model.ElectionStatus;
import com.votewithblock.gateway.service.ElectionResultsCacheService;
import com.votewithblock.gateway.service.ElectionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/elections")
@RequiredArgsConstructor
public class ElectionAdminController {

    private final ElectionService electionService;
    private final ElectionResultsCacheService electionResultsCacheService;

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ELECTION_ADMIN')")
    public ResponseEntity<Election> createElection(@Valid @RequestBody Election election, Principal principal, HttpServletRequest request) {
        Election created = electionService.createElection(election, principal.getName(), clientIp(request));
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ELECTION_ADMIN')")
    public ResponseEntity<Election> updateElection(@PathVariable UUID id, @Valid @RequestBody Election updates, Principal principal, HttpServletRequest request) {
        Election updated = electionService.updateElection(id, updates, principal.getName(), clientIp(request));
        return ResponseEntity.ok(updated);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ELECTION_ADMIN')")
    public ResponseEntity<Election> updateElectionStatus(@PathVariable UUID id, @RequestParam ElectionStatus status, Principal principal, HttpServletRequest request) {
        Election updated = electionService.updateElectionStatus(id, status, principal.getName(), clientIp(request));
        return ResponseEntity.ok(updated);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ELECTION_ADMIN', 'AUDITOR')")
    public ResponseEntity<List<Election>> getAllElections() {
        return ResponseEntity.ok(electionService.getAllElections());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ELECTION_ADMIN', 'AUDITOR')")
    public ResponseEntity<Election> getElection(@PathVariable UUID id) {
        return ResponseEntity.ok(electionService.getElection(id));
    }

    @GetMapping("/{id}/results")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ELECTION_ADMIN', 'AUDITOR')")
    public ResponseEntity<String> getElectionResults(@PathVariable UUID id) {
        Election election = electionService.getElection(id);
        String results = electionResultsCacheService.getElectionResults(election.getElectionCode());
        return ResponseEntity.ok(results);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
