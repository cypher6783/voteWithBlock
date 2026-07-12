package com.votewithblock.gateway.controller;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.votewithblock.gateway.model.OutboxEvent;
import com.votewithblock.gateway.model.VoteRequestLog;
import com.votewithblock.gateway.model.VoterAccount;
import com.votewithblock.gateway.repository.OutboxEventRepository;
import com.votewithblock.gateway.repository.VoteRequestLogRepository;
import com.votewithblock.gateway.repository.VoterRepository;
import com.votewithblock.gateway.service.InputValidationService;
import com.votewithblock.gateway.service.RateLimitService;
import com.votewithblock.gateway.service.SessionTokenService;
import com.votewithblock.gateway.util.HashUtil;
import com.votewithblock.gateway.util.SafeLog;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/vote")
public class VoteController {

    private final VoterRepository voterRepository;
    private final SessionTokenService sessionTokenService;
    private final RateLimitService rateLimitService;
    private final InputValidationService inputValidationService;
    private final VoteRequestLogRepository voteRequestLogRepository;
    private final OutboxEventRepository outboxEventRepository;

    public VoteController(VoterRepository voterRepository,
                          SessionTokenService sessionTokenService,
                          RateLimitService rateLimitService,
                          InputValidationService inputValidationService,
                          VoteRequestLogRepository voteRequestLogRepository,
                          OutboxEventRepository outboxEventRepository) {
        this.voterRepository = voterRepository;
        this.sessionTokenService = sessionTokenService;
        this.rateLimitService = rateLimitService;
        this.inputValidationService = inputValidationService;
        this.voteRequestLogRepository = voteRequestLogRepository;
        this.outboxEventRepository = outboxEventRepository;
    }

    @Transactional
    @PostMapping("/cast")
    public ResponseEntity<VoteCastResponse> castVote(@RequestHeader(name = "Authorization", required = false) String authorization,
                                                     @Valid @RequestBody VoteCastRequest request,
                                                     HttpServletRequest servletRequest) {
        rateLimitService.requireAllowed("vote:" + clientIp(servletRequest) + ":" + request.getNationalId());

        String sessionNationalId = sessionTokenService.requireBiometricClaim(authorization);

        if (!sessionNationalId.equals(request.getNationalId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Session does not match voter");
        }
        inputValidationService.requireValidBallot(request.getElectionId(), request.getCandidateId(), request.getToken());

        log.info("Received vote request for election: {}, candidate: {}, voter: {}",
                request.getElectionId(), request.getCandidateId(), SafeLog.id(request.getNationalId()));

        // 1. Idempotency check - database-backed, survives crashes
        String idempotencyKey = generateIdempotencyKey(request.getNationalId(), request.getElectionId(), request.getCandidateId());
        Optional<VoteRequestLog> existingRequest = voteRequestLogRepository.findByIdempotencyKey(idempotencyKey);
        if (existingRequest.isPresent()) {
            VoteRequestLog previous = existingRequest.get();
            log.info("Duplicate vote request detected for voter {}. Returning cached result.", SafeLog.id(request.getNationalId()));
            return ResponseEntity.ok(VoteCastResponse.builder()
                    .success(true)
                    .message("Vote already submitted")
                    .transactionId(previous.getTransactionId())
                    .build());
        }

        // 2. Validate voter identity and check if already voted
        VoterAccount voter = voterRepository.findByNationalId(request.getNationalId())
                .orElseThrow(() -> {
                    log.warn("Voter {} not found", SafeLog.id(request.getNationalId()));
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Voter account not found");
                });

        if (voter.isHasVoted()) {
            log.warn("Voter {} has already cast a vote. Replay protection triggered.", SafeLog.id(request.getNationalId()));
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Double voting attempt detected. Voter has already voted.");
        }

        // 3. Update voter state + insert outbox event + log idempotency key in single transaction
        voter.setHasVoted(true);
        voterRepository.saveAndFlush(voter);
        log.info("Off-chain database voter state updated: hasVoted = true for voter {}", SafeLog.id(request.getNationalId()));

        // 4. Insert outbox event for reliable Fabric delivery
        String payload = request.getToken() + "|" + request.getElectionId() + "|" + request.getCandidateId();
        OutboxEvent outboxEvent = OutboxEvent.builder()
                .eventType("VOTE_SUBSCRIPTION")
                .payload(payload)
                .status("PENDING")
                .createdAt(OffsetDateTime.now())
                .maxRetries(3)
                .build();
        outboxEventRepository.save(outboxEvent);

        // 5. Record idempotency key
        String voterIdHash = HashUtil.sha256Hex(request.getNationalId());
        VoteRequestLog voteRequestLog = VoteRequestLog.builder()
                .idempotencyKey(idempotencyKey)
                .voterIdHash(voterIdHash)
                .electionId(request.getElectionId())
                .candidateId(request.getCandidateId())
                .status("PENDING")
                .createdAt(OffsetDateTime.now())
                .build();
        voteRequestLogRepository.save(voteRequestLog);

        log.info("Vote recorded for voter {}. Outbox event queued for Fabric submission.", SafeLog.id(request.getNationalId()));

        return ResponseEntity.ok(VoteCastResponse.builder()
                .success(true)
                .message("Vote successfully recorded. Blockchain submission pending.")
                .transactionId("pending-" + outboxEvent.getId())
                .build());
    }

    private static String generateIdempotencyKey(String nationalId, UUID electionId, UUID candidateId) {
        String raw = nationalId + "|" + electionId + "|" + candidateId;
        return HashUtil.sha256Hex(raw);
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VoteCastRequest {
        @NotBlank
        @Size(max = 64)
        @Pattern(regexp = "^[A-Za-z0-9._:-]+$")
        private String nationalId;

        @NotNull
        private java.util.UUID electionId;

        @NotNull
        private java.util.UUID candidateId;

        @NotBlank
        @Size(min = 12, max = 128)
        @Pattern(regexp = "^[A-Za-z0-9._:-]+$")
        @JsonAlias("anonymousToken")
        private String token;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VoteCastResponse {
        private boolean success;
        private String message;
        private String transactionId;
    }
}
