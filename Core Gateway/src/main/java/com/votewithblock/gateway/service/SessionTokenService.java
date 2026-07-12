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
public class SessionTokenService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final byte[] secret;
    private final long ttlSeconds;

    public SessionTokenService(
            @Value("${app.auth.session-secret}") String sessionSecret,
            @Value("${app.auth.session-ttl-seconds:900}") long ttlSeconds) {
        if (sessionSecret == null || sessionSecret.length() < 32) {
            throw new IllegalStateException("app.auth.session-secret must be at least 32 characters");
        }
        this.secret = sessionSecret.getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = ttlSeconds;
    }

    public String issue(String nationalId) {
        try {
            long expiresAt = Instant.now().getEpochSecond() + ttlSeconds;
            String payloadJson = objectMapper.writeValueAsString(Map.of(
                    "sub", nationalId,
                    "exp", expiresAt,
                    "jti", UUID.randomUUID().toString(),
                    "bio", true
            ));
            String payload = base64Url(payloadJson.getBytes(StandardCharsets.UTF_8));
            String signature = sign(payload);
            return payload + "." + signature;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to issue session token", e);
        }
    }

    public String requireSubject(String authorizationHeader) {
        return requireSubjectInternal(authorizationHeader, false);
    }

    public String requireBiometricClaim(String authorizationHeader) {
        return requireSubjectInternal(authorizationHeader, true);
    }

    private String requireSubjectInternal(String authorizationHeader, boolean requireBio) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing bearer session token");
        }
        String token = authorizationHeader.substring("Bearer ".length()).trim();
        String[] parts = token.split("\\.");
        if (parts.length != 2 || !constantTimeEquals(sign(parts[0]), parts[1])) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid session token");
        }
        try {
            Map<?, ?> claims = objectMapper.readValue(Base64.getUrlDecoder().decode(parts[0]), Map.class);
            Object exp = claims.get("exp");
            Object sub = claims.get("sub");
            if (!(sub instanceof String) || !(exp instanceof Number)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid session token claims");
            }
            if (((Number) exp).longValue() < Instant.now().getEpochSecond()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session token expired");
            }
            if (requireBio) {
                Object bio = claims.get("bio");
                if (!Boolean.TRUE.equals(bio)) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Session lacks biometric verification claim");
                }
            }
            return (String) sub;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid session token");
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
            throw new IllegalStateException("Unable to sign session token", e);
        }
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }
}
