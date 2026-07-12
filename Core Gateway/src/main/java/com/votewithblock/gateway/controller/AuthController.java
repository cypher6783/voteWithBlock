package com.votewithblock.gateway.controller;

import com.votewithblock.gateway.model.BiometricChallenge;
import com.votewithblock.gateway.model.VoterAccount;
import com.votewithblock.gateway.repository.VoterRepository;
import com.votewithblock.gateway.service.*;
import com.votewithblock.gateway.util.SafeLog;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final VoterRepository voterRepository;
    private final PasswordService passwordService;
    private final PreAuthTokenService preAuthTokenService;
    private final SessionTokenService sessionTokenService;
    private final RateLimitService rateLimitService;
    private final BiometricChallengeService challengeService;
    private final BiometricVerificationService biometricVerificationService;
    private final boolean allowBootstrapPassword;
    private final String bootstrapPassword;

    public AuthController(VoterRepository voterRepository,
                          PasswordService passwordService,
                          PreAuthTokenService preAuthTokenService,
                          SessionTokenService sessionTokenService,
                          RateLimitService rateLimitService,
                          BiometricChallengeService challengeService,
                          BiometricVerificationService biometricVerificationService,
                          @Value("${app.auth.allow-bootstrap-password:false}") boolean allowBootstrapPassword,
                          @Value("${app.auth.bootstrap-password:}") String bootstrapPassword) {
        this.voterRepository = voterRepository;
        this.passwordService = passwordService;
        this.preAuthTokenService = preAuthTokenService;
        this.sessionTokenService = sessionTokenService;
        this.rateLimitService = rateLimitService;
        this.challengeService = challengeService;
        this.biometricVerificationService = biometricVerificationService;
        this.allowBootstrapPassword = allowBootstrapPassword;
        this.bootstrapPassword = bootstrapPassword;
    }

    @Transactional
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        rateLimitService.requireAllowed("login:" + clientIp(request) + ":" + loginRequest.getNationalId());

        VoterAccount voter = voterRepository.findByNationalId(loginRequest.getNationalId())
                .orElseThrow(() -> {
                    log.warn("Login failed for unknown voter {}", SafeLog.id(loginRequest.getNationalId()));
                    return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
                });

        if (!hasUsablePassword(voter)) {
            if (!allowBootstrapPassword || bootstrapPassword.isBlank() || !bootstrapPassword.equals(loginRequest.getPassword())) {
                log.warn("Login rejected for voter {} because no credential is enrolled", SafeLog.id(voter.getNationalId()));
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
            }
            String salt = passwordService.newSalt();
            voter.setPasswordSalt(salt);
            voter.setPasswordHash(passwordService.hashPassword(loginRequest.getPassword(), salt));
            voterRepository.saveAndFlush(voter);
            log.info("Bootstrap credential enrolled for voter {}", SafeLog.id(voter.getNationalId()));
        } else if (!passwordService.matches(loginRequest.getPassword(), voter.getPasswordSalt(), voter.getPasswordHash())) {
            log.warn("Login failed for voter {}", SafeLog.id(voter.getNationalId()));
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        String preAuthToken = preAuthTokenService.issue(voter.getNationalId());
        log.info("Step 1 Login succeeded for voter {}, issued pre-auth token", SafeLog.id(voter.getNationalId()));
        return ResponseEntity.ok(new LoginResponse(voter.getNationalId(), preAuthToken, preAuthTokenService.getTtlSeconds(), !"NOT_ENROLLED".equals(voter.getBiometricStatus())));
    }

    @PostMapping("/biometric-challenge")
    public ResponseEntity<ChallengeResponse> getBiometricChallenge(@RequestHeader("Authorization") String authHeader) {
        String nationalId = preAuthTokenService.requirePreAuthSubject(authHeader);
        BiometricChallenge challenge = challengeService.createChallenge(nationalId);
        return ResponseEntity.ok(new ChallengeResponse(challenge.getChallengeId(), challenge.getExpiresAt()));
    }

    @PostMapping("/biometric-confirm")
    public ResponseEntity<SessionResponse> confirmBiometric(@RequestBody BiometricConfirmRequest requestPayload, HttpServletRequest request) {
        String nationalId = preAuthTokenService.requirePreAuthSubject(requestPayload.getPreAuthToken());

        if (!challengeService.validateAndConsumeChallenge(requestPayload.getChallengeId(), nationalId)) {
            log.warn("Biometric challenge validation failed for voter {}", SafeLog.id(nationalId));
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired biometric challenge");
        }

        rateLimitService.requireAllowed("biometric:verify:" + nationalId + ":" + clientIp(request));

        BiometricVerificationService.VerificationResult result = biometricVerificationService.verifyIdentity(nationalId, requestPayload.getBase64FaceImage(), clientIp(request));

        switch (result) {
            case PASS:
                String sessionToken = sessionTokenService.issue(nationalId);
                log.info("Step 2 Biometric confirm succeeded for voter {}", SafeLog.id(nationalId));
                return ResponseEntity.ok(new SessionResponse(nationalId, sessionToken, sessionTokenService.getTtlSeconds()));
            case FAIL_LOCKED:
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account locked for 15 minutes due to too many failed attempts");
            case FAIL_NOT_ENROLLED:
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Your biometric profile is not yet enrolled. Please contact your election administrator.");
            case FAIL_PENDING_REENROLLMENT:
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Your account requires re-enrollment. Please contact your election administrator.");
            case FAIL_DISABLED:
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Biometric verification disabled for this account.");
            case FAIL_LIVENESS:
            case FAIL_SIMILARITY:
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Biometric identity mismatch. Please try again.");
            default:
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error during biometric verification");
        }
    }

    private static boolean hasUsablePassword(VoterAccount voter) {
        return voter.getPasswordSalt() != null && !voter.getPasswordSalt().isBlank()
                && voter.getPasswordHash() != null && !voter.getPasswordHash().isBlank();
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
    public static class LoginRequest {
        @NotBlank
        @Size(max = 64)
        @Pattern(regexp = "^[A-Za-z0-9._:-]+$")
        private String nationalId;

        @NotBlank
        @Size(min = 8, max = 128)
        private String password;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginResponse {
        private String nationalId;
        private String preAuthToken;
        private long preAuthExpiresInSeconds;
        private boolean biometricEnrolled;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChallengeResponse {
        private UUID challengeId;
        private OffsetDateTime expiresAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BiometricConfirmRequest {
        @NotBlank
        private String preAuthToken;
        @NotBlank
        private String base64FaceImage;
        private UUID challengeId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionResponse {
        private String nationalId;
        private String sessionToken;
        private long expiresInSeconds;
    }
}
