# Security Audit — Votetrust Ledger

**Audit Date**: June 2026
**Auditor**: MiMoCode Automated Security Review
**Scope**: Full application stack — Core Gateway (Spring Boot), Frontend (Next.js), AI Service (FastAPI/Python), Database (PostgreSQL), Scripts, Docker infrastructure

---

## Technology Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Backend | Spring Boot, Java | 3.2.4 / 17 |
| Frontend | Next.js, React | 16.2.6 / 19 |
| AI Service | FastAPI, Python | 3.10 |
| Database | PostgreSQL | 15 |
| Blockchain | Hyperledger Fabric | 1.4 SDK |
| JWT | JJWT | 0.11.5 |
| Deployment | Docker Compose | 3.8 |

---

## Executive Summary

This decentralized voting application contains **19 security findings** across 8 categories. Three are Critical severity, four are High, five are Medium, and seven are Low. The most dangerous attack chain combines shared token secrets (F2) with the bootstrap password backdoor (F7) to achieve full system compromise without any prior access.

**Overall Security Posture Score: 28 / 100**

---

## FINDINGS

### F1 — Secrets Committed to Version Control

| Field | Value |
|-------|-------|
| Severity | CRITICAL |
| CVSS | 9.8 |
| OWASP | A07:2021 |
| CWE | CWE-798 |

**Location**: `.env` (root directory, lines 1-14)

**What is exposed**:
- Database password: `asphalt6`
- JWT signing secret: `Ayf@sg9rv^jdVjFpKAJbYkmqQ6TzrQ@N`
- Admin token secret: same value as JWT secret
- Admin bootstrap username/password: `tinubu123` / `#whoareyou#`
- Demo voter password: `#julianisabella#`

**Proof of Concept**: Clone the repository and read `.env`. All credentials are plaintext.

**Impact**: Attacker gains database admin access, can forge authentication tokens, and logs in as super-admin.

**Fix**: Remove `.env` from git. Rotate every exposed credential. Use a secrets manager.

---

### F2 — Shared Cryptographic Secret Across Token Types

| Field | Value |
|-------|-------|
| Severity | CRITICAL |
| CVSS | 9.1 |
| OWASP | A07:2021 |
| CWE | CWE-327 |

**Location**: `.env:5,11` — both `APP_AUTH_SESSION_SECRET` and `APP_ADMIN_TOKEN_SECRET` equal `Ayf@sg9rv^jdVjFpKAJbYkmqQ6TzrQ@N`

**What is vulnerable**: `PreAuthTokenService`, `SessionTokenService`, and `AdminTokenService` all derive HMAC-SHA256 keys from this single shared secret. A token valid for one purpose can be reinterpreted for another.

**Proof of Concept**: Take a voter's session token. The base64url-decoded payload contains `{"sub":"VOTER_ID","exp":...,"bio":true}`. Decode, modify to `{"sub":"tinubu123","exp":9999999999,"jti":"new-uuid","role":"SUPER_ADMIN","type":"admin-session"}`, re-sign with the same secret. The `AdminAuthFilter` validates the signature and grants super-admin access.

**Impact**: Privilege escalation from any authenticated voter to full super-admin.

**Fix**: Generate separate 64+ character random secrets for each token purpose. Store them independently.

---

### F3 — Hardcoded Fallback Biometric Encryption Key

| Field | Value |
|-------|-------|
| Severity | CRITICAL |
| CVSS | 9.1 |
| OWASP | A02:2021 |
| CWE | CWE-321 |

**Location**: `Core Gateway/src/main/java/.../service/EmbeddingEncryptionService.java:26`

**What is vulnerable**: When `app.biometric.encryption-key` is blank (the default per `application.yml:51`), the constructor falls back to the hardcoded string `"12345678901234567890123456789012"` used as a raw AES-256 key.

**Proof of Concept**: Decompile the deployed JAR. The key bytes are directly visible in bytecode. Use them to decrypt any stored biometric embedding ciphertext from the `voter_biometrics` table.

**Impact**: All enrolled face embeddings can be decrypted, enabling biometric spoofing and voter impersonation.

**Fix**: Fail startup if the encryption key is not provided. Migrate existing data to a new key.

---

### F4 — Bootstrap Password Backdoor on Voter Accounts

| Field | Value |
|-------|-------|
| Severity | HIGH |
| CVSS | 8.1 |
| OWASP | A07:2021 |
| CWE | CWE-620 |

**Location**: `AuthController.java:73-81`

**What is vulnerable**: When `APP_AUTH_ALLOW_BOOTSTRAP_PASSWORD=true` (set in `.env`), any voter without an enrolled password gets their password silently set to whatever the login request provides. The `.env` sets the bootstrap password to empty string, and the code checks `bootstrapPassword.isBlank()` — meaning when the bootstrap password IS empty, the fallback to "any password works" activates for unenrolled voters.

**Proof of Concept**: If a voter's `passwordSalt` and `passwordHash` columns are NULL (unenrolled state), sending `POST /api/auth/login {"nationalId":"<target>","password":"x"}` succeeds and permanently sets that voter's password to `"x"`.

**Impact**: Account takeover of any voter who hasn't yet enrolled credentials.

**Fix**: Disable bootstrap password in production. Implement a proper enrollment flow with verification.

---

### F5 — Actuator Endpoints Publicly Accessible

| Field | Value |
|-------|-------|
| Severity | HIGH |
| CVSS | 7.5 |
| OWASP | A01:2021 |
| CWE | CWE-200 |

**Location**: `SecurityConfig.java:32` — `.requestMatchers("/actuator/**").permitAll()`

**What is vulnerable**: Spring Boot Actuator is exposed without authentication. The `application.yml` enables the `info` endpoint with `env: enabled: true`, which can leak environment properties.

**Proof of Concept**: `curl http://target:8080/actuator/info` returns system information. Depending on Spring Boot version, `GET /actuator/env` or `GET /actuator/configprops` may return all configuration including database URLs and secrets.

**Impact**: Disclosure of internal configuration, potentially including all secrets.

**Fix**: Bind actuator to a separate management port. Restrict to authenticated admin users. Disable `env` and `configprops` endpoints.

---

### F6 — Vote Endpoint Bypasses Filter Chain

| Field | Value |
|-------|-------|
| Severity | HIGH |
| CVSS | 7.3 |
| OWASP | A01:2021 |
| CWE | CWE-862 |

**Location**: `SecurityConfig.java:31` — `.requestMatchers("/api/vote/**").permitAll()`

**What is vulnerable**: All vote-related endpoints skip Spring Security entirely. While `VoteController.castVote()` manually validates the session token, any new endpoint added under `/api/vote/` inherits no authentication by default.

**Proof of Concept**: If a developer adds `POST /api/vote/admin/bypass` for any reason, it will be fully public with no protection.

**Impact**: Future endpoints added carelessly become attack vectors. The current architecture relies on developers remembering to add manual token validation.

**Fix**: Remove `/api/vote/**` from `permitAll`. Use Spring Security's `@PreAuthorize` on vote endpoints.

---

### F7 — IP Spoofing Defeats Rate Limiting

| Field | Value |
|-------|-------|
| Severity | HIGH |
| CVSS | 7.3 |
| OWASP | A05:2021 |
| CWE | CWE-348 |

**Location**: `AuthController.java:139-145`, `VoteController.java:146-152`, `AdminAuthController.java:142-148`

**What is vulnerable**: The `clientIp()` method in all three controllers trusts the `X-Forwarded-For` header without validation. Rate limit keys are constructed as `"login:" + clientIp + ":" + nationalId`.

**Proof of Concept**: Send 20 login attempts with `X-Forwarded-For: 10.0.0.1` through `10.0.0.20`. Each request gets a separate rate limit bucket, completely defeating the 12-attempt limit.

**Impact**: Brute-force attacks against voter and admin credentials become trivial.

**Fix**: Configure trusted proxy networks in Spring Boot. Only accept forwarded headers from known infrastructure.

---

### F8 — In-Memory Rate Limiting Is Stateless

| Field | Value |
|-------|-------|
| Severity | MEDIUM |
| CVSS | 6.5 |
| OWASP | A05:2021 |
| CWE | CWE-770 |

**Location**: `RateLimitService.java:17` — `ConcurrentHashMap<String, Deque<Long>>`

**What is vulnerable**: Rate limit counters live in JVM heap memory. They vanish on restart, don't share across instances, and have no upper bound on key count.

**Proof of Concept**: Restart the Core Gateway service. All accumulated rate limit counters reset to zero. Alternatively, send requests with millions of unique national IDs to cause memory exhaustion.

**Impact**: Rate limiting resets on redeployment. Distributed deployments have independent limits per instance. Memory exhaustion DoS is possible.

**Fix**: Use Redis for distributed counting. Add a maximum key count with LRU eviction.

---

### F9 — No Voter Session Revocation

| Field | Value |
|-------|-------|
| Severity | MEDIUM |
| CVSS | 6.5 |
| OWASP | A07:2021 |
| CWE | CWE-613 |

**Location**: `SessionTokenService.java` (entire class)

**What is vulnerable**: Voter session tokens are validated purely by HMAC signature and expiration timestamp. Unlike admin tokens (which use `AdminSessionRepository` to track JTI revocation), voter tokens have no server-side state. There is no voter logout endpoint.

**Proof of Concept**: Steal a voter's token via network interception. The token remains usable for its full 15-minute lifetime with no way to invalidate it server-side.

**Impact**: Compromised voter tokens cannot be revoked, allowing unauthorized vote casting.

**Fix**: Add a `VoterSessionRepository` tracking JTI. Implement `POST /api/auth/voter/logout`. Check revocation in `requireBiometricClaim`.

---

### F10 — No HTTP Security Headers

| Field | Value |
|-------|-------|
| Severity | MEDIUM |
| CVSS | 5.3 |
| OWASP | A05:2021 |
| CWE | CWE-693 |

**Location**: `SecurityConfig.java`, `next.config.ts`

**What is vulnerable**: Neither the Spring Boot backend nor the Next.js frontend sets Content-Security-Policy, X-Frame-Options, X-Content-Type-Options, Strict-Transport-Security, or Referrer-Policy headers.

**Proof of Concept**: Embed `http://target:3000` in an iframe on a malicious page. Without `X-Frame-Options: DENY`, clickjacking attacks become possible.

**Impact**: Clickjacking, MIME sniffing, and lack of transport security enforcement.

**Fix**: Add a servlet filter setting all recommended security headers. Add Next.js `headers()` configuration.

---

### F11 — No HTTPS Enforcement

| Field | Value |
|-------|-------|
| Severity | MEDIUM |
| CVSS | 5.3 |
| OWASP | A05:2021 |
| CWE | CWE-319 |

**Location**: `docker-compose.yml`, all service configurations

**What is vulnerable**: All services bind to plain HTTP. Docker Compose exposes ports `3000`, `8080`, `5433`, `8000` without TLS termination. The frontend `api.ts` defaults to `http://localhost:8080`.

**Proof of Concept**: Network sniffing captures all traffic including JWT tokens, biometric images, and database credentials in plaintext.

**Impact**: All authentication tokens, biometric data, and credentials transit in cleartext.

**Fix**: Add TLS termination via reverse proxy (nginx/traefik). Configure `Strict-Transport-Security` headers. Use HTTPS for all internal service communication.

---

### F12 — Hardcoded Credentials in Shell Scripts

| Field | Value |
|-------|-------|
| Severity | MEDIUM |
| CVSS | 5.3 |
| OWASP | A05:2021 |
| CWE | CWE-798 |

**Location**: `scripts/deploy-gates.sh:11` — `PGPASSWORD=asphalt6`

**What is vulnerable**: Database password appears in plaintext in the deployment verification script.

**Proof of Concept**: Read the script file to obtain database credentials.

**Impact**: Credential exposure to anyone with repository access.

**Fix**: Source credentials from environment variables or a secrets file excluded from version control.

---

### F13 — Insecure Secret Generation Script

| Field | Value |
|-------|-------|
| Severity | LOW |
| CVSS | 3.7 |
| OWASP | A02:2021 |
| CWE | CWE-330 |

**Location**: `generate-secret.ps1:9` — `Get-Random -Maximum $characters.Length`

**What is vulnerable**: PowerShell's `Get-Random` uses a deterministic PRNG seeded from the system clock in some environments, producing predictable output.

**Proof of Concept**: Generate a secret, note the timestamp, then regenerate at the same time on the same OS to predict the value.

**Impact**: Generated secrets may be guessable.

**Fix**: Replace with `[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes(...)`.

---

### F14 — `PasswordService.main()` Method in Production Code

| Field | Value |
|-------|-------|
| Severity | LOW |
| CVSS | 3.1 |
| OWASP | A05:2021 |
| CWE | CWE-215 |

**Location**: `PasswordService.java:40-43`

**What is vulnerable**: A debug `main()` method hashes a hardcoded password `"asphalt6"`. This leaks the database password into class metadata and could be invoked accidentally.

**Proof of Concept**: Execute `PasswordService.main()` to see the password hash output, confirming the known plaintext.

**Impact**: Minor information disclosure. Could confuse code review tools.

**Fix**: Remove the `main()` method entirely.

---

### F15 — Frontend Admin Auth Token Stored in Inconsistent localStorage Keys

| Field | Value |
|-------|-------|
| Severity | LOW |
| CVSS | 3.7 |
| OWASP | A07:2021 |
| CWE | CWE-922 |

**Location**: `admin/dashboard/page.tsx:12` uses `adminToken`, while `admin/elections/page.tsx:14` uses `adminSession`

**What is vulnerable**: Different admin pages store/read the admin JWT under different localStorage keys (`adminToken` vs `adminSession`). This means logging in on one page doesn't authenticate on another.

**Proof of Concept**: Log in on the elections page (sets `adminSession`). Navigate to the dashboard page (reads `adminToken`). The dashboard shows no auth and fails silently.

**Impact**: Broken admin session management leads to confusion and potential token duplication across storage keys.

**Fix**: Standardize on a single localStorage key for the admin JWT across all admin pages.

---

### F16 — CORS Allows Only GET/POST/OPTIONS

| Field | Value |
|-------|-------|
| Severity | LOW |
| CVSS | 3.1 |
| OWASP | A05:2021 |
| CWE | CWE-942 |

**Location**: `CorsConfig.java:21` — `allowedMethods("GET", "POST", "OPTIONS")`

**What is vulnerable**: The CORS configuration is narrow (good) but combined with `.anyRequest().permitAll()` in SecurityConfig, any non-admin endpoint is accessible from any origin.

**Proof of Concept**: Create a malicious webpage at `evil.com` that makes `POST /api/auth/login` requests. CORS allows the preflight but the browser will send the request since the origin isn't in the allowed list — actually, the browser will block it. However, `PUT` and `DELETE` methods blocked by CORS aren't blocked at the Spring Security level.

**Impact**: Low — current CORS is reasonably restrictive. The gap is that non-`/api/admin/` endpoints have no CORS restriction at all.

**Fix**: Apply CORS restrictions consistently across all endpoint categories.

---

### F17 — Database Port Exposed to Host

| Field | Value |
|-------|-------|
| Severity | LOW |
| CVSS | 3.7 |
| OWASP | A05:2021 |
| CWE | CWE-284 |

**Location**: `docker-compose.yml:72` — `- "5433:5432"`

**What is vulnerable**: PostgreSQL is port-mapped to the host, allowing direct database connections from any machine on the network without container isolation.

**Proof of Concept**: `psql -h localhost -p 5433 -U postgres` connects directly using the password `asphalt6`.

**Impact**: Bypasses application-layer authentication. Direct data exfiltration or modification.

**Fix**: Remove the port mapping. Access the database only through internal Docker networking.

---

### F18 — Network Configuration Contains Embedded TLS Certificate

| Field | Value |
|-------|-------|
| Severity | LOW |
| CVSS | 3.1 |
| OWASP | A05:2021 |
| CWE | CWE-321 |

**Location**: `network-config.json:17-18`

**What is vulnerable**: The Hyperledger Fabric peer TLS CA certificate is hardcoded in the network config file. This is a self-signed certificate that won't be rotated automatically.

**Proof of Concept**: Extract the certificate from the file. If the private key is ever leaked, all Fabric communication is compromised.

**Impact**: Certificate pinning prevents rotation. Stale certificates may contain outdated trust anchors.

**Fix**: Load certificates from a secrets store or volume mount. Implement certificate rotation.

---

### F19 — Biometric Enrollment Has No Input Size Validation

| Field | Value |
|-------|-------|
| Severity | LOW |
| CVSS | 3.7 |
| OWASP | A05:2021 |
| CWE | CWE-400 |

**Location**: `AdminController.java:62-65` — `EnrollmentRequest` has `@NotBlank` on `base64FaceImage` but no `@Size` limit

**What is vulnerable**: The admin biometric enrollment endpoint accepts arbitrarily large base64 images. While the AI service validates `MAX_IMAGE_LENGTH = 3_000_000`, the Gateway controller passes the raw string to the AI service without pre-filtering.

**Proof of Concept**: Send a 100MB base64 image to `POST /api/admin/biometrics/enroll`. The Gateway serializes it into JSON, transmits it to the AI service, consuming bandwidth and memory on both services.

**Impact**: Denial of service through resource exhaustion.

**Fix**: Add `@Size(max = 4_000_000)` to the `base64FaceImage` field in `EnrollmentRequest`.

---

## Attack Surface Summary

| Vector | Entry Points | Authentication Required |
|--------|-------------|----------------------|
| Voter Login | `POST /api/auth/login` | No |
| Biometric Challenge | `POST /api/auth/biometric-challenge` | Pre-auth token |
| Biometric Confirm | `POST /api/auth/biometric-confirm` | Pre-auth token + challenge |
| Vote Casting | `POST /api/vote/cast` | Session token |
| Admin Login | `POST /api/admin/auth/login` | No |
| Admin Dashboard | `GET /api/admin/dashboard` | Admin JWT |
| Admin Biometrics | `POST /api/admin/biometrics/enroll` | Admin JWT + SUPER_ADMIN role |
| Active Election | `GET /api/elections/active` | No |
| Public Dashboard | `GET /api/public/dashboard` | No |
| Actuator Health | `GET /actuator/health` | No |
| Actuator Info | `GET /actuator/info` | No |

---

## Highest-Risk Attack Chain

**Chain: Unauthenticated Super-Admin Takeover**

1. Read `.env` from repository clone (F1) → obtain admin token secret
2. Forge a SUPER_ADMIN JWT token using the shared secret (F2)
3. Call `POST /api/admin/auth/login` with bootstrap credentials (F4) to get a valid token
4. With SUPER_ADMIN access: enroll any face as a voter biometric (F3 → decrypt existing embeddings)
5. Cast votes on behalf of any enrolled voter
6. Access security events, audit logs, and manipulate election data

**Combined Impact**: Complete election system compromise — vote manipulation, identity theft, audit log tampering.

---

## OWASP Top 10 (2021) Mapping

| OWASP Category | Findings |
|---------------|----------|
| A01 Broken Access Control | F5, F6, F11, F16 |
| A02 Cryptographic Failures | F2, F3, F13, F18 |
| A05 Security Misconfiguration | F7, F8, F10, F11, F12, F16, F17, F19 |
| A07 Identification & Auth Failures | F1, F4, F9, F15 |
| A09 Security Logging & Monitoring Gaps | Actuator exposure (F5) |

---

## Remediation Priority

| Priority | Finding | Effort |
|----------|---------|--------|
| P0 — Immediate | F1 (rotate all secrets, remove .env from git) | 1 day |
| P0 — Immediate | F2 (separate token secrets) | 1 day |
| P0 — Immediate | F3 (remove hardcoded encryption key fallback) | 2 hours |
| P1 — This Sprint | F4 (disable bootstrap password) | 1 hour |
| P1 — This Sprint | F7 (fix X-Forwarded-For handling) | 4 hours |
| P1 — This Sprint | F5 (restrict actuator access) | 2 hours |
| P2 — Next Sprint | F6, F8, F9, F10, F11, F12 | 1-2 days each |
| P3 — Backlog | F13-F19 | Various |
