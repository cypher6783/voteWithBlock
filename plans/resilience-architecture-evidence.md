# Resilience Architecture Evidence

Repository evidence collected for the Votetrust Ledger Disaster Recovery, Backup, High Availability, and Production Resilience Architecture assessment.

---

## 1. Deployment

### docker-compose.yml
- **File**: `docker-compose.yml` (lines 1-70)
- **Services defined**: ai-service, core-gateway, postgres-db, frontend-ui
- **Networks**: `voting-network` (bridge), `fabric_test` (external)
- **EVIDENCE**: No `restart:` policy defined for any service
- **EVIDENCE**: No `healthcheck:` defined for any service
- **EVIDENCE**: No `deploy.resources` (CPU/memory limits) defined
- **EVIDENCE**: No named volumes for PostgreSQL data persistence
- **EVIDENCE**: `depends_on` used without `condition: service_healthy`
- **EVIDENCE**: No `read_only`, `tmpfs`, or security hardening on containers

### start-all.ps1
- **File**: `start-all.ps1` (lines 1-38)
- **EVIDENCE**: Starts all services via `docker-compose up --build -d`
- **EVIDENCE**: Fabric started separately via WSL/Git Bash (manual step)
- **EVIDENCE**: No startup health verification after container launch

### start-fabric.sh
- **File**: `start-fabric.sh` (lines 1-31)
- **EVIDENCE**: Fabric started via `./network.sh up createChannel -c mychannel -ca`
- **EVIDENCE**: No dependency check on Docker or container readiness
- **EVIDENCE**: No retry or health check after network creation

---

## 2. Docker

### Core Gateway Dockerfile
- **File**: `Core Gateway/Dockerfile` (lines 1-5)
- **Base image**: `eclipse-temurin:17-jre-jammy`
- **EVIDENCE**: No multi-stage build
- **EVIDENCE**: No health check instruction
- **EVIDENCE**: No non-root user
- **EVIDENCE**: No JVM memory tuning flags

### Frontend Dockerfile
- **File**: `frontend ui/Dockerfile` (lines 1-10)
- **Base image**: `node:20-alpine`
- **EVIDENCE**: Copies `node_modules` directly (no `npm ci` at build)
- **EVIDENCE**: No health check instruction
- **EVIDENCE**: No non-root user specified
- **EVIDENCE**: `NODE_ENV=production` set

### AI Service Dockerfile
- **File**: `ai-verification/Dockerfile` (lines 1-17)
- **Base image**: `python:3.10-slim`
- **EVIDENCE**: pip retry loop (5 attempts with 10s sleep) for network resilience
- **EVIDENCE**: No health check instruction
- **EVIDENCE**: No non-root user
- **EVIDENCE**: Runs as `python src/main.py` (uvicorn)

---

## 3. Configuration

### .env
- **File**: `.env` (lines 1-14)
- **EVIDENCE**: Contains plaintext secrets: `POSTGRES_PASSWORD`, `APP_AUTH_SESSION_SECRET`, `APP_ADMIN_BOOTSTRAP_PASSWORD`
- **EVIDENCE**: `APP_AUTH_ALLOW_BOOTSTRAP_PASSWORD=true` (bootstrap auth enabled)
- **EVIDENCE**: No environment separation (single .env for all environments)
- **EVIDENCE**: No secrets management integration (Vault, AWS Secrets Manager)
- **EVIDENCE**: No `app.configuration.version` tracked

### Core Gateway application.yml
- **File**: `Core Gateway/src/main/resources/application.yml` (lines 1-62)
- **EVIDENCE**: Spring Boot config with externalized env vars
- **EVIDENCE**: `spring.jpa.hibernate.ddl-auto` defaults to `validate` (production-safe)
- **EVIDENCE**: Actuator health endpoint exposed: `management.endpoints.web.exposure.include: health`
- **EVIDENCE**: Health probes enabled: `management.endpoint.health.probes.enabled: true`
- **EVIDENCE**: Session TTL: 900 seconds (15 minutes)
- **EVIDENCE**: Rate limit: 12 attempts per 60-second window
- **EVIDENCE**: No database connection pool configuration (HikariCP defaults)
- **EVIDENCE**: No retry/timeout configuration for external service calls
- **EVIDENCE**: No circuit breaker configuration
- **EVIDENCE**: No `/actuator/info` endpoint exposed
- **EVIDENCE**: Fabric network config at `src/main/resources/network-config.json`

### Root application.yml
- **File**: `src/main/resources/application.yml` (lines 1-4)
- **EVIDENCE**: Minimal config with JWT secret and encryption key (placeholder values)

---

## 4. Logging

### Application Logging
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/service/SystemHealthService.java`
- **EVIDENCE**: Uses Lombok `@Slf4j` (SLF4J logging)
- **EVIDENCE**: Health check results logged at info/warn level

- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/service/VotingLedgerService.java`
- **EVIDENCE**: Transaction logging with SafeLog for PII masking
- **EVIDENCE**: Error logging for blockchain failures

- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/util/SafeLog.java` (lines 1-17)
- **EVIDENCE**: PII masking utility (truncates and masks identifiers)
- **EVIDENCE**: Used throughout for voter ID logging

### AI Service Logging
- **File**: `ai-verification/src/main.py` (lines 17-18)
- **EVIDENCE**: Python logging with `logging.basicConfig(level=logging.INFO)`
- **EVIDENCE**: Model load success/failure logged

### Audit Logging
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/service/AuditAnalyticsService.java` (lines 1-127)
- **EVIDENCE**: Admin action logs stored in `admin_action_log` table
- **EVIDENCE**: SHA-256 integrity hash computed for audit exports
- **EVIDENCE**: No correlation IDs or trace IDs in log output
- **EVIDENCE**: No centralized log aggregation (ELK, Fluentd, etc.)
- **EVIDENCE**: No immutable recovery audit records

---

## 5. Monitoring

### SystemHealthService
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/service/SystemHealthService.java` (lines 1-181)
- **EVIDENCE**: Scheduled health checks every 10 seconds (`@Scheduled(fixedRate = 10000)`)
- **EVIDENCE**: Tracks 4 components: Gateway, AI, Database, Fabric
- **EVIDENCE**: Status levels: HEALTHY, DEGRADED, CRITICAL
- **EVIDENCE**: AtomicInteger counters for failure/success tracking
- **EVIDENCE**: DEGRADED threshold: 3 consecutive failures
- **EVIDENCE**: CRITICAL threshold: 5 consecutive failures
- **EVIDENCE**: HEALTHY recovery: 3 consecutive successes
- **EVIDENCE**: Overall status: CRITICAL if 2+ critical, DEGRADED if 1 critical or 2+ degraded
- **EVIDENCE**: No health history persistence (only current state)
- **EVIDENCE**: No resource monitoring (CPU, memory, disk, thread pool, JVM heap, connection pool)

### DashboardCacheService
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/service/DashboardCacheService.java` (lines 1-51)
- **EVIDENCE**: Caffeine cache with TTL: public (30s), admin (10s), health (5s)
- **EVIDENCE**: In-memory only (lost on restart)

### DashboardMetricsService
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/service/DashboardMetricsService.java` (lines 1-77)
- **EVIDENCE**: In-memory AtomicLong counters (not persisted)
- **EVIDENCE**: Biometric metrics: successful/failed verifications, lockouts, re-enrollment
- **EVIDENCE**: Security metrics: login failures, rate limit events, suspicious activities
- **EVIDENCE**: Comment: "These would normally be persisted in a database table or Redis"
- **EVIDENCE**: No resource metrics (CPU, memory, disk, thread pool, JVM heap, connection pool)

### OperationsMetricsService
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/service/OperationsMetricsService.java` (lines 1-83)
- **EVIDENCE**: Scheduled metrics persistence every 60 seconds (`@Scheduled(fixedRate = 60000)`)
- **EVIDENCE**: Persists to `operations_metrics_history` table
- **EVIDENCE**: Catches and logs persistence failures (no retry)
- **EVIDENCE**: No health history aggregation (raw/hourly/daily)

### DashboardAlertService
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/service/DashboardAlertService.java` (lines 1-55)
- **EVIDENCE**: In-memory alert list (CopyOnWriteArrayList)
- **EVIDENCE**: Evaluates alerts based on system health
- **EVIDENCE**: Fabric disconnected = CRITICAL
- **EVIDENCE**: AI Service offline = HIGH
- **EVIDENCE**: Database degraded = CRITICAL
- **EVIDENCE**: No circuit breaker state metrics exposed
- **EVIDENCE**: No resource exhaustion alerts

---

## 6. Database

### PostgreSQL Configuration
- **File**: `docker-compose.yml` (lines 41-50)
- **EVIDENCE**: PostgreSQL 15 Alpine image
- **EVIDENCE**: Port mapping: 5433:5432 (host:container)
- **EVIDENCE**: No named volume for data persistence
- **EVIDENCE**: No backup configuration
- **EVIDENCE**: No replication setup
- **EVIDENCE**: No connection pool tuning
- **EVIDENCE**: No backup_catalog table
- **EVIDENCE**: No health_history table

### Database Migrations
- **File**: `database_migrations.sql` (lines 1-45)
- **EVIDENCE**: Biometric tables: voter_biometrics, biometric_challenges, biometric_attempt_log
- **EVIDENCE**: Uses `IF NOT EXISTS` for idempotent migration
- **EVIDENCE**: No migration versioning tool (Flyway/Liquibase)

- **File**: `database_migrations_v2_admin.sql` (lines 1-54)
- **EVIDENCE**: Admin tables: admin_accounts, admin_sessions, admin_action_log
- **EVIDENCE**: Indexes on username, expires_at, performed_at

- **File**: `database_migrations_v3_elections.sql` (lines 1-54)
- **EVIDENCE**: Election tables: elections, candidates, election_incidents
- **EVIDENCE**: Optimistic locking via `version` column on elections

- **File**: `database_migrations_v4_operations.sql` (lines 1-141)
- **EVIDENCE**: Operations tables: operations_alerts, security_events, operations_metrics_history, audit_exports
- **EVIDENCE**: Custom PostgreSQL enums: incident_status, incident_severity, alert_severity, alert_status, security_event_type
- **EVIDENCE**: Comprehensive indexing for query performance
- **EVIDENCE**: No outbox_events table
- **EVIDENCE**: No vote_request_log table
- **EVIDENCE**: No backup_catalog table
- **EVIDENCE**: No health_history table
- **EVIDENCE**: No recovery_audit_log table

### VoterAccount Model
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/model/VoterAccount.java` (lines 1-26)
- **EVIDENCE**: `hasVoted` boolean field for double-vote prevention
- **EVIDENCE**: No optimistic locking version field
- **EVIDENCE**: No idempotency key tracking

### Election Model
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/model/Election.java` (lines 1-47)
- **EVIDENCE**: `@Version` annotation for optimistic locking
- **EVIDENCE**: Status enum: SCHEDULED, OPEN, CLOSED

---

## 7. Fabric / Blockchain

### VotingLedgerService
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/service/VotingLedgerService.java` (lines 1-127)
- **EVIDENCE**: `@PostConstruct` initializes Fabric connection
- **EVIDENCE**: Sandbox fallback mode when Fabric is offline (line 79: `sandbox-tx-` prefix)
- **EVIDENCE**: `@PreDestroy` closes gateway session
- **EVIDENCE**: No retry logic for blockchain submissions
- **EVIDENCE**: No timeout configuration for gRPC calls
- **EVIDENCE**: No circuit breaker pattern
- **EVIDENCE**: Transaction latency tracking (average submission/query latency)
- **EVIDENCE**: Contract availability check: `isContractAvailable()` returns `contract != null`
- **EVIDENCE**: No outbox pattern for reliable event delivery

### network-config.json
- **File**: `Core Gateway/target/classes/network-config.json` (lines 1-21)
- **EVIDENCE**: Single peer configuration: `peer0.org1.example.com`
- **EVIDENCE**: gRPC URL: `grpc://localhost:7051`
- **EVIDENCE**: TLS CA certificate embedded
- **EVIDENCE**: Single organization: Org1MSP

### ElectionResultsCacheService
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/service/ElectionResultsCacheService.java` (lines 1-40)
- **EVIDENCE**: Caffeine cache with 10-second TTL
- **EVIDENCE**: Prevents Fabric query spam
- **EVIDENCE**: Mock results returned (not connected to real chaincode queries)

---

## 8. Security

### SecurityConfig
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/config/SecurityConfig.java` (lines 1-42)
- **EVIDENCE**: Stateless session management
- **EVIDENCE**: CSRF disabled
- **EVIDENCE**: Public endpoints: `/api/auth/**`, `/api/vote/**`, `/actuator/**`
- **EVIDENCE**: Admin endpoints require authentication
- **EVIDENCE**: Custom `AdminAuthFilter` for JWT validation

### AdminTokenService
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/service/AdminTokenService.java` (lines 1-107)
- **EVIDENCE**: HMAC-SHA256 signing
- **EVIDENCE**: Default TTL: 8 hours (28800 seconds)
- **EVIDENCE**: Constant-time comparison for signature validation
- **EVIDENCE**: Token type validation (`admin-session`)

### SessionTokenService
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/service/SessionTokenService.java` (lines 1-114)
- **EVIDENCE**: HMAC-SHA256 signing
- **EVIDENCE**: Default TTL: 900 seconds (15 minutes)
- **EVIDENCE**: Biometric claim verification required for voting
- **EVIDENCE**: Constant-time comparison for signature validation

### RateLimitService
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/service/RateLimitService.java` (lines 1-42)
- **EVIDENCE**: In-memory sliding window rate limiter
- **EVIDENCE**: Default: 12 attempts per 60-second window
- **EVIDENCE**: ConcurrentHashMap storage (lost on restart)
- **EVIDENCE**: No distributed rate limiting

### SecurityEventService
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/service/SecurityEventService.java` (lines 1-68)
- **EVIDENCE**: Event types: FAILED_LOGIN, FAILED_ADMIN_LOGIN, RATE_LIMIT, ACCOUNT_LOCKOUT, BIOMETRIC_FAILURE, SUSPICIOUS_ACTIVITY, FABRIC_FAILURE, SYSTEM_ERROR
- **EVIDENCE**: Events stored in `security_events` table
- **EVIDENCE**: Event statistics: 24h, 7d counts by type
- **EVIDENCE**: No recovery action event types (DATABASE_RESTORE, FABRIC_RECOVERY, BACKUP_CREATED, etc.)

### AdminBootstrapRunner
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/config/AdminBootstrapRunner.java` (lines 1-89)
- **EVIDENCE**: Bootstrap admin account creation on startup
- **EVIDENCE**: Credentials from environment variables
- **EVIDENCE**: Audit log entry created for bootstrap action
- **EVIDENCE**: Fails startup if bootstrap enabled but credentials missing

---

## 9. Recovery

### VoteController - Transaction Recovery
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/controller/VoteController.java` (lines 58-124)
- **EVIDENCE**: Vote flow: validate session -> check voter -> set hasVoted=true -> submit to Fabric
- **EVIDENCE**: Rollback on blockchain failure: reverts `hasVoted` to false (lines 113-118)
- **EVIDENCE**: CRITICAL ERROR logged if DB rollback also fails (line 118)
- **EVIDENCE**: No retry logic for blockchain submission
- **EVIDENCE**: No idempotency key for vote submission
- **EVIDENCE**: No outbox pattern for reliable event delivery
- **EVIDENCE**: `@Transactional` on castVote method
- **EVIDENCE**: Crash between DB update and Fabric submission can leave inconsistent state

### SystemHealthService - Component Recovery
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/service/SystemHealthService.java` (lines 65-130)
- **EVIDENCE**: Database check: `SELECT 1` query
- **EVIDENCE**: AI check: HTTP GET to `/health` endpoint
- **EVIDENCE**: Fabric check: `isContractAvailable()` method
- **EVIDENCE**: Auto-recovery: 3 consecutive successes restores HEALTHY status
- **EVIDENCE**: No health history persistence for trend analysis

---

## 10. Health

### Spring Boot Actuator
- **File**: `Core Gateway/src/main/resources/application.yml` (lines 54-62)
- **EVIDENCE**: Actuator health endpoint exposed at `/actuator/health`
- **EVIDENCE**: Health probes enabled for Kubernetes-style probes
- **EVIDENCE**: Only `health` endpoint exposed (not all actuator endpoints)
- **EVIDENCE**: No `/actuator/info` endpoint for configuration version tracking
- **EVIDENCE**: No `/actuator/metrics` for resource monitoring (CPU, memory, JVM, thread pool)

### AI Service Health
- **File**: `ai-verification/src/main.py` (lines 74-80)
- **EVIDENCE**: `/health` endpoint returns: status, modelLoaded, modelVersion
- **EVIDENCE**: Model load status tracked in `app_state`

### Operations Health API
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/controller/OperationsController.java` (lines 148-165)
- **EVIDENCE**: `/api/admin/operations/health` endpoint for full system health
- **EVIDENCE**: `/api/admin/operations/health/{service}` for individual component health
- **EVIDENCE**: Service names: gateway, ai, ai-service, database, db, fabric, blockchain
- **EVIDENCE**: No circuit breaker metrics endpoint
- **EVIDENCE**: No resource metrics endpoint

### Frontend Health Display
- **File**: `frontend ui/src/app/admin/operations/health/page.tsx` (lines 1-91)
- **EVIDENCE**: Polls health endpoint every 10 seconds
- **EVIDENCE**: Displays component health status with color coding
- **EVIDENCE**: Shows blockchain metrics (latency, transaction IDs)
- **EVIDENCE**: No circuit breaker state display
- **EVIDENCE**: No resource utilization display

---

## 11. Infrastructure

### CorsConfig
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/config/CorsConfig.java` (lines 1-25)
- **EVIDENCE**: CORS configured for `/api/**` paths
- **EVIDENCE**: Allowed origins from environment variable
- **EVIDENCE**: Methods: GET, POST, OPTIONS
- **EVIDENCE**: Max age: 3600 seconds (1 hour)

### ElectionLifecycleScheduler
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/service/ElectionLifecycleScheduler.java` (lines 1-55)
- **EVIDENCE**: Scheduled every 60 seconds
- **EVIDENCE**: Transitions: SCHEDULED -> OPEN -> CLOSED
- **EVIDENCE**: Error handling per election (continues processing others)

### ElectionOperationsService
- **File**: `Core Gateway/src/main/java/com/votewithblock/gateway/service/ElectionOperationsService.java` (lines 1-155)
- **EVIDENCE**: Health score calculation: 100 - (critical*20) - (high*10) - (alerts*5)
- **EVIDENCE**: Operational warnings: HIGH_INCIDENT_COUNT, CRITICAL_INCIDENT, ALERT_SPIKE, ELECTION_ENDING_SOON

---

## 12. Summary of Resilience Gaps

### NO EVIDENCE FOUND for:
1. **Database backup strategy** - No pg_dump scripts, no WAL archiving, no backup scheduling
2. **Docker restart policies** - No `restart:` in docker-compose.yml
3. **Docker health checks** - No `healthcheck:` in docker-compose.yml
4. **Container resource limits** - No CPU/memory limits defined
5. **Persistent volumes** - PostgreSQL data not persisted with named volumes
6. **Circuit breakers** - No Resilience4j or similar patterns
7. **Retry logic** in application code - Only in Dockerfile pip install
8. **Graceful shutdown** - No shutdown hooks beyond @PreDestroy
9. **Centralized logging** - No ELK, Fluentd, or log aggregation
10. **Correlation IDs** - No request tracing across services
11. **Encrypted backups** - No backup encryption
12. **Point-in-time recovery** - No WAL archiving
13. **Idempotency keys** - Vote submission has no idempotency protection
14. **Secrets management** - Secrets in plaintext .env file
15. **Rolling deployment** - No deployment strategy defined
16. **Database replication** - Single PostgreSQL instance
17. **Load balancing** - Single instance per service
18. **Network policies** - Basic Docker bridge only
19. **Container security** - No non-root users, no read-only filesystems
20. **Startup ordering** - depends_on without health conditions
21. **Outbox pattern** - No reliable event delivery between DB and Fabric
22. **Repository-based idempotency** - No database-backed idempotency keys
23. **Configuration version tracking** - No `app.configuration.version`
24. **Backup catalog** - No `backup_catalog` table
25. **Recovery drills** - No scheduled recovery exercises
26. **Circuit breaker metrics** - No circuit state, open count, failure rate exposed
27. **Health history retention** - No raw/hourly/daily aggregation
28. **Deployment verification gates** - No pre/post deployment validation
29. **Resource monitoring** - No CPU, memory, disk, thread pool, JVM heap, connection pool metrics
30. **Immutable recovery audit** - No DATABASE_RESTORE, FABRIC_RECOVERY, BACKUP_CREATED records
31. **Chaos testing** - No controlled failure testing
