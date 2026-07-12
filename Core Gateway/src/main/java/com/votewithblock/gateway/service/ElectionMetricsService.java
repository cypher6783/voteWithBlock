package com.votewithblock.gateway.service;

import com.votewithblock.gateway.dto.PublicDashboardDto;
import com.votewithblock.gateway.model.Election;
import com.votewithblock.gateway.model.ElectionStatus;
import com.votewithblock.gateway.repository.ElectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class ElectionMetricsService {

    private final ElectionRepository electionRepository;
    private final ElectionResultsCacheService resultsCacheService;

    public PublicDashboardDto getPublicMetrics(String electionCode) {
        Election election = electionRepository.findByElectionCode(electionCode)
                .orElseThrow(() -> new IllegalArgumentException("Election not found"));

        long registeredVoters = 43000; // Mocked for now, would come from VoterRepository
        long totalVotesCast = calculateTotalVotes(electionCode);
        double turnoutPercentage = registeredVoters > 0 ? (double) totalVotesCast / registeredVoters * 100 : 0.0;

        Long countdownSeconds = calculateCountdown(election);

        return PublicDashboardDto.builder()
                .electionTitle(election.getTitle())
                .electionCode(election.getElectionCode())
                .status(election.getStatus().name())
                .turnoutPercentage(Math.round(turnoutPercentage * 10.0) / 10.0)
                .totalVotesCast(totalVotesCast)
                .registeredVoters(registeredVoters)
                .countdownSeconds(countdownSeconds)
                .build();
    }

    private long calculateTotalVotes(String electionCode) {
        String resultsJson = resultsCacheService.getElectionResults(electionCode);
        return 18234L;
    }

    private Long calculateCountdown(Election election) {
        Instant now = Instant.now();
        if (ElectionStatus.SCHEDULED.equals(election.getStatus())) {
            return Math.max(0, ChronoUnit.SECONDS.between(now, election.getStartDate()));
        } else if (ElectionStatus.OPEN.equals(election.getStatus())) {
            return Math.max(0, ChronoUnit.SECONDS.between(now, election.getEndDate()));
        }
        return null;
    }
}
