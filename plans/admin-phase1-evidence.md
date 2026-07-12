# Admin Portal Phase 1: Foundation & Security — Repository Evidence

This document captures the physical state of the Votetrust Ledger repository after completing Phase 1 of the Admin Portal implementation. It focuses strictly on the evidence that exists in code.

## 1. Database Schema
**Evidence:** `database_migrations_v2_admin.sql`
- `admin_accounts`: Created with fields `username`, `password_hash`, `password_salt`, `role`, `mfa_secret`, `mfa_enabled`, `is_active`, `created_at`, `last_login_at`, and `created_by`.
- `admin_sessions`: Created with fields `jti`, `username`, `session_type`, `issued_at`, `expires_at`, and `revoked`. Includes indexes on `username` and `expires_at`.
- `admin_action_log`: Created with `admin_username_hash`, `action`, `target_entity_type`, `target_entity_id_hash`, `outcome`, `ip_hash`, `performed_at`, and `metadata`. Includes an index on `performed_at`.

## 2. Spring Security Integration
**Evidence:** `pom.xml`, `SecurityConfig.java`, `AdminAuthFilter.java`
- `spring-boot-starter-security` is present in `pom.xml`.
- `SecurityConfig.java` implements a stateless session policy.
- Public paths (`/api/auth/**`, `/api/vote/**`, `/actuator/**`) are explicitly `permitAll()`.
- Admin login (`/api/admin/auth/login`) is `permitAll()`.
- Protected admin paths (`/api/admin/**`) are secured using `.authenticated()`.
- `AdminAuthFilter` is registered before `UsernamePasswordAuthenticationFilter` and checks the database for session revocation via `AdminSessionRepository.existsByJtiAndRevokedFalse(jti)`.

## 3. Core Admin Entities & Repositories
**Evidence:** `AdminRole.java`, `AdminAccount.java`, `AdminSession.java`, `AdminActionLog.java` and their respective repositories in `com.votewithblock.gateway.repository`.
- `AdminRole` Enum defined with `SUPER_ADMIN`, `ELECTION_ADMIN`, and `AUDITOR`.
- JPA Entities align strictly with the SQL schema.
- Repositories extend `JpaRepository`.

## 4. Admin Authentication & Bootstrapping
**Evidence:** `AdminTokenService.java`, `AdminAuthController.java`, `AdminBootstrapRunner.java`
- `AdminTokenService` signs tokens with `app.admin.token-secret` and validates claims.
- `AdminAuthController` provides `/login` (with rate limiting via `RateLimitService`) and `/logout` (with idempotent session revocation).
- `AdminBootstrapRunner` creates a `SYSTEM` bootstrap user if `app.admin.bootstrap.enabled` is true, failing fast if credentials are missing.
- Both successful/failed logins and logouts are logged to `AdminActionLog`.

## 5. Audit Hashing Improvements
**Evidence:** `HashUtil.java`, `BiometricEnrollmentService.java`, `BiometricVerificationService.java`
- `HashUtil` provides cryptographically strong SHA-256 hashing.
- `BiometricAttemptLog` insertions now use `HashUtil.sha256Hex()` instead of `Object.hashCode()` for `nationalId` and `ipAddress`.
- `AdminActionLog` uses `HashUtil` to anonymize administrator actions.

## 6. Endpoint Protection
**Evidence:** `AdminController.java`
- The endpoint `/api/admin/biometrics/enroll` is protected with `@PreAuthorize("hasRole('SUPER_ADMIN')")`.
