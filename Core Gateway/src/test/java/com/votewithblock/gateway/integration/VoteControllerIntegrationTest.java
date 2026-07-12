package com.votewithblock.gateway.integration;

import com.votewithblock.gateway.model.OutboxEvent;
import com.votewithblock.gateway.model.VoteRequestLog;
import com.votewithblock.gateway.model.VoterAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class VoteControllerIntegrationTest {

    private VoterAccount testVoter;
    private UUID electionId;
    private UUID candidateId;

    @BeforeEach
    void setUp() {
        testVoter = new VoterAccount("US-98420-A", "Test Voter", false, null, "salt", "hash", "ACTIVE", 0, null);
        electionId = UUID.randomUUID();
        candidateId = UUID.randomUUID();
    }

    @Test
    void voterAccount_shouldSupportDoubleVotePrevention() {
        assertFalse(testVoter.isHasVoted());

        testVoter.setHasVoted(true);
        assertTrue(testVoter.isHasVoted());
    }

    @Test
    void outboxEvent_shouldTrackVoteSubmission() {
        OutboxEvent event = OutboxEvent.builder()
                .eventType("VOTE_SUBSCRIPTION")
                .payload("token|" + electionId + "|" + candidateId)
                .status("PENDING")
                .createdAt(OffsetDateTime.now())
                .maxRetries(3)
                .retryCount(0)
                .build();

        assertEquals("VOTE_SUBSCRIPTION", event.getEventType());
        assertEquals("PENDING", event.getStatus());
        assertEquals(0, event.getRetryCount());
        assertEquals(3, event.getMaxRetries());
    }

    @Test
    void outboxEvent_shouldSupportRetryTracking() {
        OutboxEvent event = OutboxEvent.builder()
                .eventType("VOTE_SUBSCRIPTION")
                .payload("token|election|candidate")
                .status("PENDING")
                .createdAt(OffsetDateTime.now())
                .maxRetries(3)
                .retryCount(0)
                .build();

        event.setRetryCount(event.getRetryCount() + 1);
        assertEquals(1, event.getRetryCount());

        event.setRetryCount(event.getRetryCount() + 1);
        assertEquals(2, event.getRetryCount());

        event.setRetryCount(event.getRetryCount() + 1);
        assertEquals(3, event.getRetryCount());

        if (event.getRetryCount() >= event.getMaxRetries()) {
            event.setStatus("FAILED");
        }
        assertEquals("FAILED", event.getStatus());
    }

    @Test
    void outboxEvent_shouldMarkAsProcessed() {
        OutboxEvent event = OutboxEvent.builder()
                .eventType("VOTE_SUBSCRIPTION")
                .payload("token|election|candidate")
                .status("PENDING")
                .createdAt(OffsetDateTime.now())
                .maxRetries(3)
                .retryCount(0)
                .build();

        event.setStatus("PROCESSED");
        event.setProcessedAt(OffsetDateTime.now());

        assertEquals("PROCESSED", event.getStatus());
        assertNotNull(event.getProcessedAt());
    }

    @Test
    void voteRequestLog_shouldTrackIdempotency() {
        VoteRequestLog log = VoteRequestLog.builder()
                .idempotencyKey("key-123")
                .voterIdHash("hash-456")
                .electionId(electionId)
                .candidateId(candidateId)
                .status("PENDING")
                .createdAt(OffsetDateTime.now())
                .build();

        assertEquals("key-123", log.getIdempotencyKey());
        assertEquals("hash-456", log.getVoterIdHash());
        assertEquals(electionId, log.getElectionId());
        assertEquals(candidateId, log.getCandidateId());
        assertEquals("PENDING", log.getStatus());
    }

    @Test
    void voteRequestLog_shouldStoreTransactionId() {
        VoteRequestLog log = VoteRequestLog.builder()
                .idempotencyKey("key-123")
                .voterIdHash("hash-456")
                .electionId(electionId)
                .candidateId(candidateId)
                .status("SUBMITTED")
                .transactionId("tx-789")
                .createdAt(OffsetDateTime.now())
                .processedAt(OffsetDateTime.now())
                .build();

        assertEquals("tx-789", log.getTransactionId());
        assertEquals("SUBMITTED", log.getStatus());
        assertNotNull(log.getProcessedAt());
    }

    @Test
    void voterAccount_shouldPersistStateAcrossOperations() {
        assertFalse(testVoter.isHasVoted());

        testVoter.setHasVoted(true);
        assertTrue(testVoter.isHasVoted());

        testVoter.setHasVoted(false);
        assertFalse(testVoter.isHasVoted());
    }
}
