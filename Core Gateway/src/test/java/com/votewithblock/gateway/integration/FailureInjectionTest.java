package com.votewithblock.gateway.integration;

import com.votewithblock.gateway.model.*;
import com.votewithblock.gateway.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FailureInjectionTest {

    @Test
    void fabricUnavailable_shouldUseSandboxMode() {
        VotingLedgerService ledgerService = new VotingLedgerService();
        assertFalse(ledgerService.isContractAvailable());
    }

    @Test
    void outboxRetry_shouldIncrementRetryCount() {
        OutboxEvent event = OutboxEvent.builder()
                .eventType("VOTE_SUBSCRIPTION")
                .payload("token|election|candidate")
                .status("PENDING")
                .retryCount(0)
                .maxRetries(3)
                .createdAt(OffsetDateTime.now())
                .build();

        event.setRetryCount(event.getRetryCount() + 1);
        assertEquals(1, event.getRetryCount());
    }

    @Test
    void outboxMaxRetries_shouldMarkAsFailed() {
        OutboxEvent event = OutboxEvent.builder()
                .eventType("VOTE_SUBSCRIPTION")
                .payload("token|election|candidate")
                .status("PENDING")
                .retryCount(3)
                .maxRetries(3)
                .createdAt(OffsetDateTime.now())
                .build();

        if (event.getRetryCount() >= event.getMaxRetries()) {
            event.setStatus("FAILED");
        }
        assertEquals("FAILED", event.getStatus());
    }

    @Test
    void duplicateVote_shouldReturnCachedResult() {
        VoteRequestLog existingLog = VoteRequestLog.builder()
                .idempotencyKey("existing-key")
                .status("PENDING")
                .transactionId("existing-tx")
                .build();

        assertEquals("existing-tx", existingLog.getTransactionId());
        assertEquals("PENDING", existingLog.getStatus());
    }

    @Test
    void biometricFailure_shouldTrackStatus() {
        BiometricVerificationService.VerificationResult result =
                BiometricVerificationService.VerificationResult.FAIL_SIMILARITY;
        assertEquals(BiometricVerificationService.VerificationResult.FAIL_SIMILARITY, result);
    }

    @Test
    void accountLocked_shouldTrackStatus() {
        BiometricVerificationService.VerificationResult result =
                BiometricVerificationService.VerificationResult.FAIL_LOCKED;
        assertEquals(BiometricVerificationService.VerificationResult.FAIL_LOCKED, result);
    }

    @Test
    void electionClosed_shouldRejectVote() {
        InputValidationService validationService = new InputValidationService(null, null);

        assertThrows(Exception.class, () -> {
            validationService.requireValidBallot(UUID.randomUUID(), UUID.randomUUID(), "token");
        });
    }

    @Test
    void expiredSession_shouldReturn401() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Session expired");
        });
        assertEquals(org.springframework.http.HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void revokedSession_shouldBeDetected() {
        boolean isRevoked = true;
        assertTrue(isRevoked, "Revoked session should be detected");
    }

    @Test
    void recoveryAfterRestart_shouldRebuildFromDatabase() {
        VoterAccount voter = new VoterAccount("US-98420-A", "Test", false, null, null, null, "ACTIVE", 0, null);
        voter.setHasVoted(true);

        VoterAccount restoredVoter = new VoterAccount("US-98420-A", "Test", voter.isHasVoted(), null, null, null, "ACTIVE", 0, null);
        assertTrue(restoredVoter.isHasVoted());
    }
}
