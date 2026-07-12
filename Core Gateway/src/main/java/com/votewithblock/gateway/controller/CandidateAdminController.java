package com.votewithblock.gateway.controller;

import com.votewithblock.gateway.model.Candidate;
import com.votewithblock.gateway.service.CandidateService;
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
@RequestMapping("/api/admin/candidates")
@RequiredArgsConstructor
public class CandidateAdminController {

    private final CandidateService candidateService;

    @PostMapping("/election/{electionId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ELECTION_ADMIN')")
    public ResponseEntity<Candidate> createCandidate(@PathVariable UUID electionId, @Valid @RequestBody Candidate candidate, Principal principal, HttpServletRequest request) {
        Candidate created = candidateService.createCandidate(electionId, candidate, principal.getName(), clientIp(request));
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ELECTION_ADMIN')")
    public ResponseEntity<Candidate> updateCandidate(@PathVariable UUID id, @Valid @RequestBody Candidate updates, Principal principal, HttpServletRequest request) {
        Candidate updated = candidateService.updateCandidate(id, updates, principal.getName(), clientIp(request));
        return ResponseEntity.ok(updated);
    }

    @PutMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ELECTION_ADMIN')")
    public ResponseEntity<Candidate> deactivateCandidate(@PathVariable UUID id, Principal principal, HttpServletRequest request) {
        Candidate deactivated = candidateService.deactivateCandidate(id, principal.getName(), clientIp(request));
        return ResponseEntity.ok(deactivated);
    }

    @GetMapping("/election/{electionId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ELECTION_ADMIN', 'AUDITOR')")
    public ResponseEntity<List<Candidate>> getCandidates(@PathVariable UUID electionId) {
        return ResponseEntity.ok(candidateService.getCandidatesForElection(electionId));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
