# Resilience Architecture Plan

Production-grade Disaster Recovery, Backup, High Availability, and Resilience Architecture for Votetrust Ledger.

---

## 1. Executive Summary

Votetrust Ledger is approaching production deployment for a 24-hour public election. The current architecture consists of four services (Core Gateway, Frontend UI, AI Service, PostgreSQL) orchestrated via Docker Compose, with Hyperledger Fabric as the blockchain layer. The system has operational monitoring capabilities (SystemHealthService, OperationsAlertService, SecurityEventService) but lacks production-critical resilience infrastructure: no database backup strategy, no Docker restart policies, no health checks, no persistent volumes, no circuit breakers, no outbox pattern, and no centralized logging. This plan addresses all gaps to achieve election-day reliability.

---

## 2. Current Repository Capabilities

### Existing Components

| Component | Technology | Health Check | Persistence | Recovery |
|-----------|-----------|--------------|-------------|----------|
| Core Gateway | Spring Boot 3.2.4 / Java 17 | Actuator `/actuator/health` | PostgreSQL (JPA) | Sandbox fallback for Fabric |
| Frontend UI | Next.js / TypeScript | NONE | None | Container restart only |
| AI Service | FastAPI / Python 3.10 | `/health` endpoint | None | Container restart only |
| PostgreSQL | PostgreSQL 15 Alpine | JdbcTemplate `SELECT 1` | NONE (no volumes) | Manual |
| Hyperledger Fabric | Fabric Gateway 1.4.0 | `isContractAvailable()` | External network | Sandbox fallback |

**Evidence**: `Core Gateway/src/main/resources/application.yml:54-62` (Actuator config), `ai-verification/src/main.py:74-80` (AI health), `Core Gateway/src/main/java/com/votewithblock/gateway/service/SystemHealthService.java:39-45` (health checks)

### Existing Monitoring

- **SystemHealthService**: 10-second interval health checks for all 4 components with DEGRADED (3 failures) and CRITICAL (5 failure) thresholds
- **OperationsAlertService**: CRUD for operational alerts with severity/status tracking
- **SecurityEventService**: Security event recording with 24h/7d statistics
- **AuditAnalyticsService**: Admin action logging with SHA-256 integrity hashing
- **OperationsMetricsService**: 60-second metrics persistence to database
- **DashboardCacheService**: Caffeine caching (5-30s TTL)

**Evidence**: `SystemHealthService.java:39-45`, `OperationsAlertService.java:26-37`, `SecurityEventService.java:24-37`, `AuditAnalyticsService.java:76-126`, `OperationsMetricsService.java:43-68`

### Existing Security

- HMAC-SHA256 token signing (voter sessions: 15min TTL, admin sessions: 8hr TTL)
- Biometric claim enforcement on vote submission
- Rate limiting: 12 attempts per 60-second window (in-memory)
- Admin authentication filter with JWT validation
- PII masking via SafeLog utility

**Evidence**: `SessionTokenService.java:20-33`, `AdminTokenService.java:20-33`, `RateLimitService.java:22-27`, `SecurityConfig.java:23-38`

---

## 3. Existing Reusable Components

| Component | File | Reusability |
|-----------|------|-------------|
| SystemHealthService | `Core Gateway/src/main/java/.../service/SystemHealthService.java` | Extend with backup health, storage health, resource metrics |
| OperationsAlertService | `Core Gateway/src/main/java/.../service/OperationsAlertService.java` | Add backup alert types, circuit breaker alerts |
| SecurityEventService | `Core Gateway/src/main/java/.../service/SecurityEventService.java` | Add recovery event types |
| DashboardCacheService | `Core Gateway/src/main/java/.../service/DashboardCacheService.java` | Add backup metrics cache |
| SafeLog | `Core Gateway/src/main/java/.../util/SafeLog.java` | Use for all log sanitization |
| AuditAnalyticsService | `Core Gateway/src/main/java/.../service/AuditAnalyticsService.java` | Extend for recovery audit, immutable audit records |
| ElectionLifecycleScheduler | `Core Gateway/src/main/java/.../service/ElectionLifecycleScheduler.java` | Add recovery checks |
| Docker Compose | `docker-compose.yml` | Add restart policies, health checks, volumes |

---

## 4. Backup Architecture

### 4.1 Database Backup Strategy

**Current State**: NO EVIDENCE FOUND for any backup mechanism.

**Target Architecture**:

#### Full Backups
- **Schedule**: Daily at 02:00 UTC (outside election hours)
- **Method**: `pg_dump` with custom format (`-Fc`) for compression and selective restore
- **Storage**: Encrypted to local volume + offsite (S3 or equivalent)
- **Retention**: 30 days for daily, 12 months for weekly
- **Verification**: Automated restore test to isolated database instance

#### Incremental Backups
- **Schedule**: Every 6 hours
- **Method**: PostgreSQL WAL (Write-Ahead Log) archiving
- **Storage**: Same encrypted destination as full backups
- **Retention**: 7 days

#### Point-in-Time Recovery
- **Method**: WAL archiving with `archive_mode = on`
- **Recovery Point**: Any point within retention window
- **Recovery Time**: < 15 minutes for database restoration

#### Backup Verification
- **Automated**: Weekly restore test to isolated instance
- **Manual**: Pre-election full restore verification
- **Checksum**: SHA-256 hash verification of backup files

#### Encrypted Backups
- **Method**: GPG encryption with dedicated backup key
- **Key Management**: Separate from application secrets
- **Key Rotation**: Quarterly

#### Backup Catalog

Maintain a `backup_catalog` table to track all backup operations:

```sql
CREATE TABLE backup_catalog (
    id BIGSERIAL PRIMARY KEY,
    backup_id VARCHAR(64) UNIQUE NOT NULL,
    backup_type VARCHAR(32) NOT NULL,       -- FULL, INCREMENTAL, WAL
    created_at TIMESTAMPTZ NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256_hash VARCHAR(64) NOT NULL,
    storage_location VARCHAR(512) NOT NULL,
    verified BOOLEAN DEFAULT FALSE,
    verified_at TIMESTAMPTZ,
    retention_days INT NOT NULL,
    metadata TEXT
);
```

This enables rapid backup selection during incidents and automated retention management.

**Implementation**:
- Create `scripts/backup-database.sh` for pg_dump operations
- Create `scripts/restore-database.sh` for recovery operations
- Create `scripts/verify-backup.sh` for automated verification
- Create database migration for `backup_catalog` table
- Add PostgreSQL configuration for WAL archiving

### 4.2 Application Backup

**Current State**: In-memory metrics (AtomicLong) lost on restart.

**Target**: Persist all metrics to database (already partially done via OperationsMetricsService). Ensure election state survives restarts via database persistence.

**Evidence**: `DashboardMetricsService.java:16-17` (in-memory counters), `OperationsMetricsService.java:43-68` (periodic persistence)

---

## 5. Disaster Recovery

### 5.1 Recovery Time Objectives (RTO)

| Component | RTO | Recovery Method |
|-----------|-----|-----------------|
| PostgreSQL | 15 min | WAL restore + pg_restore |
| Core Gateway | 2 min | Container auto-restart |
| AI Service | 2 min | Container auto-restart |
| Frontend UI | 1 min | Container auto-restart |
| Hyperledger Fabric | 10 min | Network restart + channel rejoin |
| Full System | 30 min | Automated recovery runbook |

### 5.2 Recovery Point Objectives (RPO)

| Component | RPO | Data Loss Window |
|-----------|-----|------------------|
| PostgreSQL | 6 hours | WAL archiving interval |
| Vote Transactions | 0 | Blockchain is source of truth |
| Audit Logs | 6 hours | Database backup interval |
| Security Events | 6 hours | Database backup interval |

### 5.3 Disaster Recovery Scenarios

#### Scenario: Database Failure
1. PostgreSQL container crashes
2. Docker auto-restarts container (with restart policy)
3. SystemHealthService detects DB status: DEGRADED -> CRITICAL
4. OperationsAlertService creates CRITICAL alert
5. If data loss: restore from latest WAL archive
6. Verify: `SELECT 1` health check returns success
7. Re-sync: Fabric is source of truth for votes
8. Log recovery action in `recovery_audit_log` table

**Evidence**: `SystemHealthService.java:65-82` (DB health check), `VoteController.java:88-96` (DB state update)

#### Scenario: Fabric Outage
1. Fabric network becomes unavailable
2. SystemHealthService detects: fabricStatus = CRITICAL
3. VotingLedgerService falls back to sandbox mode
4. Votes accepted but flagged as "sandbox-tx-*"
5. Admin notified via OperationsAlertService
6. When Fabric recovers: reconcile sandbox transactions via outbox pattern

**Evidence**: `VotingLedgerService.java:77-80` (sandbox fallback), `SystemHealthService.java:104-130` (Fabric health check)

#### Scenario: AI Service Failure
1. AI service becomes unavailable
2. SystemHealthService detects: aiStatus = CRITICAL
3. Biometric verification unavailable
4. New voter enrollments blocked
5. Existing verified sessions continue (session tokens valid)
6. Admin notified, manual intervention required

**Evidence**: `SystemHealthService.java:84-101` (AI health check), `ai-verification/src/main.py:74-80` (health endpoint)

#### Scenario: Gateway Failure
1. Core Gateway container crashes
2. Docker auto-restarts container
3. Spring Boot health endpoint recovers
4. SystemHealthService detects: gatewayStatus = HEALTHY
5. In-memory caches rebuilt on demand
6. Rate limiter state lost (acceptable - resets counters)

**Evidence**: `SystemHealthService.java:47-62` (gateway health check), `RateLimitService.java:17` (in-memory state)

### 5.4 Recovery Drills

Mandatory scheduled recovery exercises to validate procedures:

| Drill | Frequency | Scope | Duration |
|-------|-----------|-------|----------|
| PostgreSQL Restore Drill | Monthly | Database backup/restore | 2 hours |
| Fabric Recovery Drill | Monthly | Network restart, channel rejoin | 2 hours |
| Full Disaster Recovery Simulation | Quarterly | Complete system recovery | 4 hours |
| Pre-Election Operational Rehearsal | Before election | Full operational workflow | 8 hours |

**Drill Requirements**:
- Documented expected outcomes
- Time-to-recovery measurement
- Comparison against RTO targets
- Lessons learned documentation
- Process improvement implementation

---

## 6. High Availability

### 6.1 Component Availability Matrix

| Component | Current State | Target State | Strategy |
|-----------|---------------|--------------|----------|
| PostgreSQL | Single instance | Primary + replica | Streaming replication |
| Core Gateway | Single instance | 2+ instances | Load balancer + shared state |
| AI Service | Single instance | 2+ instances | Stateless, load balanced |
| Frontend UI | Single instance | 2+ instances | Stateless, CDN |
| Fabric | External network | Multi-org | Existing Fabric HA |

### 6.2 Failure Behavior

| Component Failure | User Impact | Recovery Action |
|-------------------|-------------|-----------------|
| PostgreSQL | Vote submission fails | Auto-restart, WAL restore |
| Core Gateway | All API calls fail | Auto-restart, load balancer failover |
| AI Service | Biometric verification fails | Graceful degradation, manual fallback |
| Fabric | Blockchain writes fail | Sandbox mode, queue for retry via outbox |
| Frontend UI | Web interface unavailable | Auto-restart, CDN fallback |

### 6.3 Graceful Degradation

**Current Evidence**: `VotingLedgerService.java:77-80` (sandbox fallback when Fabric offline)

**Target**:
- AI offline: Block new enrollments, allow existing verified sessions
- Fabric offline: Accept votes in sandbox mode, queue for blockchain sync via outbox pattern
- Database offline: Reject vote submissions (cannot verify voter state)
- Gateway offline: Frontend shows maintenance page

---

## 7. Startup Architecture

### 7.1 Dependency Ordering

**Current State**:
- `core-gateway` depends on: `ai-service`, `postgres-db`
- `frontend-ui` depends on: `core-gateway`
- `depends_on` without health condition

**Target**: Docker Compose `depends_on` with `condition: service_healthy`

```yaml
core-gateway:
  depends_on:
    ai-service:
      condition: service_healthy
    postgres-db:
      condition: service_healthy

frontend-ui:
  depends_on:
    core-gateway:
      condition: service_healthy
```

**Evidence**: `docker-compose.yml:34-36` (current depends_on), `docker-compose.yml:60-61` (frontend depends_on)

### 7.2 Health Verification Sequence

1. PostgreSQL starts and passes health check (`pg_isready`)
2. AI Service starts and passes health check (`/health` endpoint)
3. Core Gateway starts, connects to DB, initializes Fabric connection
4. Core Gateway passes Actuator health check (`/actuator/health`)
5. Frontend UI starts, connects to Gateway API

### 7.3 Startup Validation

- **Database**: `pg_isready` command in health check
- **AI Service**: HTTP GET `/health` returns `modelLoaded: true`
- **Gateway**: Actuator `/actuator/health` returns UP
- **Fabric**: `isContractAvailable()` returns true (or sandbox mode)

**Evidence**: `SystemHealthService.java:65-67` (DB check), `ai-verification/src/main.py:74-80` (AI health), `Core Gateway/src/main/resources/application.yml:54-62` (Actuator)

---

## 8. Transaction Recovery

### 8.1 Outbox Pattern

**Current State**: Crash between PostgreSQL update and Fabric submission can leave inconsistent state.

**Target Architecture**:

#### OutboxEvent Table

```sql
CREATE TABLE outbox_events (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(32) NOT NULL,        -- VOTE_SUBSCRIPTION, AUDIT_LOG, etc.
    payload TEXT NOT NULL,                   -- JSON payload
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',  -- PENDING, PROCESSED, FAILED
    created_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ,
    retry_count INT DEFAULT 0,
    max_retries INT DEFAULT 3,
    last_error TEXT,
    metadata TEXT
);
```

#### Outbox Workflow

```
Vote Request
    |
    v
Database Transaction
    |
    v
Update voter_state (hasVoted = true)
    |
    v
Insert OutboxEvent (VOTE_SUBSCRIPTION)
    |
    v
Commit Transaction
    |
    v
Background Processor (scheduled every 5 seconds)
    |
    v
Read PENDING events
    |
    v
Submit to Fabric
    |
    v
Mark PROCESSED or FAILED
```

**Benefits**:
- Guarantees reliable event delivery without distributed transactions
- Survives crashes between DB update and Fabric submission
- Enables retry with exponential backoff
- Provides audit trail of all blockchain submissions

### 8.2 Vote Submission Flow

**Current Flow** (VoteController.java:58-124):
1. Rate limit check
2. Session token validation (biometric claim required)
3. Voter identity verification
4. Double-vote check (`hasVoted` flag)
5. Set `hasVoted = true` in database
6. Submit ballot to Fabric
7. On failure: rollback `hasVoted` to false

**Current Gaps**:
- No idempotency key for vote submission
- No retry logic for blockchain submission
- No timeout configuration for Fabric calls
- No poison message handling
- Crash between DB update and Fabric submission

### 8.3 Target Transaction Recovery

#### Repository-Based Idempotency

**Create `vote_request_log` table**:

```sql
CREATE TABLE vote_request_log (
    id BIGSERIAL PRIMARY KEY,
    idempotency_key VARCHAR(64) UNIQUE NOT NULL,
    voter_id_hash VARCHAR(64) NOT NULL,
    election_id UUID NOT NULL,
    candidate_id UUID NOT NULL,
    status VARCHAR(16) NOT NULL,           -- PENDING, SUBMITTED, FAILED
    transaction_id VARCHAR(128),            -- Fabric transaction ID
    created_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ,
    retry_count INT DEFAULT 0,
    last_error TEXT
);
```

**Never rely solely on cache or memory. Database-backed idempotency survives crashes.**

#### Blockchain Retry
- Exponential backoff: 1s, 2s, 4s (max 3 retries)
- Circuit breaker: Open after 5 consecutive failures
- Timeout: 10 seconds per Fabric call
- Fallback: Sandbox mode with deferred blockchain sync via outbox

#### Database Retry
- Connection pool: HikariCP with retry logic
- Deadlock retry: Spring `@Retryable` on transaction failures
- Validation query: `SELECT 1` before operations

#### Duplicate Detection
- `hasVoted` boolean on VoterAccount (already implemented)
- `vote_request_log` idempotency key (database-backed)
- Fabric transaction ID stored for audit trail

**Evidence**: `VoteController.java:83-86` (double-vote check), `VoteController.java:88-96` (DB state update), `VoteController.java:99-123` (Fabric submission + rollback)

---

## 9. Deployment Strategy

### 9.1 Docker Compose Enhancements

**Current State** (docker-compose.yml): No restart policies, no health checks, no volumes, no resource limits.

**Target Configuration**:

```yaml
services:
  postgres-db:
    restart: unless-stopped
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 10s
      timeout: 5s
      retries: 5
    volumes:
      - postgres_data:/var/lib/postgresql/data
    deploy:
      resources:
        limits:
          memory: 2G
          cpus: '2.0'

  core-gateway:
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 15s
      timeout: 10s
      retries: 5
      start_period: 60s
    depends_on:
      ai-service:
        condition: service_healthy
      postgres-db:
        condition: service_healthy
    deploy:
      resources:
        limits:
          memory: 1G
          cpus: '1.0'

  ai-service:
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8000/health"]
      interval: 15s
      timeout: 10s
      retries: 5
      start_period: 30s
    deploy:
      resources:
        limits:
          memory: 2G
          cpus: '2.0'

  frontend-ui:
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:3000"]
      interval: 15s
      timeout: 5s
      retries: 3
    depends_on:
      core-gateway:
        condition: service_healthy
    deploy:
      resources:
        limits:
          memory: 512M
          cpus: '0.5'

volumes:
  postgres_data:
    driver: local
```

### 9.2 Rolling Deployment

- Build new image versions
- Stop and replace one service at a time
- Health check verification before proceeding to next service
- Rollback capability: keep previous image tags

### 9.3 Zero-Downtime Deployment

- Frontend: Static assets served by Next.js, no database dependency
- Gateway: Graceful shutdown with Spring Boot (`server.shutdown=graceful`)
- AI Service: Stateless, replaceable without state loss
- Database: Schema migrations via Flyway (future enhancement)

**Evidence**: `Core Gateway/Dockerfile:5` (ENTRYPOINT java -jar), `ai-verification/Dockerfile:17` (CMD python src/main.py)

### 9.4 Deployment Verification Gates

Automated validation gates for every deployment:

#### Pre-Deployment Gates
1. **Database Migration Validation**: Verify schema compatibility
2. **Configuration Validation**: Check required environment variables
3. **Secret Validation**: Verify secrets are present and valid
4. **Health Validation**: All services report HEALTHY

#### Post-Deployment Gates
1. **Gateway Healthy**: `/actuator/health` returns UP
2. **AI Healthy**: `/health` returns `modelLoaded: true`
3. **Fabric Connected**: `isContractAvailable()` returns true
4. **Database Healthy**: `SELECT 1` succeeds
5. **Synthetic Login**: Successful admin login test
6. **Synthetic Vote** (staging only): End-to-end vote submission

**Deployment should fail automatically if any gate fails.**

---

## 10. Configuration Management

### 10.1 Environment Separation

**Current State**: Single `.env` file with production secrets.

**Target**: Environment-specific configuration files:
- `.env.development` - Local development
- `.env.testing` - Test environment
- `.env.staging` - Pre-production
- `.env.production` - Production deployment

### 10.2 Configuration Version Tracking

**Current State**: No configuration version tracked.

**Target**: Add `app.configuration.version` to application metadata.

```yaml
# application.yml
app:
  configuration:
    version: ${APP_CONFIG_VERSION:1.0.0}
```

Expose via Actuator `/actuator/info`:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    info:
      enabled: true
```

Operations staff should always know which configuration version is running.

**Evidence**: `Core Gateway/src/main/resources/application.yml:54-62` (Actuator config, no info endpoint)

### 10.3 Secret Management

**Current State**: Secrets in plaintext `.env` file.

**Target**:
- Use Docker secrets or environment-specific secret injection
- Rotate session secrets quarterly
- Never commit secrets to version control
- Use `generate-secret.ps1` for secret generation

**Evidence**: `.env:5` (APP_AUTH_SESSION_SECRET in plaintext), `generate-secret.ps1:1-13` (secret generation utility)

### 10.4 Configuration Validation

**Current State**: AdminBootstrapRunner validates bootstrap credentials on startup.

**Target**:
- Validate all required environment variables at startup
- Fail fast if critical configuration missing
- Log configuration summary (without secrets) on startup

**Evidence**: `AdminBootstrapRunner.java:49-53` (credential validation)

---

## 11. Logging Architecture

### 11.1 Current State

- SLF4J logging via Lombok `@Slf4j`
- Python logging via `logging.basicConfig`
- Audit logs in `admin_action_log` table with SHA-256 integrity
- PII masking via `SafeLog.id()` utility

**Evidence**: `SafeLog.java:7-16` (PII masking), `AuditAnalyticsService.java:86-100` (audit export with SHA-256)

### 11.2 Target Logging Architecture

#### Application Logs
- Structured JSON logging for all services
- Log levels: ERROR, WARN, INFO, DEBUG
- Rotation: Daily, 30-day retention

#### Security Logs
- Security events stored in `security_events` table (already implemented)
- Include: failed logins, rate limits, biometric failures, suspicious activity
- Retention: 90 days

#### Audit Logs
- Admin actions stored in `admin_action_log` table (already implemented)
- SHA-256 integrity hash (already implemented)
- Export capability (already implemented)
- Retention: 1 year

#### Biometric Logs
- Biometric attempt logs in `biometric_attempt_log` table (already implemented)
- Include: outcome, similarity score, model version
- Retention: 90 days

#### Operations Logs
- Operational metrics in `operations_metrics_history` table (already implemented)
- Alerts in `operations_alerts` table (already implemented)
- Incidents in `election_incidents` table (already implemented)
- Retention: 1 year

#### Correlation IDs
- Generate unique request ID per API call
- Propagate through all service calls
- Include in all log entries
- Enable request tracing across services

### 11.3 Immutable Recovery Audit

Every recovery action creates an immutable audit record:

```sql
CREATE TABLE recovery_audit_log (
    id BIGSERIAL PRIMARY KEY,
    action_type VARCHAR(32) NOT NULL,      -- DATABASE_RESTORE, FABRIC_RECOVERY, BACKUP_CREATED,
                                            -- BACKUP_VERIFIED, ROLLBACK_PERFORMED, EMERGENCY_SHUTDOWN,
                                            -- CONFIGURATION_CHANGE
    performed_by VARCHAR(64) NOT NULL,
    performed_at TIMESTAMPTZ NOT NULL,
    target_component VARCHAR(32) NOT NULL,  -- postgres, gateway, ai-service, fabric
    details TEXT NOT NULL,
    outcome VARCHAR(16) NOT NULL,           -- SUCCESS, FAILED, PARTIAL
    duration_ms BIGINT,
    metadata TEXT,
    sha256_hash VARCHAR(64) NOT NULL        -- Integrity verification
);
```

These belong alongside existing administrative audit records in `admin_action_log`.

### 11.4 Log Integrity

- SHA-256 hashing for audit exports (already implemented)
- Append-only log storage
- Tamper detection via hash chain

**Evidence**: `AuditAnalyticsService.java:112-126` (SHA-256 computation)

---

## 12. Monitoring Integration

### 12.1 Current Monitoring

- **SystemHealthService**: Component health (Gateway, AI, DB, Fabric)
- **OperationsAlertService**: Alert management (CRITICAL, HIGH, MEDIUM, LOW)
- **SecurityEventService**: Security event tracking
- **DashboardAlertService**: In-memory alert evaluation
- **OperationsMetricsService**: 60-second metrics persistence

**Evidence**: `SystemHealthService.java:17-181`, `OperationsAlertService.java:21-101`, `SecurityEventService.java:19-68`

### 12.2 New Alerts Required

| Alert Type | Source | Severity | Trigger |
|------------|--------|----------|---------|
| Backup Failed | Backup script | CRITICAL | pg_dump failure |
| Backup Success | Backup script | INFO | Backup completed |
| Storage Low | OS/Container | HIGH | Disk < 20% free |
| Replication Lag | PostgreSQL | HIGH | Lag > 30 seconds |
| Recovery Initiated | Recovery script | CRITICAL | Disaster recovery started |
| Recovery Complete | Recovery script | INFO | Recovery successful |
| Circuit Open | Application | HIGH | Circuit breaker tripped |
| Vote Queue Full | Application | MEDIUM | Queue > 1000 items |
| CPU High | Resource monitor | HIGH | CPU > 80% for 5 min |
| Memory High | Resource monitor | HIGH | Memory > 85% for 5 min |
| Disk High | Resource monitor | HIGH | Disk > 80% for 5 min |
| Thread Pool Exhausted | JVM monitor | CRITICAL | All threads busy |
| Connection Pool Exhausted | HikariCP | CRITICAL | All connections used |
| JVM Heap Pressure | JVM monitor | HIGH | Heap > 85% used |

### 12.3 Circuit Breaker Metrics

Expose circuit breaker metrics in Operations Center dashboard:

| Metric | Description |
|--------|-------------|
| Circuit State | OPEN, HALF_OPEN, CLOSED |
| Open Count | Number of times circuit opened |
| Half-Open Count | Number of half-open transitions |
| Failure Rate | Percentage of failed calls |
| Recovery Count | Number of successful recoveries |

**Evidence**: `DashboardAlertService.java:149-155` (current alert evaluation, no circuit metrics)

### 12.4 Health History Retention

Persist health history for trend analysis and capacity planning:

| Granularity | Retention | Use Case |
|-------------|-----------|----------|
| Raw samples | 30 days | Real-time debugging |
| Hourly aggregates | 1 year | Trend analysis |
| Daily aggregates | Indefinite | Capacity planning |

**Implementation**:
- Create `health_history` table with timestamp, component, status, metrics
- Scheduled job aggregates raw data hourly
- Scheduled job aggregates hourly data daily
- Retention policy enforced via scheduled cleanup

**Evidence**: `SystemHealthService.java:39-45` (current 10-second checks, no persistence)

### 12.5 Resource Monitoring

Monitor resource utilization to detect exhaustion before failure:

| Resource | Metric | Alert Threshold |
|----------|--------|-----------------|
| CPU | Usage % | > 80% for 5 minutes |
| Memory | Usage % | > 85% for 5 minutes |
| Disk | Usage % | > 80% for 5 minutes |
| Thread Pool | Active/Total | > 90% utilization |
| JVM Heap | Used/Max | > 85% used |
| Connection Pool | Active/Max | > 90% utilization |
| Fabric Session | Active count | > 100 sessions |
| Outbox Queue | Pending count | > 1000 items |

**Operational failures often begin as resource exhaustion rather than outright service failures.**

### 12.6 Integration Points

- **Dashboard**: Already displays component health (`frontend ui/src/app/admin/operations/health/page.tsx`)
- **Operations Center**: Already displays alerts, incidents, security events (`frontend ui/src/app/admin/operations/page.tsx`)
- **Alert Service**: Already creates/acknowledges/resolves alerts (`OperationsAlertService.java`)
- **Health Service**: Already checks all components (`SystemHealthService.java`)

---

## 13. Operational Runbooks

### 13.1 Database Failure Runbook

1. **Detect**: SystemHealthService reports DB status = CRITICAL
2. **Alert**: OperationsAlertService creates CRITICAL alert
3. **Assess**: Check PostgreSQL container status (`docker ps`)
4. **Recover**:
   - If container crashed: Docker auto-restarts (with restart policy)
   - If data corruption: Restore from latest backup
   - If WAL corruption: Point-in-time recovery
5. **Verify**: `SELECT 1` health check passes
6. **Re-sync**: Fabric is source of truth for votes
7. **Document**: Log incident in election_incidents table
8. **Audit**: Create recovery_audit_log entry (DATABASE_RESTORE)

### 13.2 Fabric Failure Runbook

1. **Detect**: SystemHealthService reports Fabric status = CRITICAL
2. **Alert**: OperationsAlertService creates CRITICAL alert
3. **Assess**: Check Fabric network status, peer connectivity
4. **Recover**:
   - Sandbox mode activates automatically
   - Votes accepted but flagged as sandbox transactions
   - When Fabric recovers: reconcile via outbox pattern
5. **Verify**: `isContractAvailable()` returns true
6. **Document**: Log incident, track sandbox transaction count
7. **Audit**: Create recovery_audit_log entry (FABRIC_RECOVERY)

### 13.3 AI Service Failure Runbook

1. **Detect**: SystemHealthService reports AI status = CRITICAL
2. **Alert**: OperationsAlertService creates HIGH alert
3. **Assess**: Check AI service container, model load status
4. **Recover**:
   - New biometric enrollments blocked
   - Existing verified sessions continue
   - Restart container or rebuild image
5. **Verify**: `/health` endpoint returns `modelLoaded: true`
6. **Document**: Log incident, track affected voters
7. **Audit**: Create recovery_audit_log entry (AI_RECOVERY)

### 13.4 Gateway Failure Runbook

1. **Detect**: SystemHealthService reports Gateway status = CRITICAL
2. **Alert**: OperationsAlertService creates CRITICAL alert
3. **Assess**: Check Core Gateway container, Spring Boot status
4. **Recover**:
   - Docker auto-restarts container
   - In-memory caches rebuild on demand
   - Rate limiter state resets (acceptable)
5. **Verify**: `/actuator/health` returns UP
6. **Document**: Log incident, track downtime
7. **Audit**: Create recovery_audit_log entry (GATEWAY_RECOVERY)

### 13.5 Election Incident Runbook

1. **Detect**: Operations alerts, security events, or manual report
2. **Classify**: Severity (CRITICAL, HIGH, MEDIUM, LOW)
3. **Assign**: ElectionIncidentService tracks assignment
4. **Investigate**: Review logs, metrics, alerts
5. **Resolve**: Implement fix, verify resolution
6. **Document**: Update incident status, add comments
7. **Audit**: Create recovery_audit_log entry (INCIDENT_RESOLVED)

### 13.6 Backup Restore Runbook

1. **Assess**: Determine required recovery point
2. **Locate**: Find appropriate backup from backup_catalog
3. **Verify**: Check backup integrity (SHA-256)
4. **Restore**: Execute pg_restore to target database
5. **Verify**: Run health checks, validate data
6. **Resume**: Restart affected services
7. **Document**: Log recovery action
8. **Audit**: Create recovery_audit_log entry (BACKUP_RESTORED)

### 13.7 Deployment Rollback Runbook

1. **Detect**: Health check failures after deployment
2. **Decide**: Rollback threshold exceeded
3. **Rollback**: Deploy previous image version
4. **Verify**: Health checks pass
5. **Document**: Log rollback reason, affected version
6. **Audit**: Create recovery_audit_log entry (ROLLBACK_PERFORMED)

### 13.8 Emergency Shutdown Runbook

1. **Detect**: Critical security incident or data integrity threat
2. **Decide**: Emergency shutdown authorized
3. **Execute**: Stop all services, preserve state
4. **Secure**: Ensure no data loss
5. **Investigate**: Determine root cause
6. **Document**: Full incident report
7. **Audit**: Create recovery_audit_log entry (EMERGENCY_SHUTDOWN)

---

## 14. Election Integrity During Failure

### 14.1 No Duplicate Votes

**Current Implementation**: `VoteController.java:83-86` checks `hasVoted` flag before allowing vote.

**Enhancement**: Database-backed idempotency via `vote_request_log` table prevents duplicate submissions from network retries.

**Evidence**: `VoteController.java:83-86` (double-vote check)

### 14.2 No Lost Votes

**Current Implementation**: `VoteController.java:88-96` sets `hasVoted = true` before Fabric submission. On Fabric failure, rolls back to `false`.

**Enhancement**: Outbox pattern ensures reliable delivery. Votes are never lost - they are either on Fabric or in outbox for retry.

**Evidence**: `VoteController.java:98-123` (vote submission + rollback)

### 14.3 No Replay Attacks

**Current Implementation**: Session tokens have TTL (15 minutes), biometric claim required, one-time ballot tokens.

**Enhancement**: Add nonce tracking for additional replay protection.

**Evidence**: `SessionTokenService.java:36-49` (token with exp and jti), `VoteController.java:71` (input validation)

### 14.4 No Inconsistent Vote Totals

**Current Implementation**: Fabric is source of truth for vote tallies. Database stores `hasVoted` flag only.

**Enhancement**: Periodic reconciliation between Fabric and database state.

**Evidence**: `VotingLedgerService.java:63-86` (Fabric submission), `ElectionResultsCacheService.java:28-38` (results query)

### 14.5 Recovery After Fabric Outage

**Current Implementation**: Sandbox mode (`VotingLedgerService.java:77-80`) accepts votes when Fabric is offline.

**Enhancement**: Outbox pattern queues sandbox transactions for replay when Fabric recovers. Track pending transactions.

**Evidence**: `VotingLedgerService.java:77-80` (sandbox fallback)

### 14.6 Recovery After Gateway Restart

**Current Implementation**: In-memory state lost. Database state preserved.

**Enhancement**: Caches rebuild on demand. Rate limiter resets. No vote data lost.

**Evidence**: `DashboardCacheService.java:19-34` (Caffeine cache), `RateLimitService.java:17` (in-memory state)

### 14.7 Recovery After Database Restart

**Current Implementation**: PostgreSQL data persisted (if volumes configured). Docker auto-restarts container.

**Enhancement**: Add named volumes for data persistence. Implement WAL archiving for point-in-time recovery.

**Evidence**: `docker-compose.yml:41-50` (PostgreSQL config, no volumes)

---

## 15. Security During Recovery

### 15.1 Secret Recovery

- Session secrets stored in environment variables
- Admin bootstrap credentials in `.env`
- Recovery: Re-inject secrets via environment

**Evidence**: `.env:5` (APP_AUTH_SESSION_SECRET), `Core Gateway/src/main/resources/application.yml:33` (session secret config)

### 15.2 Key Rotation

- Session token signing key: Rotate quarterly
- Admin token signing key: Rotate quarterly
- Backup encryption key: Rotate quarterly
- Process: Generate new key, update all services, revoke old tokens

### 15.3 Backup Encryption

- GPG encryption for backup files
- Separate encryption key from application secrets
- Key stored in secure key management system

### 15.4 Recovery Authentication

- Admin access required for recovery operations
- Audit logging of all recovery actions
- Multi-admin approval for critical recovery

### 15.5 Audit Trail Preservation

- Audit logs stored in `admin_action_log` table
- SHA-256 integrity hash on exports
- Recovery actions logged with admin identity
- Immutable recovery audit records in `recovery_audit_log`

**Evidence**: `AdminBootstrapRunner.java:72-82` (audit log creation), `AuditAnalyticsService.java:100-109` (SHA-256 hash)

---

## 16. Production Readiness Assessment

### 16.1 Infrastructure
- [ ] Docker restart policies configured
- [ ] Docker health checks configured
- [ ] Container resource limits defined
- [ ] Persistent volumes for PostgreSQL
- [ ] Network isolation between services
- [ ] Container security hardening (non-root, read-only)

### 16.2 Security
- [ ] Secrets management (not plaintext .env)
- [ ] Session token rotation schedule
- [ ] Backup encryption
- [ ] Recovery authentication workflow
- [ ] Audit trail integrity verification

### 16.3 Monitoring
- [ ] All component health checks active
- [ ] Alert escalation configured
- [ ] Metrics persistence verified
- [ ] Log aggregation implemented
- [ ] Correlation IDs for request tracing
- [ ] Circuit breaker metrics exposed
- [ ] Resource monitoring (CPU, memory, disk, thread pool, JVM heap, connection pool)
- [ ] Health history retention configured

### 16.4 Operations
- [ ] Backup schedule verified
- [ ] Restore procedure tested
- [ ] Runbooks documented
- [ ] Incident response process defined
- [ ] Communication plan for election day
- [ ] Recovery drills scheduled

### 16.5 Backup
- [ ] Full backup schedule (daily)
- [ ] Incremental backup schedule (6-hourly)
- [ ] Point-in-time recovery tested
- [ ] Backup verification automated
- [ ] Backup retention policy defined
- [ ] Backup catalog implemented

### 16.6 Recovery
- [ ] Database recovery tested
- [ ] Fabric recovery tested
- [ ] Gateway recovery tested
- [ ] AI service recovery tested
- [ ] Full system recovery tested
- [ ] Outbox pattern implemented
- [ ] Idempotency keys database-backed

### 16.7 Deployment
- [ ] Rolling deployment procedure defined
- [ ] Zero-downtime deployment tested
- [ ] Rollback procedure tested
- [ ] Pre-deployment verification gates
- [ ] Post-deployment verification gates
- [ ] Configuration version tracking

### 16.8 Performance
- [ ] Load testing completed
- [ ] Capacity planning documented
- [ ] Scaling boundaries identified
- [ ] Bottleneck analysis completed
- [ ] Performance monitoring configured

### 16.9 Compliance
- [ ] Audit trail integrity verified
- [ ] Data retention policy enforced
- [ ] Access control verified
- [ ] Encryption at rest confirmed
- [ ] Encryption in transit confirmed
- [ ] Immutable recovery audit records

### 16.10 Operational Maturity
- [ ] All runbooks tested
- [ ] Team trained on recovery procedures
- [ ] Communication channels established
- [ ] Escalation matrix defined
- [ ] Post-election review planned
- [ ] Chaos testing completed

---

## 17. Prioritized Implementation Roadmap

### Phase 1: Critical (Pre-Election) - 1-2 weeks

1. **Docker Compose Hardening**
   - Add restart policies (`restart: unless-stopped`)
   - Add health checks for all services
   - Add persistent volumes for PostgreSQL
   - Add resource limits

2. **Database Backup**
   - Implement pg_dump backup script
   - Configure WAL archiving
   - Test backup/restore procedure
   - Create backup_catalog table

3. **Startup Ordering**
   - Add `condition: service_healthy` to depends_on
   - Verify startup sequence

### Phase 2: Important (Election Week) - 1 week

4. **Transaction Recovery**
   - Implement outbox pattern (outbox_events table)
   - Add database-backed idempotency (vote_request_log table)
   - Implement retry logic for Fabric calls
   - Add circuit breaker pattern

5. **Logging Enhancement**
   - Add correlation IDs
   - Structured JSON logging
   - Log rotation configuration
   - Create recovery_audit_log table

6. **Monitoring Enhancement**
   - Add backup health alerts
   - Add storage capacity alerts
   - Verify alert escalation
   - Add circuit breaker metrics
   - Add resource monitoring

### Phase 3: Recommended (Post-Election) - 2-4 weeks

7. **High Availability**
   - PostgreSQL streaming replication
   - Gateway load balancing
   - AI service scaling

8. **Secrets Management**
   - Implement secrets rotation
   - Environment separation
   - Production secret injection
   - Configuration version tracking

9. **Disaster Recovery Testing**
   - Full DR drill
   - Recovery time verification
   - Recovery point verification
   - Chaos testing (kill Gateway, PostgreSQL, Fabric, AI during voting)

10. **Health History**
    - Implement health_history table
    - Hourly/daily aggregation
    - Retention policy enforcement
