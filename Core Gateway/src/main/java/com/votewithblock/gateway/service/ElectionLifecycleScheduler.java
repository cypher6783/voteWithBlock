package com.votewithblock.gateway.service;

import com.votewithblock.gateway.model.Election;
import com.votewithblock.gateway.model.ElectionStatus;
import com.votewithblock.gateway.repository.ElectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ElectionLifecycleScheduler {

    private final ElectionRepository electionRepository;
    private final ElectionService electionService;

    // Run every minute
    @Scheduled(fixedRateString = "60000")
    @Transactional
    public void processLifecycleTransitions() {
        OffsetDateTime now = OffsetDateTime.now();

        // 1. SCHEDULED -> OPEN
        List<Election> scheduledElections = electionRepository.findByStatus(ElectionStatus.SCHEDULED);
        for (Election election : scheduledElections) {
            if (!now.isBefore(election.getStartDate())) {
                try {
                    log.info("Transitioning election {} from SCHEDULED to OPEN", election.getElectionCode());
                    electionService.updateElectionStatus(election.getId(), ElectionStatus.OPEN, "SYSTEM_SCHEDULER", "127.0.0.1");
                } catch (Exception e) {
                    log.error("Failed to open election {}: {}", election.getElectionCode(), e.getMessage());
                }
            }
        }

        // 2. OPEN -> CLOSED
        List<Election> openElections = electionRepository.findByStatus(ElectionStatus.OPEN);
        for (Election election : openElections) {
            if (now.isAfter(election.getEndDate())) {
                try {
                    log.info("Transitioning election {} from OPEN to CLOSED", election.getElectionCode());
                    electionService.updateElectionStatus(election.getId(), ElectionStatus.CLOSED, "SYSTEM_SCHEDULER", "127.0.0.1");
                } catch (Exception e) {
                    log.error("Failed to close election {}: {}", election.getElectionCode(), e.getMessage());
                }
            }
        }
    }
}
