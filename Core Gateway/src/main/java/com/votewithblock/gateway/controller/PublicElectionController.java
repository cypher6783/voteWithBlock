package com.votewithblock.gateway.controller;

import com.votewithblock.gateway.model.Candidate;
import com.votewithblock.gateway.model.Election;
import com.votewithblock.gateway.service.CandidateService;
import com.votewithblock.gateway.service.ElectionService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/elections")
@RequiredArgsConstructor
public class PublicElectionController {

    private final ElectionService electionService;
    private final CandidateService candidateService;

    @GetMapping("/active")
    public ResponseEntity<ActiveElectionResponse> getActiveElection() {
        Election active = electionService.getActiveElection();
        if (active == null) {
            return ResponseEntity.noContent().build();
        }

        List<Candidate> candidates = candidateService.getActiveCandidatesForElection(active.getId());
        return ResponseEntity.ok(new ActiveElectionResponse(active, candidates));
    }

    @Data
    public static class ActiveElectionResponse {
        private final Election election;
        private final List<Candidate> candidates;
    }
}
