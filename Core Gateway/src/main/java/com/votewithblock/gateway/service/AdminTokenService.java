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
public class AdminTokenService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final byte[] secret;
    private final long ttlSeconds;

    public AdminTokenService(
            @Value("${app.admin.token-secret}") String adminTokenSecret,
            @Value("${app.admin.token-ttl-seconds:28800}") long ttlSeconds) { // Default 8 hours
        if (adminTokenSecret == null || adminTokenSecret.length() < 32) {
            throw new IllegalStateException("app.admin.token-secret must be at least 32 characters");
        }
        this.secret = adminTokenSecret.getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = ttlSeconds;
    }

    public String issue(String username, String role) {
        try {
            long expiresAt = Instant.now().getEpochSecond() + ttlSeconds;
            String payloadJson = objectMapper.writeValueAsString(Map.of(
                    "sub", username,
                    "exp", expiresAt,
                    "jti", UUID.randomUUID().toString(),
                    "role", role,
                    "type", "admin-session"
            ));
            String payload = base64Url(payloadJson.getBytes(StandardCharsets.UTF_8));
            String signature = sign(payload);
            return payload + "." + signature;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to issue admin token", e);
        }
    }

    public Map<String, Object> validateAndExtractClaims(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing bearer admin token");
        }
        String token = authorizationHeader.substring("Bearer ".length()).trim();
        String[] parts = token.split("\\.");
        if (parts.length != 2 || !constantTimeEquals(sign(parts[0]), parts[1])) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid admin token");
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> claims = objectMapper.readValue(Base64.getUrlDecoder().decode(parts[0]), Map.class);
            Object exp = claims.get("exp");
            Object sub = claims.get("sub");
            Object type = claims.get("type");
            
            if (!(sub instanceof String) || !(exp instanceof Number)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid admin token claims");
            }
            if (!"admin-session".equals(type)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token type");
            }
            if (((Number) exp).longValue() < Instant.now().getEpochSecond()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Admin token expired");
            }
            return claims;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid admin token");
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
            throw new IllegalStateException("Unable to sign admin token", e);
        }
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }
}
