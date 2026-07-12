package com.votewithblock.gateway.service;

import com.votewithblock.gateway.model.Candidate;
import com.votewithblock.gateway.model.Election;
import com.votewithblock.gateway.model.ElectionStatus;
import com.votewithblock.gateway.repository.CandidateRepository;
import com.votewithblock.gateway.repository.ElectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class InputValidationService {

    private static final Pattern BALLOT_TOKEN_PATTERN = Pattern.compile("^[A-Za-z0-9._:-]{12,128}$");
    
    private final ElectionRepository electionRepository;
    private final CandidateRepository candidateRepository;

    public void requireValidBallot(UUID electionId, UUID candidateId, String token) {
        Election election = electionRepository.findById(electionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid election"));
                
        if (election.getStatus() != ElectionStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Election is not currently OPEN for voting");
        }

        Candidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid candidate"));
                
        if (!candidate.getElectionId().equals(electionId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Candidate does not belong to the specified election");
        }
        
        if (!candidate.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Candidate is no longer active");
        }

        if (token == null || !BALLOT_TOKEN_PATTERN.matcher(token).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid ballot token");
        }
    }
}
