# Admin Portal — Repository Evidence

All findings are grounded in inspected source files. No speculation. No invented code.

---

## 1. Existing Controllers

### AdminController
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/controller/AdminController.java`
- **Class**: `AdminController`
- **Route**: `@RequestMapping("/api/admin/biometrics")`
- **Evidence**: Single endpoint `POST /api/admin/biometrics/enroll` backed by `BiometricEnrollmentService.enrollVoter()`
- **Security Gap (documented in source)**: Line 30-31: `// In a real application, this endpoint MUST be protected by admin authentication. For this milestone, we leave it open to localhost or simple auth for the admin portal.`
- **No authentication guard exists on this endpoint.**

### AuthController
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/controller/AuthController.java`
- **Class**: `AuthController`
- **Route**: `@RequestMapping("/api/auth")`
- **Endpoints**:
  - `POST /api/auth/login` — Password-based step 1 authentication
  - `POST /api/auth/biometric-challenge` — Issues UUID challenge, guarded by `preAuthTokenService.requirePreAuthSubject()`
  - `POST /api/auth/biometric-confirm` — Validates challenge + face image, guarded by `preAuthTokenService.requirePreAuthSubject()`
- **Token Issuance**: `preAuthTokenService.issue(voter.getNationalId())` on successful login
- **biometricEnrolled field**: Line 90: `!\"NOT_ENROLLED\".equals(voter.getBiometricStatus())`

### VoteController
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/controller/VoteController.java`
- **Class**: `VoteController`
- **Route**: `@RequestMapping("/api/vote")`
- **Endpoint**: `POST /api/vote/cast`
- **Guards**:
  - `rateLimitService.requireAllowed("vote:...")`
  - `sessionTokenService.requireBiometricClaim(authorization)` — enforces `"bio": true` claim
  - Session nationalId must match request nationalId (line 67)
  - `inputValidationService.requireValidBallot()` — validates electionId, candidateId against allowlists
- **Replay Protection**: Line 82-85: `voter.isHasVoted()` check before blockchain submission
- **Rollback pattern**: Lines 112-118: DB `hasVoted` reverted to false on blockchain failure

---

## 2. Existing Services

### PasswordService
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/service/PasswordService.java`
- **Algorithm**: `PBKDF2WithHmacSHA256`, 120,000 iterations, 256-bit key
- **Salt**: 16-byte `SecureRandom`, Base64-encoded
- **Methods**: `newSalt()`, `hashPassword(password, salt)`, `matches(password, salt, expectedHash)`
- **Constant-time comparison**: `MessageDigest.isEqual()` used in `matches()`

### PreAuthTokenService
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/service/PreAuthTokenService.java`
- **Algorithm**: HMAC-SHA256, signed with `app.auth.session-secret`
- **TTL**: `app.biometric.pre-auth-ttl-seconds` (default 120 seconds)
- **Claims**: `sub` (nationalId), `exp` (epoch), `jti` (UUID), `stage` = `"pre-auth"`
- **Validation**: Constant-time HMAC comparison, stage claim enforcement, expiry check
- **Reuse potential**: The signing infrastructure (HMAC-SHA256 + payload pattern) is directly reusable for admin tokens with different claims.

### SessionTokenService
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/service/SessionTokenService.java`
- **Algorithm**: HMAC-SHA256, signed with `app.auth.session-secret`
- **TTL**: `app.auth.session-ttl-seconds` (default 900 seconds / 15 minutes)
- **Claims**: `sub` (nationalId), `exp`, `jti`, `bio` = `true`
- **Methods**:
  - `requireSubject()` — validates token, returns subject, no bio claim required
  - `requireBiometricClaim()` — validates token AND enforces `bio: true`
- **Secret requirement**: Line 28-30: minimum 32-character secret enforced at startup

### RateLimitService
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/service/RateLimitService.java`
- **Implementation**: In-memory `ConcurrentHashMap<String, Deque<Long>>` sliding window
- **Config**: `app.rate-limit.max-attempts` (default 12), `app.rate-limit.window-seconds` (default 60)
- **Key pattern**: Arbitrary string key — reusable for admin rate limiting

### BiometricVerificationService
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/service/BiometricVerificationService.java`
- **Lockout logic**: Line 39-48: 5 failed attempts → 15-minute lockout using `lastFailedBiometricAt`
- **Status checks**: `"NOT_ENROLLED"` → `FAIL_NOT_ENROLLED`; `"DISABLED"` → `FAIL_DISABLED`
- **Similarity threshold**: `app.biometric.similarity-threshold` (default 0.82)
- **Soft threshold**: similarity >= 0.75 and < 0.82 → `PENDING_REENROLLMENT` (line 86-91)
- **Auto-enrollment**: First scan with no stored embedding auto-enrolls (added during current session)
- **Result enum**: `PASS`, `FAIL_LIVENESS`, `FAIL_SIMILARITY`, `FAIL_LOCKED`, `FAIL_NOT_ENROLLED`, `FAIL_PENDING_REENROLLMENT`, `FAIL_DISABLED`, `FAIL_ERROR`

### BiometricEnrollmentService
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/service/BiometricEnrollmentService.java`
- **Flow**: Calls `aiClient.requestEnrollment()` → encrypts embedding → saves `VoterBiometric` → sets `biometricStatus = "ACTIVE"`
- **Result enum**: `SUCCESS`, `FAIL_NOT_FOUND`, `FAIL_QUALITY`, `FAIL_ERROR`

### FaceVerificationService
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/service/FaceVerificationService.java`
- **HTTP client**: `RestTemplate` (synchronous)
- **Endpoints called**:
  - `POST /enroll` — enrollment
  - `POST /verify-identity` — verification
  - `POST /verify-liveness` — liveness only
- **Base URL config**: `services.python-ai.url` (default `http://localhost:8000`)
- **Response DTO** `BiometricAiResponse`: fields `liveness_passed`, `embedding` (List<Float>), `model_version`, `face_count`, `confidence`, `message`, `verified` (fallback)

### EmbeddingEncryptionService
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/service/EmbeddingEncryptionService.java`
- **Algorithm**: AES-256-GCM, 12-byte IV, 128-bit GCM tag
- **Key**: `app.biometric.encryption-key` (Base64, must be 32 bytes); fallback to ASCII string in dev
- **Methods**: `encrypt(float[])` → `EncryptedEmbedding(ciphertext, iv, tag)`; `decrypt(ciphertext, iv, tag)` → `float[]`

### InputValidationService
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/service/InputValidationService.java`
- **Election allowlist**: `app.validation.allowed-election-ids` (CSV, default `elect-2026`)
- **Candidate allowlist**: `app.validation.allowed-candidate-ids` (CSV, default `cand-1,cand-2,cand-3,cand-4`)
- **Ballot token pattern**: `^[A-Za-z0-9._:-]{12,128}$`
- **Note**: Election and candidate IDs are static config — no database-driven election management exists.

### VotingLedgerService
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/service/VotingLedgerService.java`
- **Fabric client**: `org.hyperledger.fabric.client.Contract`, `Gateway`, `Network`
- **Config**: `services.fabric.channel-name`, `services.fabric.chaincode-name`
- **Sandbox fallback**: When `contract == null`, returns `"sandbox-tx-{hash}"` (line 70-71)
- **Test override**: `"force-blockchain-failure-id"` triggers simulated failure (line 59-61)
- **Chaincode function called**: `"CastVote"` with args `(token, electionId, candidateId)`

### BiometricChallengeService
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/service/BiometricChallengeService.java`
- **Challenge TTL**: `app.biometric.challenge-ttl-seconds` (default 90 seconds)
- **Storage**: `BiometricChallengeRepository` (JPA, `biometric_challenges` table)

---

## 3. Existing Database Entities

### voter_accounts table
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/model/VoterAccount.java`
- **Table**: `voter_accounts`
- **Columns**: `national_id` (PK, String), `full_name`, `has_voted` (boolean), `face_embedding_reference` (String, legacy/unused), `password_salt`, `password_hash`, `biometric_status` (default `"NOT_ENROLLED"`), `failed_biometric_attempts` (int, default 0), `last_failed_biometric_at` (OffsetDateTime)
- **biometricStatus values in use**: `"NOT_ENROLLED"`, `"ENROLLED"`, `"ACTIVE"`, `"DISABLED"`, `"PENDING_REENROLLMENT"`

### voter_biometrics table
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/model/VoterBiometric.java`
- **Table**: `voter_biometrics`
- **Columns**: `id` (BIGSERIAL PK), `national_id` (UNIQUE, FK to voter_accounts), `embedding_ciphertext` (BYTEA), `embedding_iv` (BYTEA), `embedding_tag` (BYTEA), `model_version` (VARCHAR), `enrolled_at` (TIMESTAMPTZ), `enrollment_ip_hash` (VARCHAR), `last_verified_at` (TIMESTAMPTZ), `version` (INT, optimistic lock)

### biometric_challenges table
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/model/BiometricChallenge.java`
- **Table**: `biometric_challenges`
- **Columns**: `id` (BIGSERIAL PK), `challenge_id` (UUID UNIQUE), `national_id` (VARCHAR), `expires_at` (TIMESTAMPTZ), `used` (BOOLEAN, default false)

### biometric_attempt_log table
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/model/BiometricAttemptLog.java`
- **Table**: `biometric_attempt_log`
- **Columns**: `id` (BIGSERIAL PK), `national_id_hash` (VARCHAR — hashed, not raw), `attempt_type` (VARCHAR: `"ENROLLMENT"` or `"VERIFICATION"`), `outcome` (VARCHAR: `"PASS"`, `"FAIL_LIVENESS"`, `"FAIL_SIMILARITY"`, `"FAIL_QUALITY"`, `"FAIL_LOCKED"`), `similarity_score` (FLOAT), `threshold_used` (FLOAT), `ip_hash` (VARCHAR — hashed), `attempted_at` (TIMESTAMPTZ), `model_version` (VARCHAR)
- **Privacy note**: Both `nationalIdHash` and `ipHash` are stored as hex of `Object.hashCode()` — not cryptographically secure hash

### biometric_attempt_log — no query methods defined
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/repository/BiometricAttemptLogRepository.java`
- **Evidence**: `extends JpaRepository<BiometricAttemptLog, Long>` — no custom query methods. Only basic CRUD available.

---

## 4. Existing Authentication Flow

### Step 1 — Password Auth
- **File**: `AuthController.java`, method `login()`, line 64
- `POST /api/auth/login` → validates password via `PasswordService.matches()` → issues `preAuthToken` via `PreAuthTokenService.issue()`
- Response: `LoginResponse { nationalId, preAuthToken, preAuthExpiresInSeconds, biometricEnrolled }`

### Step 2 — Biometric Challenge
- `POST /api/auth/biometric-challenge` → guarded by `preAuthTokenService.requirePreAuthSubject(authHeader)` → returns `challengeId` (UUID) + `expiresAt`

### Step 3 — Biometric Confirm
- `POST /api/auth/biometric-confirm` → validates challenge + calls `biometricVerificationService.verifyIdentity()` → on `PASS`, issues `sessionToken` via `SessionTokenService.issue()`
- Session token payload: `{ sub, exp, jti, bio: true }`

### Bootstrap Password
- **File**: `AuthController.java`, lines 73-82
- `app.auth.allow-bootstrap-password` + `app.auth.bootstrap-password` allow first-time password enrollment for a voter with no stored hash.

---

## 5. Existing Audit Capabilities

### BiometricAttemptLog
- **Enrollment events**: `attemptType = "ENROLLMENT"`, outcomes: `"PASS"`, `"FAIL_QUALITY"`, `"FAIL_ERROR"`
- **Verification events**: `attemptType = "VERIFICATION"`, outcomes: `"PASS"`, `"FAIL_LIVENESS"`, `"FAIL_SIMILARITY"`, `"FAIL_LOCKED"`
- **Similarity score stored** on verification attempts
- **Model version stored** on all attempts

### SafeLog utility
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/util/SafeLog.java`
- **Method**: `SafeLog.id(String value)` — masks IDs to format `"US***-A"` (first 3 + `***` + last 2)
- Used in: `AuthController`, `VoteController`, `BiometricVerificationService`, `VotingLedgerService`

### Application Logs (SLF4J)
- Login success/failure logged via `@Slf4j` in `AuthController`
- Vote submission success/failure logged in `VoteController`
- Biometric enroll/verify outcomes logged in `BiometricEnrollmentService`, `BiometricVerificationService`
- **No structured log sink or log aggregation configured in repository**

---

## 6. Existing Frontend Structure

### Framework
- **File**: `frontend ui/src/app/page.tsx`
- Next.js App Router (`"use client"` directive, file-system routing)
- Single-page flow: step state managed in `page.tsx` via `useState<1|2|3|4>`

### Components
- **File**: `frontend ui/src/components/`
- `LoginScreen.tsx` — Step 1: national ID + password form, calls `POST /api/auth/login`
- `BiometricScreen.tsx` — Step 2: face-api.js blink detection + `POST /api/auth/biometric-confirm`
- `BallotScreen.tsx` — Step 3: candidate selection + `POST /api/vote/cast`
- `AuditScreen.tsx` — Step 4: post-vote confirmation with transaction ID

### No routing library
- **File**: `frontend ui/src/app/page.tsx` line 11
- Navigation is handled by `currentStep` state: `useState<1|2|3|4>(1)`. No Next.js `router.push()` or `react-router` used.
- **Implication**: Admin portal requires either a separate Next.js route (`/admin`) or a separate application.

### API constants
- **File**: `frontend ui/src/lib/api.ts` (file exists in `lib/` directory, not directly inspected due to directory-only listing, but imported in LoginScreen.tsx as `AUTH_LOGIN_URL` and BiometricScreen.tsx as `AUTH_BIOMETRIC_CHALLENGE_URL`, `AUTH_BIOMETRIC_CONFIRM_URL`)

---

## 7. Infrastructure

### Docker Compose services
- **File**: `docker-compose.yml`
- Services: `ai-service` (port 8000), `core-gateway` (port 8080), `postgres-db` (port 5433:5432), `frontend-ui` (port 3000)
- Network: `votewithblock_voting-network` (bridge)
- `core-gateway` env: `SERVICES_PYTHON_AI_URL=http://ai-service:8000/verify-liveness`, `APP_CORS_ALLOWED_ORIGINS`, `APP_AUTH_SESSION_SECRET`, `APP_DEMO_SEED_VOTER`, `APP_DEMO_PASSWORD`
- **No admin service defined in docker-compose.yml**

### Application config
- **File**: `Core Gateway/src/main/resources/application.yml`
- CORS: `app.cors.allowed-origins` (default `http://localhost:3000`)
- Rate limit: `app.rate-limit.max-attempts` = 12, `app.rate-limit.window-seconds` = 60
- Biometric: `similarity-threshold` = 0.82, `challenge-ttl-seconds` = 90, `pre-auth-ttl-seconds` = 120
- Session: `session-ttl-seconds` = 900
- Validation: `allowed-election-ids` = `elect-2026`, `allowed-candidate-ids` = `cand-1,cand-2,cand-3,cand-4` (static config, not DB-driven)

### No existing admin role or permission model
- Searched all Java source files — **NO EVIDENCE FOUND** of any `Role`, `Permission`, `AdminAccount`, or Spring Security configuration.

---

## 8. Database Migration File

- **File**: `database_migrations.sql`
- Contains only biometric-related migrations (voter_accounts columns, voter_biometrics, biometric_challenges, biometric_attempt_log)
- **No election table, no candidate table, no admin account table exists in the schema.**

---

## 9. What Does NOT Exist (No Evidence Found)

| Capability | Evidence Status |
|---|---|
| Admin account entity | NO EVIDENCE FOUND |
| Role / Permission model | NO EVIDENCE FOUND |
| Spring Security configuration | NO EVIDENCE FOUND |
| Election entity / table | NO EVIDENCE FOUND |
| Candidate entity / table | NO EVIDENCE FOUND |
| Admin authentication endpoint | NO EVIDENCE FOUND |
| Admin session token separate from voter token | NO EVIDENCE FOUND |
| Audit log query API | NO EVIDENCE FOUND |
| WebSocket / SSE endpoint | NO EVIDENCE FOUND |
| Admin frontend application | NO EVIDENCE FOUND |
| MFA for any user type | NO EVIDENCE FOUND |
| Vote count / turnout query | NO EVIDENCE FOUND |