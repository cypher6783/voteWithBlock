# Admin Portal — Implementation Plan
# Votetrust Ledger

---

## 1. Executive Summary

This plan designs a production-grade Administration Portal for Votetrust Ledger. All design decisions are grounded in the existing repository structure. The portal introduces: admin account management, role-based access control, election lifecycle management, candidate management, voter administration, a structured audit centre, incident management, and a real-time election dashboard.

The existing `Core Gateway` (Spring Boot) provides a reusable signing infrastructure, password hashing, rate limiting, and biometric audit logging. It will be secured using **Spring Security** to provide robust role-based access control. The existing `frontend ui` (Next.js) will be extended to include admin interfaces via a dedicated `/admin/*` route group, leveraging shared UI components and API utilities while maintaining a single deployment boundary.

---

## 2. Current Repository Capabilities

### What Exists (backed by `admin-portal-evidence.md`)

| Capability | File | Reusable |
|---|---|---|
| PBKDF2 password hashing (120k iterations) | `PasswordService.java` | ✅ Directly |
| HMAC-SHA256 token signing | `SessionTokenService.java`, `PreAuthTokenService.java` | ✅ Pattern reuse |
| In-memory sliding-window rate limiting | `RateLimitService.java` | ✅ Directly |
| AES-256-GCM embedding encryption | `EmbeddingEncryptionService.java` | ✅ Pattern reuse |
| Biometric attempt audit log | `BiometricAttemptLog.java`, `biometric_attempt_log` table | ✅ Extend (Fix Hashing) |
| SafeLog ID masking utility | `SafeLog.java` | ✅ Directly |
| Voter account entity | `VoterAccount.java`, `voter_accounts` table | ✅ Extend |
| Voter biometric entity | `VoterBiometric.java`, `voter_biometrics` table | ✅ Read-only admin |
| Biometric enroll endpoint | `AdminController.java` `/api/admin/biometrics/enroll` | ✅ Secure and extend |
| Biometric enrollment service | `BiometricEnrollmentService.java` | ✅ Directly |
| Face AI client | `FaceVerificationService.java` | ✅ Directly |
| Blockchain ledger client | `VotingLedgerService.java` | ✅ Query extension |

### What Does Not Exist (from `admin-portal-evidence.md`)

- No admin account entity or table
- No role or permission model
- No Spring Security configuration
- No election or candidate entity
- No admin authentication endpoint
- No audit log query API
- No WebSocket/SSE endpoint
- No vote count or turnout query
- No admin frontend pages
- No incident management

---

## 3. Required New Components

### Backend (Core Gateway extensions)
- **Security & Auth**: `spring-boot-starter-security` integration, `SecurityFilterChain` for `/api/admin/**`, `AdminRole` enum, `AdminAccount` entity, `admin_sessions` table (for revocation), `AdminTokenService`, `AdminAuthController`
- **Election Management**: `Election` entity, `ElectionController`, freeze protection logic
- **Candidate Management**: `Candidate` entity, `CandidateController`, `CandidateImageStorageService`
- **Voter Admin**: `VoterAdminController`
- **Audit & Metrics**: SHA-256 hashing utility, `AdminActionLog` entity, `AuditLogController`, `ElectionMetricsService` (database layer), `DashboardController` (SSE)
- **Incidents**: `ElectionIncident` entity, `election_incidents` table, `IncidentController`

### Frontend (Extended Next.js App)
- Add `/app/admin/(login, dashboard, elections, voters, audit, incidents)` routes within the existing Next.js application.
- Role-aware navigation guard.

---

## 4. Role-Based Access Control Architecture

### 4.1 Role Definitions (Enum)

Define `AdminRole` enum to avoid magic strings. Stored as `@Enumerated(EnumType.STRING)`.

| Role | Description |
|---|---|
| `SUPER_ADMIN` | Full control — elections, users, admins, audit |
| `ELECTION_ADMIN` | Monitor and manage active elections and candidates |
| `AUDITOR` | Read-only — logs, reports, blockchain data |

### 4.2 Spring Security Integration

- Add `spring-boot-starter-security`.
- Configure `SecurityFilterChain`:
  - `/api/admin/auth/login` → Permit All
  - `/api/admin/**` → Requires authenticated Admin Session token, enforce role-based access using `@PreAuthorize("hasRole('SUPER_ADMIN')")` or ant matchers.
- Roles dictate permissions via server-side policy. The token payload only contains the role, not explicit permissions.

---

## 5. Admin Authentication & Sessions

### 5.1 Admin Entities

New tables:
- `admin_accounts`: `username` (PK), `full_name`, `email`, `role` (AdminRole), `password_salt`, `password_hash`, `mfa_secret`, `mfa_enabled`, `is_active`
- `admin_sessions`: `jti` (PK), `username`, `issued_at`, `expires_at`, `revoked` (boolean)

### 5.2 Session Revocation

Admin sessions are tracked in `admin_sessions` to allow revocation.
- On login, a new session record is created.
- On logout, or manual admin action, the session's `revoked` flag is set to true.
- The authentication filter checks the database (with potential caching) to ensure the session `jti` is not revoked.

### 5.3 Token Payload

Tokens issued by `AdminTokenService` will contain:
```json
{
  "sub": "<adminUsername>",
  "role": "SUPER_ADMIN",
  "exp": <epoch>,
  "jti": "<uuid>",
  "type": "admin-session"
}
```

---

## 6. Audit & Hashing

### 6.1 SHA-256 Audit Hashing Fix (High Priority)
- The current implementation uses `Object.hashCode()` for `nationalIdHash` and `ipHash` in `biometric_attempt_log`. This is insecure.
- **Fix**: Implement a cryptographically secure hash using `MessageDigest` (SHA-256) for all log anonymization.

### 6.2 Admin Action Log
- Immutable audit trail `admin_action_log` table storing username hash, action, target entity hash, outcome, IP hash (SHA-256), and timestamp.

---

## 7. Election & Candidate Management

### 7.1 Election Lifecycle & Freeze Protection
- Statuses: `DRAFT`, `SCHEDULED`, `OPEN`, `CLOSED`, `ARCHIVED`.
- **Freeze Protection**: When an election's status transitions to `OPEN`, backend logic strictly prohibits candidate creation, candidate deletion, or election modification. Only emergency closure is permitted.

### 7.2 Election Results Source
- The Hyperledger Fabric blockchain is the immutable source of truth for vote tallies.
- The `voter_accounts.has_voted` flag is solely for replay protection.
- `GET /api/admin/elections/{id}/results` MUST query the blockchain via `VotingLedgerService` to aggregate the final tally. No conflicting tallies will be stored in the relational database.

### 7.3 Candidate Management & Storage
- `candidates` table tracks candidate metadata.
- **Candidate Images**: Implement a `CandidateImageStorageService` with interchangeable backend providers (`LocalStorageProvider` for development, `S3StorageProvider` for production). Do not hardcode disk paths.

---

## 8. Dashboard Metrics & Incident Management

### 8.1 ElectionMetricsService
- A dedicated repository layer service (`ElectionMetricsService`) handles aggregated database metrics (turnout, registered voters, votes cast, biometric failures). Controllers do not perform ad-hoc `COUNT(*)` operations.

### 8.2 Incident Management
- New entity `ElectionIncident` (`election_incidents` table) to track real-time operational issues during an election (e.g., "Biometric outage", "Fabric outage", "Suspicious activity").
- APIs: Create Incident, Update Incident, Resolve Incident.
- Incidents are surfaced on the real-time SSE dashboard.

---

## 9. Prioritized Implementation Roadmap

### Phase 1 — Foundation & Security (Critical Prerequisites)
1. Add `spring-boot-starter-security` dependency and configure `SecurityFilterChain`.
2. Create `AdminRole` enum.
3. Create `admin_accounts` and `admin_sessions` tables.
4. Implement `AdminTokenService` and `AdminAuthController` (Login/Logout with revocation).
5. Fix `biometric_attempt_log` hashing (replace `Object.hashCode()` with SHA-256).
6. Protect existing `/api/admin/biometrics/enroll` endpoint.

### Phase 2 — Election & Candidate Management
1. Create `elections` table and `Election` entity with freeze protection logic.
2. Create `candidates` table and `CandidateImageStorageService`.
3. Implement `ElectionController` and `CandidateController`.
4. Refactor `InputValidationService` to query the database.

### Phase 3 — Voter Administration & Incident Management
1. Implement voter search, status override, and lock/unlock endpoints.
2. Create `election_incidents` table and `IncidentController`.

### Phase 4 — Audit & Metrics
1. Create `admin_action_log` table and apply SHA-256 hashing.
2. Implement `ElectionMetricsService` for aggregated database metrics.
3. Implement `GET /api/admin/elections/{id}/results` querying Hyperledger Fabric.

### Phase 5 — Real-Time Dashboard (SSE)
1. Implement `DashboardController` exposing snapshot and SSE stream.
2. Feed `ElectionMetricsService` and active incidents into the stream.

### Phase 6 — Frontend Integration (Next.js `/app/admin/*`)
1. Build `AdminGuard` wrapper component for route protection based on role.
2. Build login, dashboard, election management, candidate management, and incident response UI components leveraging existing styling.
3. Integrate SSE client for real-time dashboard updates.
