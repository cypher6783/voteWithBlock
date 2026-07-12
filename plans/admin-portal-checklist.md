# Admin Portal — Implementation Checklist
# Votetrust Ledger

All items must reference findings in `admin-portal-evidence.md` and align with `admin-portal-plan.md`.

---

## Architecture Review

- [x] Authentication architecture reviewed
  - Evidence: `admin-portal-evidence.md §4 Existing Authentication Flow`
  - Finding: Existing HMAC-SHA256 token signing (`PreAuthTokenService`, `SessionTokenService`) is reusable. New `AdminTokenService` required with `role` and `type: "admin-session"` claims. No Spring Security exists — filter chain must be added.

- [x] Existing reusable services identified
  - Evidence: `admin-portal-evidence.md §2 Existing Services`
  - Finding: `PasswordService`, `RateLimitService`, `BiometricEnrollmentService`, `FaceVerificationService`, `EmbeddingEncryptionService`, `SafeLog` are all directly reusable. Token signing pattern from `PreAuthTokenService` and `SessionTokenService` is reusable by template.

- [x] RBAC architecture defined
  - Evidence: `admin-portal-evidence.md §9 What Does NOT Exist` — NO EVIDENCE FOUND for any existing role model
  - Finding: Three roles defined from scratch: `SUPER_ADMIN`, `ELECTION_ADMIN`, `AUDITOR`. Permission matrix defined in `admin-portal-plan.md §5`. Role embedded in admin token payload, enforced by new Spring Security filter on `/api/admin/**`.

- [x] Election management architecture defined
  - Evidence: `admin-portal-evidence.md §2 InputValidationService` — election IDs are static `@Value` config, not DB-driven. `admin-portal-evidence.md §9` — NO EVIDENCE FOUND for election entity.
  - Finding: New `elections` table and `Election` entity required. `InputValidationService` must be refactored from static allowlist to DB query. Lifecycle: `DRAFT → SCHEDULED → OPEN → CLOSED → ARCHIVED`. Full API defined in `admin-portal-plan.md §7`.

- [x] Candidate management architecture defined
  - Evidence: `admin-portal-evidence.md §2 InputValidationService` — candidate IDs are static `@Value` config. `admin-portal-evidence.md §9` — NO EVIDENCE FOUND for candidate entity.
  - Finding: New `candidates` table with FK to `elections`. Image stored as URL reference. Full CRUD API defined in `admin-portal-plan.md §8`.

- [x] Voter administration architecture defined
  - Evidence: `admin-portal-evidence.md §3 voter_accounts table` — `biometricStatus` values: `NOT_ENROLLED`, `ENROLLED`, `ACTIVE`, `DISABLED`, `PENDING_REENROLLMENT`. `VoterRepository` has `findByNationalId()` with pessimistic write lock.
  - Finding: Voter search, lock/unlock, biometric re-enrollment, and voting status override endpoints designed. All operations require `SUPER_ADMIN`. Full API defined in `admin-portal-plan.md §9`.

- [x] Audit centre architecture defined
  - Evidence: `admin-portal-evidence.md §3 biometric_attempt_log`, `§5 Existing Audit Capabilities`
  - Finding: `biometric_attempt_log` provides biometric event history. No query API exists (`BiometricAttemptLogRepository` has no custom methods). New `admin_action_log` and `election_events` tables required. Paginated query endpoints designed. Full design in `admin-portal-plan.md §10`.

- [x] Dashboard architecture defined
  - Evidence: `admin-portal-evidence.md §2 VotingLedgerService` — synchronous `RestTemplate` only. `admin-portal-evidence.md §6 Frontend` — no WebSocket infrastructure. `admin-portal-evidence.md §9` — NO EVIDENCE FOUND for WebSocket or SSE.
  - Finding: SSE (`SseEmitter`) selected over WebSocket. No existing infrastructure to leverage. Dashboard metrics sourced from `voter_accounts` count queries and `biometric_attempt_log`. Snapshot + stream endpoints designed. Full design in `admin-portal-plan.md §11`.

- [x] Security controls defined
  - Evidence: `admin-portal-evidence.md §2 SessionTokenService` — `type` claim not present in voter tokens; admin tokens will use `type: "admin-session"` to prevent cross-use. `admin-portal-evidence.md §2 PasswordService` — PBKDF2 reused for admin passwords.
  - Finding: Admin endpoint isolation via separate filter chain. TOTP MFA enforced for SUPER_ADMIN. Privilege escalation protected by cryptographic role claim. Full security design in `admin-portal-plan.md §12`.

- [x] Database changes identified
  - Evidence: `admin-portal-evidence.md §8 Database Migration File` — current schema has only biometric tables. `admin-portal-evidence.md §9` — no election, candidate, or admin tables found.
  - Finding: Five new tables required: `admin_accounts`, `elections`, `candidates`, `admin_action_log`, `election_events`. Index on `biometric_attempt_log.attempted_at` required. Full schema in `admin-portal-plan.md §13`.

- [x] API design identified
  - Evidence: `admin-portal-evidence.md §1 AdminController` — existing `/api/admin/biometrics/enroll` has documented security gap (line 30-31 comment: endpoint MUST be protected by admin authentication).
  - Finding: Full admin API surface designed across auth, voter administration, election management, candidate management, audit centre, and dashboard. All endpoints include required role. Full design in `admin-portal-plan.md §14`.

- [x] Frontend design identified
  - Evidence: `admin-portal-evidence.md §6 Frontend` — voter app uses `useState<1|2|3|4>` step flow, no routing library. Next.js App Router in use.
  - Finding: Admin portal as separate Next.js application (or `/admin` route group). Role-aware navigation guard using decoded admin token claim. SSE dashboard via `EventSource`. Page structure defined in `admin-portal-plan.md §15`.

- [x] Implementation roadmap created
  - Finding: Six-phase roadmap defined in `admin-portal-plan.md §17`. Phase 1: Admin auth foundation. Phase 2: Election and candidate management. Phase 3: Voter administration. Phase 4: Audit centre. Phase 5: Dashboard. Phase 6: MFA and security hardening.

- [x] Findings backed by repository evidence
  - All findings in `admin-portal-plan.md` reference specific files, classes, and line numbers documented in `admin-portal-evidence.md`.
  - Where no evidence was found, explicitly marked as `NO EVIDENCE FOUND` per evidence rule.

---

## Evidence Completeness Verification

| Evidence Area | Status |
|---|---|
| All controllers inspected | ✅ `AdminController.java`, `AuthController.java`, `VoteController.java` |
| All services inspected | ✅ All 11 service files |
| All models inspected | ✅ All 4 model files |
| All repositories inspected | ✅ All 4 repository files |
| Database migration file inspected | ✅ `database_migrations.sql` |
| Frontend page structure inspected | ✅ `page.tsx`, component listing |
| Infrastructure inspected | ✅ `docker-compose.yml`, `application.yml` |
| Utility classes inspected | ✅ `SafeLog.java` |
| Config classes inspected | ✅ `DemoDataInitializer.java` |
| Missing capabilities documented | ✅ `admin-portal-evidence.md §9` |
