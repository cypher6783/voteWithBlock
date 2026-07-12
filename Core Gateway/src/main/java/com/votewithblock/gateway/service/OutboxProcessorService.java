package com.votewithblock.gateway.service;

import com.votewithblock.gateway.model.OutboxEvent;
import com.votewithblock.gateway.repository.OutboxEventRepository;
import com.votewithblock.gateway.util.SafeLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class OutboxProcessorService {

    private final OutboxEventRepository outboxEventRepository;
    private final VotingLedgerService votingLedgerService;

    @Scheduled(fixedRate = 5000)
    @Transactional
    public void processPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findProcessableEvents();

        for (OutboxEvent event : pendingEvents) {
            processEvent(event);
        }
    }

    private void processEvent(OutboxEvent event) {
        try {
            log.info("Processing outbox event: {} (id={})", event.getEventType(), event.getId());

            if ("VOTE_SUBSCRIPTION".equals(event.getEventType())) {
                processVoteSubscription(event);
            }

            event.setStatus("PROCESSED");
            event.setProcessedAt(OffsetDateTime.now());
            outboxEventRepository.save(event);

            log.info("Outbox event processed successfully: id={}", event.getId());
        } catch (Exception e) {
            log.error("Failed to process outbox event id={}: {}", event.getId(), e.getMessage());
            event.setRetryCount(event.getRetryCount() + 1);
            event.setLastError(e.getMessage());

            if (event.getRetryCount() >= event.getMaxRetries()) {
                event.setStatus("FAILED");
                log.error("Outbox event id={} exceeded max retries, marked as FAILED", event.getId());
            }

            outboxEventRepository.save(event);
        }
    }

    private void processVoteSubscription(OutboxEvent event) {
        String[] parts = event.getPayload().split("\\|");
        if (parts.length < 3) {
            throw new IllegalArgumentException("Invalid vote subscription payload");
        }

        String token = parts[0];
        String electionId = parts[1];
        String candidateId = parts[2];

        log.info("Submitting vote to Fabric via outbox: election={}, candidate={}", electionId, candidateId);
        votingLedgerService.submitBallotToLedger(token, electionId, candidateId);
    }

    public long getPendingCount() {
        return outboxEventRepository.countByStatus("PENDING");
    }

    public long getFailedCount() {
        return outboxEventRepository.countByStatus("FAILED");
    }
}
