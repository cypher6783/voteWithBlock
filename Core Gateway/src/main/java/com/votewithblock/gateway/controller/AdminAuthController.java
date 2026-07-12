package com.votewithblock.gateway.controller;

import com.votewithblock.gateway.model.AdminAccount;
import com.votewithblock.gateway.model.AdminActionLog;
import com.votewithblock.gateway.model.AdminSession;
import com.votewithblock.gateway.repository.AdminAccountRepository;
import com.votewithblock.gateway.repository.AdminActionLogRepository;
import com.votewithblock.gateway.repository.AdminSessionRepository;
import com.votewithblock.gateway.service.AdminTokenService;
import com.votewithblock.gateway.service.PasswordService;
import com.votewithblock.gateway.service.RateLimitService;
import com.votewithblock.gateway.util.HashUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private final AdminAccountRepository adminAccountRepository;
    private final AdminSessionRepository adminSessionRepository;
    private final AdminActionLogRepository adminActionLogRepository;
    private final AdminTokenService adminTokenService;
    private final PasswordService passwordService;
    private final RateLimitService rateLimitService;

    public AdminAuthController(AdminAccountRepository adminAccountRepository,
                               AdminSessionRepository adminSessionRepository,
                               AdminActionLogRepository adminActionLogRepository,
                               AdminTokenService adminTokenService,
                               PasswordService passwordService,
                               RateLimitService rateLimitService) {
        this.adminAccountRepository = adminAccountRepository;
        this.adminSessionRepository = adminSessionRepository;
        this.adminActionLogRepository = adminActionLogRepository;
        this.adminTokenService = adminTokenService;
        this.passwordService = passwordService;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping("/login")
    @Transactional
    public ResponseEntity<AdminLoginResponse> login(@RequestBody AdminLoginRequest request, HttpServletRequest servletRequest) {
        String ip = clientIp(servletRequest);
        String username = request.getUsername() != null ? request.getUsername().trim() : "";
        
        // Admin Login Rate Limiting (reusing existing RateLimitService)
        rateLimitService.requireAllowed("admin-login:" + ip + ":" + username);

        AdminAccount account = adminAccountRepository.findByUsername(username).orElse(null);

        if (account == null || !account.isActive() || 
            !passwordService.matches(request.getPassword(), account.getPasswordSalt(), account.getPasswordHash())) {
            
            logAction(HashUtil.sha256Hex(username), "ADMIN_LOGIN_FAIL", "ADMIN", HashUtil.sha256Hex(username), "FAIL", ip);
            
            if (account != null) {
                account.setFailedLoginAttempts(account.getFailedLoginAttempts() + 1);
                account.setLastFailedLoginAt(OffsetDateTime.now());
                adminAccountRepository.save(account);
            }
            
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid admin credentials");
        }

        // Reset failed login attempts and update last_login_at
        account.setFailedLoginAttempts(0);
        account.setLastFailedLoginAt(null);
        account.setLastLoginAt(OffsetDateTime.now());
        adminAccountRepository.save(account);

        String token = adminTokenService.issue(username, account.getRole().name());
        
        // Extract JTI from the token we just generated (to store the session)
        Map<String, Object> claims = adminTokenService.validateAndExtractClaims("Bearer " + token);
        String jti = (String) claims.get("jti");

        AdminSession session = AdminSession.builder()
                .jti(jti)
                .username(username)
                .sessionType("ADMIN_SESSION")
                .issuedAt(OffsetDateTime.now())
                .expiresAt(OffsetDateTime.now().plusSeconds(adminTokenService.getTtlSeconds()))
                .revoked(false)
                .build();
        adminSessionRepository.save(session);

        logAction(HashUtil.sha256Hex(username), "ADMIN_LOGIN_SUCCESS", "ADMIN", HashUtil.sha256Hex(username), "SUCCESS", ip);

        // Future MFA placeholder check can go here

        return ResponseEntity.ok(new AdminLoginResponse(token, account.getRole().name(), false));
    }

    @PostMapping("/logout")
    @Transactional
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String authHeader, HttpServletRequest servletRequest) {
        try {
            Map<String, Object> claims = adminTokenService.validateAndExtractClaims(authHeader);
            String jti = (String) claims.get("jti");
            String username = (String) claims.get("sub");
            String ip = clientIp(servletRequest);

            AdminSession session = adminSessionRepository.findByJti(jti).orElse(null);
            if (session != null && !session.isRevoked()) {
                session.setRevoked(true);
                adminSessionRepository.save(session);
                logAction(HashUtil.sha256Hex(username), "ADMIN_LOGOUT", "ADMIN", HashUtil.sha256Hex(username), "SUCCESS", ip);
            }
        } catch (Exception e) {
            log.warn("Invalid token during logout", e);
            // Ignore token validation failure during logout to ensure idempotence
        }
        return ResponseEntity.noContent().build();
    }

    private void logAction(String adminHash, String action, String targetType, String targetHash, String outcome, String ip) {
        AdminActionLog logEntry = AdminActionLog.builder()
                .adminUsernameHash(adminHash)
                .action(action)
                .targetEntityType(targetType)
                .targetEntityIdHash(targetHash)
                .outcome(outcome)
                .ipHash(HashUtil.sha256Hex(ip))
                .performedAt(OffsetDateTime.now())
                .build();
        adminActionLogRepository.save(logEntry);
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
    public static class AdminLoginRequest {
        private String username;
        private String password;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminLoginResponse {
        private String adminToken;
        private String role;
        private boolean mfaRequired;
    }
}
