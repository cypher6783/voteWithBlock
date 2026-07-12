package com.votewithblock.gateway.service;

import com.votewithblock.gateway.model.AdminActionLog;
import com.votewithblock.gateway.model.Candidate;
import com.votewithblock.gateway.model.Election;
import com.votewithblock.gateway.model.ElectionStatus;
import com.votewithblock.gateway.repository.AdminActionLogRepository;
import com.votewithblock.gateway.repository.CandidateRepository;
import com.votewithblock.gateway.repository.ElectionRepository;
import com.votewithblock.gateway.util.HashUtil;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CandidateService {

    private final CandidateRepository candidateRepository;
    private final ElectionRepository electionRepository;
    private final AdminActionLogRepository adminActionLogRepository;

    @Transactional
    public Candidate createCandidate(UUID electionId, Candidate candidate, String adminUsername, String ipAddress) {
        Election election = electionRepository.findById(electionId)
                .orElseThrow(() -> new IllegalArgumentException("Election not found"));

        if (election.getStatus() == ElectionStatus.OPEN) {
            throw new IllegalStateException("Cannot modify candidates for an OPEN election.");
        }

        candidate.setId(UUID.randomUUID());
        candidate.setElectionId(electionId);
        candidate.setCreatedAt(OffsetDateTime.now());
        candidate.setUpdatedAt(OffsetDateTime.now());
        
        Candidate saved = candidateRepository.save(candidate);
        logAction(adminUsername, ipAddress, "CREATE_CANDIDATE", saved.getId().toString(), "SUCCESS");
        return saved;
    }

    @Transactional
    public Candidate updateCandidate(UUID id, Candidate updates, String adminUsername, String ipAddress) {
        Candidate candidate = candidateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Candidate not found"));

        Election election = electionRepository.findById(candidate.getElectionId())
                .orElseThrow(() -> new IllegalStateException("Associated election not found"));

        if (election.getStatus() == ElectionStatus.OPEN) {
            throw new IllegalStateException("Cannot modify candidates for an OPEN election.");
        }

        candidate.setName(updates.getName());
        candidate.setParty(updates.getParty());
        candidate.setManifesto(updates.getManifesto());
        candidate.setImageUrl(updates.getImageUrl());
        candidate.setDisplayOrder(updates.getDisplayOrder());
        candidate.setUpdatedAt(OffsetDateTime.now());

        Candidate saved = candidateRepository.save(candidate);
        logAction(adminUsername, ipAddress, "UPDATE_CANDIDATE", saved.getId().toString(), "SUCCESS");
        return saved;
    }

    @Transactional
    public Candidate deactivateCandidate(UUID id, String adminUsername, String ipAddress) {
        Candidate candidate = candidateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Candidate not found"));

        Election election = electionRepository.findById(candidate.getElectionId())
                .orElseThrow(() -> new IllegalStateException("Associated election not found"));

        if (election.getStatus() == ElectionStatus.OPEN) {
            throw new IllegalStateException("Cannot modify candidates for an OPEN election.");
        }

        candidate.setActive(false);
        candidate.setUpdatedAt(OffsetDateTime.now());

        Candidate saved = candidateRepository.save(candidate);
        logAction(adminUsername, ipAddress, "DEACTIVATE_CANDIDATE", saved.getId().toString(), "SUCCESS");
        return saved;
    }

    public List<Candidate> getCandidatesForElection(UUID electionId) {
        return candidateRepository.findByElectionIdOrderByDisplayOrderAsc(electionId);
    }
    
    public List<Candidate> getActiveCandidatesForElection(UUID electionId) {
        return candidateRepository.findByElectionIdAndIsActiveTrueOrderByDisplayOrderAsc(electionId);
    }

    private void logAction(String username, String ip, String action, String targetId, String outcome) {
        AdminActionLog log = AdminActionLog.builder()
                .adminUsernameHash(HashUtil.sha256Hex(username))
                .action(action)
                .targetEntityType("CANDIDATE")
                .targetEntityIdHash(HashUtil.sha256Hex(targetId))
                .outcome(outcome)
                .ipHash(HashUtil.sha256Hex(ip))
                .performedAt(OffsetDateTime.now())
                .metadata("{}")
                .build();
        adminActionLogRepository.save(log);
    }
}
