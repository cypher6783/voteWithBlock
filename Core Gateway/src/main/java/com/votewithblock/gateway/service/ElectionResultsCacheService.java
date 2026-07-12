package com.votewithblock.gateway.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class ElectionResultsCacheService {

    private final VotingLedgerService votingLedgerService;

    // Cache results for 10 seconds to prevent Fabric spam
    private final Cache<String, String> resultsCache = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.SECONDS)
            .maximumSize(100)
            .build();

    public String getElectionResults(String electionCode) {
        return resultsCache.get(electionCode, this::fetchFromFabric);
    }

    private String fetchFromFabric(String electionCode) {
        log.info("Cache miss for {}, querying Hyperledger Fabric...", electionCode);
        try {
            // Since VotingLedgerService doesn't have a GetElectionTally method yet, we simulate one.
            // In a real scenario, this would call contract.evaluateTransaction("GetElectionTally", electionCode)
            // For now, we return a mock payload if not implemented.
            return "{\"status\":\"success\", \"election\":\"" + electionCode + "\", \"tallies\":{}}";
        } catch (Exception e) {
            log.error("Failed to fetch results from Fabric", e);
            throw new RuntimeException("Failed to fetch results from blockchain", e);
        }
    }
}
