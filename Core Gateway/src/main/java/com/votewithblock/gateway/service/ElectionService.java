package com.votewithblock.gateway.service;

import com.votewithblock.gateway.model.AdminActionLog;
import com.votewithblock.gateway.model.Election;
import com.votewithblock.gateway.model.ElectionStatus;
import com.votewithblock.gateway.repository.AdminActionLogRepository;
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
public class ElectionService {

    private final ElectionRepository electionRepository;
    private final AdminActionLogRepository adminActionLogRepository;

    @Transactional
    public Election createElection(Election election, String adminUsername, String ipAddress) {
        election.setId(UUID.randomUUID());
        election.setCreatedAt(OffsetDateTime.now());
        election.setUpdatedAt(OffsetDateTime.now());
        election.setCreatedBy(adminUsername);
        
        Election saved = electionRepository.save(election);
        logAction(adminUsername, ipAddress, "CREATE_ELECTION", saved.getId().toString(), "SUCCESS");
        return saved;
    }

    @Transactional
    public Election updateElection(UUID id, Election updates, String adminUsername, String ipAddress) {
        Election election = electionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Election not found"));

        if (election.getStatus() == ElectionStatus.OPEN) {
            throw new IllegalStateException("Cannot modify an OPEN election. Freeze rules apply.");
        }

        election.setTitle(updates.getTitle());
        election.setDescription(updates.getDescription());
        election.setType(updates.getType());
        election.setStartDate(updates.getStartDate());
        election.setEndDate(updates.getEndDate());
        election.setUpdatedAt(OffsetDateTime.now());

        Election saved = electionRepository.save(election);
        logAction(adminUsername, ipAddress, "UPDATE_ELECTION", saved.getId().toString(), "SUCCESS");
        return saved;
    }

    @Transactional
    public Election updateElectionStatus(UUID id, ElectionStatus newStatus, String adminUsername, String ipAddress) {
        Election election = electionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Election not found"));

        if (newStatus == ElectionStatus.OPEN) {
            // Enforce single active election
            List<Election> openElections = electionRepository.findByStatus(ElectionStatus.OPEN);
            if (!openElections.isEmpty() && openElections.stream().noneMatch(e -> e.getId().equals(id))) {
                throw new IllegalStateException("Another election is currently OPEN. Only one OPEN election is permitted.");
            }
        }

        election.setStatus(newStatus);
        election.setUpdatedAt(OffsetDateTime.now());

        Election saved = electionRepository.save(election);
        logAction(adminUsername, ipAddress, "CHANGE_STATUS_" + newStatus.name(), saved.getId().toString(), "SUCCESS");
        return saved;
    }

    public List<Election> getAllElections() {
        return electionRepository.findAll();
    }

    public Election getElection(UUID id) {
        return electionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Election not found"));
    }
    
    public Election getActiveElection() {
        List<Election> openElections = electionRepository.findByStatus(ElectionStatus.OPEN);
        if (openElections.isEmpty()) return null;
        return openElections.get(0);
    }

    private void logAction(String username, String ip, String action, String targetId, String outcome) {
        AdminActionLog log = AdminActionLog.builder()
                .adminUsernameHash(HashUtil.sha256Hex(username))
                .action(action)
                .targetEntityType("ELECTION")
                .targetEntityIdHash(HashUtil.sha256Hex(targetId))
                .outcome(outcome)
                .ipHash(HashUtil.sha256Hex(ip))
                .performedAt(OffsetDateTime.now())
                .metadata("{}")
                .build();
        adminActionLogRepository.save(log);
    }
}
