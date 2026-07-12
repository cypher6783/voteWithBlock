package com.votewithblock.gateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Service
public class PreAuthTokenService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final byte[] secret;
    private final long ttlSeconds;

    public PreAuthTokenService(
            @Value("${app.auth.pre-auth-secret}") String preAuthSecret,
            @Value("${app.biometric.pre-auth-ttl-seconds:120}") long ttlSeconds) {
        if (preAuthSecret == null || preAuthSecret.length() < 32) {
            throw new IllegalStateException("app.auth.pre-auth-secret must be at least 32 characters and must differ from session-secret and admin token-secret");
        }
        this.secret = preAuthSecret.getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = ttlSeconds;
    }

    public String issue(String nationalId) {
        try {
            long expiresAt = Instant.now().getEpochSecond() + ttlSeconds;
            String payloadJson = objectMapper.writeValueAsString(Map.of(
                    "sub", nationalId,
                    "exp", expiresAt,
                    "jti", UUID.randomUUID().toString(),
                    "stage", "pre-auth"
            ));
            String payload = base64Url(payloadJson.getBytes(StandardCharsets.UTF_8));
            String signature = sign(payload);
            return payload + "." + signature;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to issue pre-auth token", e);
        }
    }

    public String requirePreAuthSubject(String tokenStr) {
        if (tokenStr == null || tokenStr.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing pre-auth token");
        }
        
        // Remove Bearer prefix if present
        if (tokenStr.startsWith("Bearer ")) {
            tokenStr = tokenStr.substring("Bearer ".length()).trim();
        }
        
        String[] parts = tokenStr.split("\\.");
        if (parts.length != 2 || !constantTimeEquals(sign(parts[0]), parts[1])) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid pre-auth token");
        }
        try {
            Map<?, ?> claims = objectMapper.readValue(Base64.getUrlDecoder().decode(parts[0]), Map.class);
            Object exp = claims.get("exp");
            Object sub = claims.get("sub");
            Object stage = claims.get("stage");
            
            if (!(sub instanceof String) || !(exp instanceof Number)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid pre-auth token claims");
            }
            if (!"pre-auth".equals(stage)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token is not a pre-auth token");
            }
            if (((Number) exp).longValue() < Instant.now().getEpochSecond()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Pre-auth token expired");
            }
            return (String) sub;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid pre-auth token");
        }
    }

    public long getTtlSeconds() {
        return ttlSeconds;
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return base64Url(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sign pre-auth token", e);
        }
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }
}
