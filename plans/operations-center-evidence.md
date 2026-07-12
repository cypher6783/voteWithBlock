# Operations Center — Repository Evidence

All findings are grounded in inspected source files. No speculation. No invented code.

---

## 1. Existing Incident Architecture

### ElectionIncident Entity
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/model/ElectionIncident.java`
- **Table**: `election_incidents`
- **Columns**: `id` (BIGSERIAL PK), `election_id` (UUID FK), `severity` (VARCHAR), `status` (VARCHAR), `title` (VARCHAR), `description` (TEXT), `created_by` (VARCHAR), `created_at` (TIMESTAMPTZ), `resolved_at` (TIMESTAMPTZ)
- **Evidence**: Entity uses Lombok `@Builder`, `@Data` annotations. Status values are free-form strings (not enum).

### ElectionIncidentController
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/controller/ElectionIncidentController.java`
- **Route**: `@RequestMapping("/api/admin/incidents")`
- **Endpoints**:
  - `POST /api/admin/incidents/election/{electionId}` — Create incident, `@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ELECTION_ADMIN')")`
  - `PUT /api/admin/incidents/{id}/resolve` — Resolve incident, `@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ELECTION_ADMIN')")`
  - `GET /api/admin/incidents/election/{electionId}` — List incidents, `@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ELECTION_ADMIN', 'AUDITOR')")`
- **Evidence**: Lifecycle limited to OPEN → RESOLVED. No ACKNOWLEDGED or INVESTIGATING states. No severity enum enforcement.

### ElectionIncidentRepository
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/repository/ElectionIncidentRepository.java`
- **Methods**: `findByElectionIdOrderByCreatedAtDesc(UUID electionId)`
- **Evidence**: Basic CRUD only. No filtering by severity, status, date range, or cross-election queries.

### Database Schema
- **File**: `database_migrations_v3_elections.sql` (lines 37-49)
- **Evidence**: `election_incidents` table with `severity VARCHAR(32)`, `status VARCHAR(32)` — no CHECK constraints, no enum validation at DB level.

---

## 2. Existing Dashboard Architecture

### AdminDashboardDto
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/dto/AdminDashboardDto.java`
- **Fields**: `electionMetrics` (PublicDashboardDto), `biometricMetrics` (BiometricMetricsDto), `securityMetrics` (SecurityDashboardDto), `operationalMetrics` (MonitoringDashboardDto)
- **Evidence**: Composite DTO aggregating four metric domains.

### AdminDashboardController
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/controller/AdminDashboardController.java`
- **Route**: `@RequestMapping("/api/admin")`
- **Endpoints**:
  - `GET /api/admin/dashboard` — Returns `AdminDashboardDto`
  - `GET /api/admin/metrics/operational` — Returns `MonitoringDashboardDto`
- **Evidence**: No `@PreAuthorize` annotations on endpoints (security handled by filter). Uses `DashboardCacheService` for caching.

### DashboardCacheService
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/service/DashboardCacheService.java`
- **Cache Configuration**:
  - Public metrics: 30-second TTL, max 100 entries
  - Admin metrics: 10-second TTL, max 10 entries
  - Health metrics: 5-second TTL, max 10 entries
- **Evidence**: Caffeine cache implementation. Pattern: `cache.get(key, supplier)`.

### DashboardMetricsService
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/service/DashboardMetricsService.java`
- **Counters** (AtomicLong, in-memory):
  - `successfulVerifications`, `failedVerifications`, `lockouts`, `reEnrollmentRequests`
  - `loginFailures`, `adminLoginFailures`, `rateLimitEvents`, `suspiciousActivities`
- **Evidence**: Thread-safe in-memory counters. No persistence. No historical aggregation. Counters reset on service restart.

### BiometricMetricsDto
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/dto/BiometricMetricsDto.java`
- **Fields**: `successfulVerifications`, `failedVerifications`, `lockouts`, `reEnrollmentRequests`

### SecurityDashboardDto
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/dto/SecurityDashboardDto.java`
- **Fields**: `loginFailures`, `adminLoginFailures`, `rateLimitEvents`, `suspiciousActivities`

### PublicDashboardDto
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/dto/PublicDashboardDto.java`
- **Fields**: `electionTitle`, `electionCode`, `status`, `turnoutPercentage`, `totalVotesCast`, `registeredVoters`, `countdownSeconds`

### Frontend Admin Dashboard
- **File**: `frontend ui/src/app/admin/dashboard/page.tsx`
- **Polling**: `setInterval(fetchMetrics, 10000)` — 10-second refresh
- **Displays**: Election Overview, Biometric Verification, Security Events, System Health
- **Evidence**: Uses `localStorage.getItem('adminToken')` for auth. Calls `/api/admin/dashboard`.

### Frontend Security Dashboard
- **File**: `frontend ui/src/app/admin/security/page.tsx`
- **Polling**: `setInterval(fetchMetrics, 10000)` — 10-second refresh
- **Displays**: Suspicious Activities, Rate Limit Events, Login Failures, Admin Login Failures
- **Evidence**: Calls `/api/admin/dashboard`, extracts `securityMetrics`.

### Frontend Monitoring Dashboard
- **File**: `frontend ui/src/app/admin/monitoring/page.tsx`
- **Polling**: `setInterval(fetchMetrics, 5000)` — 5-second refresh
- **Displays**: Overall System Status, Gateway/AI/DB/Fabric health cards, Blockchain Metrics
- **Evidence**: Calls `/api/admin/metrics/operational`. Uses `StatusCard` and `MetricCard` components.

### Frontend Incidents Dashboard
- **File**: `frontend ui/src/app/admin/incidents/page.tsx`
- **Polling**: Single fetch on mount (no interval)
- **Displays**: Incident table with Severity, Status, Title, Reported By, Actions
- **Evidence**: Calls `/api/admin/incidents/election/{electionId}`. Uses placeholder `activeElectionId`. Has "Report Incident" button (not wired).

---

## 3. Existing Audit Architecture

### AdminActionLog Entity
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/model/AdminActionLog.java`
- **Table**: `admin_action_log`
- **Columns**: `id` (BIGSERIAL PK), `admin_username_hash` (VARCHAR), `action` (VARCHAR), `target_entity_type` (VARCHAR), `target_entity_id_hash` (VARCHAR), `outcome` (VARCHAR), `ip_hash` (VARCHAR), `performed_at` (TIMESTAMPTZ), `metadata` (TEXT)
- **Evidence**: All identifiers are SHA-256 hashed via `HashUtil.sha256Hex()`. Privacy-preserving design.

### AdminActionLogRepository
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/repository/AdminActionLogRepository.java`
- **Methods**: Only `JpaRepository` defaults (findAll, findById, save, delete)
- **Evidence**: No custom query methods. No filtering, pagination, or search capabilities.

### Audit Actions Logged
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/service/ElectionService.java`
- **Evidence**: `logAction()` method records: `CREATE_ELECTION`, `UPDATE_ELECTION`, `CHANGE_STATUS_OPEN`, `CHANGE_STATUS_CLOSED`, `CHANGE_STATUS_SCHEDULED`
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/controller/AdminAuthController.java`
- **Evidence**: Logs `ADMIN_LOGIN_SUCCESS`, `ADMIN_LOGIN_FAIL`, `ADMIN_LOGOUT`
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/config/AdminBootstrapRunner.java`
- **Evidence**: Logs `BOOTSTRAP_ADMIN_CREATED`

### BiometricAttemptLog Entity
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/model/BiometricAttemptLog.java`
- **Table**: `biometric_attempt_log`
- **Columns**: `id`, `national_id_hash`, `attempt_type` (ENROLLMENT/VERIFICATION), `outcome` (PASS/FAIL_*), `similarity_score`, `threshold_used`, `ip_hash`, `attempted_at`, `model_version`
- **Evidence**: Privacy-preserving hashes. Detailed biometric audit trail.

### BiometricAttemptLogRepository
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/repository/BiometricAttemptLogRepository.java`
- **Methods**: Only `JpaRepository` defaults
- **Evidence**: No custom query methods. No aggregation or analytics queries.

### SafeLog Utility
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/util/SafeLog.java`
- **Method**: `SafeLog.id(String value)` — masks IDs to format `"US***-A"`
- **Evidence**: Used across AuthController, VoteController, BiometricVerificationService, VotingLedgerService.

---

## 4. Existing Monitoring Architecture

### SystemHealthService
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/service/SystemHealthService.java`
- **Returns**: `MonitoringDashboardDto` with component health statuses
- **Evidence**: Currently returns hardcoded `HEALTHY` status for all components. Uses mock `Random` values for blockchain metrics. No actual health checks implemented.

### MonitoringDashboardDto
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/dto/MonitoringDashboardDto.java`
- **Fields**: `overallStatus` (HEALTHY/DEGRADED/CRITICAL), `gatewayHealth`, `aiServiceHealth`, `databaseHealth`, `fabricHealth`, `blockchainMetrics`
- **Nested DTOs**: `ComponentHealth` (status, details), `BlockchainMetrics` (lastSuccessfulTransaction, lastFailedTransaction, averageSubmissionLatencyMs, averageQueryLatencyMs)

### DashboardAlertService
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/service/DashboardAlertService.java`
- **Active Alerts**: `CopyOnWriteArrayList<Alert>` (in-memory)
- **Alert Sources**:
  - Fabric disconnected → CRITICAL
  - AI Service offline → HIGH
  - Database degraded → CRITICAL
  - Critical incident opened → CRITICAL
  - Biometric failures spiking → HIGH
- **Evidence**: Alerts are ephemeral (in-memory only). No persistence. No acknowledgement/resolution workflow. Alert record: `Alert(String severity, String message)`.

### ElectionResultsCacheService
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/service/ElectionResultsCacheService.java`
- **Cache**: Caffeine, 10-second TTL, max 100 entries
- **Evidence**: Fetches from Fabric via `VotingLedgerService`. Currently returns mock JSON payload.

### VotingLedgerService
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/service/VotingLedgerService.java`
- **Connection**: Hyperledger Fabric `Gateway`, `Network`, `Contract`
- **Evidence**: `@PostConstruct initializeFabricConnection()` — reads `network-config.json`. Falls back to sandbox mode when Fabric offline. Logs connection failures via `log.warn()`.

### Frontend Monitoring Page
- **File**: `frontend ui/src/app/admin/monitoring/page.tsx`
- **Components**: `StatusCard`, `MetricCard`
- **Evidence**: Displays 4 service health cards (Gateway, AI, DB, Fabric) and 4 blockchain metrics.

---

## 5. Existing Security Architecture

### SecurityConfig
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/config/SecurityConfig.java`
- **Policy**: Stateless sessions, CSRF disabled
- **Public routes**: `/api/auth/**`, `/api/vote/**`, `/actuator/**`, `/api/admin/auth/login`
- **Protected routes**: All `/api/admin/**` require authentication
- **Evidence**: `AdminAuthFilter` added before `UsernamePasswordAuthenticationFilter`.

### AdminAuthFilter
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/config/AdminAuthFilter.java`
- **Logic**: Extracts Bearer token → validates via `AdminTokenService` → checks session not revoked → sets `ROLE_{role}` authority
- **Evidence**: Skips `/api/admin/auth/login`. Checks `adminSessionRepository.existsByJtiAndRevokedFalse(jti)`.

### AdminTokenService
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/service/AdminTokenService.java`
- **Algorithm**: HMAC-SHA256, Base64Url encoding
- **Claims**: `sub` (username), `exp` (epoch), `jti` (UUID), `role`, `type` ("admin-session")
- **TTL**: Configurable via `app.admin.token-ttl-seconds` (default 28800 = 8 hours)
- **Validation**: Constant-time comparison via `MessageDigest.isEqual()`

### AdminRole Enum
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/model/AdminRole.java`
- **Values**: `SUPER_ADMIN`, `ELECTION_ADMIN`, `AUDITOR`

### AdminAccount Entity
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/model/AdminAccount.java`
- **Table**: `admin_accounts`
- **Fields**: `username` (PK), `fullName`, `email`, `role` (AdminRole enum), `passwordSalt`, `passwordHash`, `mfaSecret`, `mfaEnabled`, `isActive`, `failedLoginAttempts`, `lastFailedLoginAt`, `createdAt`, `lastLoginAt`, `createdBy`

### AdminSession Entity
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/model/AdminSession.java`
- **Table**: `admin_sessions`
- **Fields**: `jti` (PK), `username`, `sessionType`, `issuedAt`, `expiresAt`, `revoked`

### RateLimitService
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/service/RateLimitService.java`
- **Implementation**: In-memory `ConcurrentHashMap<String, Deque<Long>>` sliding window
- **Config**: `app.rate-limit.max-attempts` (default 12), `app.rate-limit.window-seconds` (default 60)
- **Evidence**: Used for voter login, biometric verification, vote casting, and admin login.

### BiometricVerificationService
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/service/BiometricVerificationService.java`
- **Lockout**: 5 failed attempts → 15-minute lockout
- **Thresholds**: similarity >= 0.82 → PASS, 0.75-0.82 → PENDING_REENROLLMENT, < 0.75 → FAIL_SIMILARITY
- **Result enum**: PASS, FAIL_LIVENESS, FAIL_SIMILARITY, FAIL_LOCKED, FAIL_NOT_ENROLLED, FAIL_PENDING_REENROLLMENT, FAIL_DISABLED, FAIL_ERROR

---

## 6. Existing Authentication Architecture

### Voter Authentication Flow
1. `POST /api/auth/login` → PreAuthToken (password + optional bootstrap)
2. `POST /api/auth/biometric-challenge` → Challenge UUID
3. `POST /api/auth/biometric-confirm` → SessionToken (with `bio: true` claim)

### Admin Authentication Flow
1. `POST /api/admin/auth/login` → AdminToken (HMAC-SHA256 signed)
2. Session tracked in `admin_sessions` table (JTI-based revocation)

### Token Services
- **PreAuthTokenService**: HMAC-SHA256, TTL configurable, claims: `sub`, `exp`, `jti`, `stage`
- **SessionTokenService**: HMAC-SHA256, TTL 900s default, claims: `sub`, `exp`, `jti`, `bio`
- **AdminTokenService**: HMAC-SHA256, TTL 28800s default, claims: `sub`, `exp`, `jti`, `role`, `type`

---

## 7. Existing Blockchain Architecture

### VotingLedgerService
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/service/VotingLedgerService.java`
- **Chaincode function**: `CastVote(token, electionId, candidateId)`
- **Fallback**: Sandbox mode when Fabric offline (`sandbox-tx-{hash}`)
- **Test override**: `force-blockchain-failure-id` triggers simulated failure

### ElectionResultsCacheService
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/service/ElectionResultsCacheService.java`
- **Cache**: 10-second TTL to prevent Fabric spam
- **Evidence**: Returns mock JSON. No `GetElectionTally` chaincode method implemented yet.

---

## 8. Existing Frontend Architecture

### Framework
- **File**: `frontend ui/src/app/page.tsx`
- **Evidence**: Next.js App Router with `"use client"` directive. Step-based flow (steps 1-4).

### Admin Pages
- `/admin/dashboard/page.tsx` — Admin Dashboard (metrics overview)
- `/admin/security/page.tsx` — Security Monitoring
- `/admin/monitoring/page.tsx` — System Monitoring
- `/admin/incidents/page.tsx` — Incident Management
- `/admin/elections/page.tsx` — Elections Management
- `/admin/candidates/page.tsx` — Candidate Management

### API Constants
- **File**: `frontend ui/src/lib/api.ts`
- **Endpoints**: `AUTH_LOGIN_URL`, `AUTH_BIOMETRIC_CHALLENGE_URL`, `AUTH_BIOMETRIC_CONFIRM_URL`, `VOTE_CAST_URL`

### Authentication Pattern
- Token stored in `localStorage.getItem('adminToken')` or `localStorage.getItem('adminSession')`
- Passed as `Authorization: Bearer {token}` header

---

## 9. Existing Infrastructure

### Docker Compose
- **File**: `docker-compose.yml`
- **Services**: `ai-service` (port 8000), `core-gateway` (port 8080), `postgres-db` (port 5433:5432), `frontend-ui` (port 3000)
- **Networks**: `voting-network` (bridge), `fabric_test` (external)
- **Evidence**: No health checks defined. No monitoring service. No Redis/cache service.

### Database
- **PostgreSQL 15 Alpine**
- **Tables**: `voter_accounts`, `voter_biometrics`, `biometric_challenges`, `biometric_attempt_log`, `admin_accounts`, `admin_sessions`, `admin_action_log`, `elections`, `candidates`, `election_incidents`

---

## 10. What Does NOT Exist (No Evidence Found)

| Capability | Evidence Status |
|---|---|
| Incident lifecycle states (ACKNOWLEDGED, INVESTIGATING) | NO EVIDENCE FOUND |
| IncidentStatus enum | NO EVIDENCE FOUND |
| IncidentSeverity enum | NO EVIDENCE FOUND |
| Incident assignment/ownership tracking | NO EVIDENCE FOUND |
| incident_assignment_history table | NO EVIDENCE FOUND |
| Incident comments/notes | NO EVIDENCE FOUND |
| Alert persistence/alert table | NO EVIDENCE FOUND |
| AlertSeverity enum | NO EVIDENCE FOUND |
| AlertStatus enum | NO EVIDENCE FOUND |
| Alert acknowledgement/resolution workflow | NO EVIDENCE FOUND |
| Alert correlation (parentAlertId) | NO EVIDENCE FOUND |
| OperationsMetricsService (unified metrics) | NO EVIDENCE FOUND |
| OperationsAlertService (formalized alerts) | NO EVIDENCE FOUND |
| SecurityEventType enum | NO EVIDENCE FOUND |
| Audit log query/search/pagination API | NO EVIDENCE FOUND |
| Audit log filtering (by action, date, admin) | NO EVIDENCE FOUND |
| Audit export integrity (SHA-256) | NO EVIDENCE FOUND |
| Historical metrics aggregation | NO EVIDENCE FOUND |
| Actual health checks (all mocked) | NO EVIDENCE FOUND |
| Service health consecutive failure thresholds | NO EVIDENCE FOUND |
| Election operations view (velocity, timeline) | NO EVIDENCE FOUND |
| Operational metrics persistence | NO EVIDENCE FOUND |
| Data retention policies | NO EVIDENCE FOUND |
| Election Day metrics permanent preservation | NO EVIDENCE FOUND |
| `/admin/operations/*` frontend routes | NO EVIDENCE FOUND |
| Role-based frontend access control | NO EVIDENCE FOUND |
| No-delete policy enforcement | NO EVIDENCE FOUND |
