package com.votewithblock.controller;

import com.votewithblock.service.SessionTokenService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
public class BiometricChallengeController {

    private final SessionTokenService sessionTokenService;
    // In production, use a distributed cache like Redis
    private final Map<String, String> challengeStore = new HashMap<>();

    public BiometricChallengeController(SessionTokenService sessionTokenService) {
        this.sessionTokenService = sessionTokenService;
    }

    /**
     * Creates a biometric challenge from a preAuthToken.
     * The preAuthToken is expected to be a valid JWT issued after initial authentication.
     *
     * @param preAuthToken The Authorization header value (Bearer token)
     * @return Biometric challenge response containing a random nonce
     */
    @PostMapping("/biometric-challenge")
    public ResponseEntity<BiometricChallengeResponse> createChallenge(@RequestHeader("Authorization") String preAuthToken) {
        // Extract token from Bearer prefix
        String token = preAuthToken.startsWith("Bearer ")
            ? preAuthToken.substring(7)
            : preAuthToken;

        // Validate the preAuthToken
        if (!sessionTokenService.validateToken(token)) {
            return ResponseEntity.status(401).build();
        }

        // Generate a cryptographically random challenge (nonce)
        String challenge = Base64.getEncoder()
                .encodeToString(UUID.randomUUID().toString().getBytes());

        // Store challenge associated with token for later verification
        // In production, associate with user ID from token and use expiry
        challengeStore.put(token, challenge);

        return ResponseEntity.ok(new BiometricChallengeResponse(challenge));
    }

    /**
     * Response DTO for biometric challenge
     */
    public static class BiometricChallengeResponse {
        private String challenge;

        public BiometricChallengeResponse() {}

        public BiometricChallengeResponse(String challenge) {
            this.challenge = challenge;
        }

        public String getChallenge() {
            return challenge;
        }

        public void setChallenge(String challenge) {
            this.challenge = challenge;
        }
    }
}