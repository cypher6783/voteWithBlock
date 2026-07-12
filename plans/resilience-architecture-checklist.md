# Resilience Architecture Checklist

Production readiness checklist for Votetrust Ledger Disaster Recovery, Backup, High Availability, and Resilience Architecture.

---

## Infrastructure

- [ ] Docker restart policies configured (`restart: unless-stopped` for all services)
- [ ] Docker health checks configured for PostgreSQL (`pg_isready`)
- [ ] Docker health checks configured for Core Gateway (`/actuator/health`)
- [ ] Docker health checks configured for AI Service (`/health`)
- [ ] Docker health checks configured for Frontend UI (`curl localhost:3000`)
- [ ] Container resource limits defined (CPU and memory)
- [ ] Persistent volumes configured for PostgreSQL data
- [ ] Network isolation verified between services
- [ ] Container security hardening (non-root user, read-only filesystem)
- [ ] Startup dependency ordering with health conditions (`condition: service_healthy`)

## Security

- [ ] Secrets management implemented (not plaintext .env)
- [ ] Session token signing key rotation schedule defined
- [ ] Admin token signing key rotation schedule defined
- [ ] Backup encryption configured (GPG or equivalent)
- [ ] Recovery authentication workflow documented
- [ ] Audit trail integrity verification (SHA-256)
- [ ] Rate limiter state recovery after restart
- [ ] Biometric data encryption at rest verified
- [ ] HTTPS/TLS configured for production

## Monitoring

- [ ] SystemHealthService health checks active (10-second interval)
- [ ] Component health tracking: Gateway, AI, Database, Fabric
- [ ] Alert escalation configured (CRITICAL, HIGH, MEDIUM, LOW)
- [ ] OperationsAlertService functional for alert CRUD
- [ ] SecurityEventService recording security events
- [ ] Metrics persistence to database (OperationsMetricsService)
- [ ] Log aggregation implemented (ELK, Fluentd, or equivalent)
- [ ] Correlation IDs for request tracing across services
- [ ] Backup health alerts configured
- [ ] Storage capacity alerts configured
- [ ] Replication lag alerts configured (if applicable)
- [ ] Circuit breaker metrics exposed (state, open count, failure rate, recovery count)
- [ ] Resource monitoring configured (CPU, memory, disk, thread pool, JVM heap, connection pool)
- [ ] Health history retention configured (raw: 30 days, hourly: 1 year, daily: indefinite)

## Operations

- [ ] Backup schedule verified (daily full, 6-hourly incremental)
- [ ] Backup restore procedure tested
- [ ] Operational runbooks documented for all failure scenarios
- [ ] Incident response process defined
- [ ] Communication plan for election day established
- [ ] Team trained on recovery procedures
- [ ] Escalation matrix defined
- [ ] Post-election review planned
- [ ] Recovery drills scheduled (monthly PostgreSQL, monthly Fabric, quarterly full DR, pre-election rehearsal)

## Backup

- [ ] Full backup schedule implemented (daily at 02:00 UTC)
- [ ] Incremental backup schedule implemented (6-hourly)
- [ ] Point-in-time recovery capability tested
- [ ] Backup verification automated (weekly restore test)
- [ ] Backup retention policy defined (30 days daily, 12 months weekly)
- [ ] Backup encryption key management documented
- [ ] Backup integrity verification (SHA-256 checksums)
- [ ] Offsite backup storage configured
- [ ] Backup catalog table implemented (backup_id, type, size, sha256, location, verified)

## Recovery

- [ ] Database recovery tested (pg_restore from backup)
- [ ] Fabric recovery tested (network restart, channel rejoin)
- [ ] Gateway recovery tested (container auto-restart)
- [ ] AI Service recovery tested (container auto-restart)
- [ ] Full system recovery tested (end-to-end drill)
- [ ] Recovery Time Objectives (RTO) verified: DB 15min, Gateway 2min, AI 2min, Fabric 10min
- [ ] Recovery Point Objectives (RPO) verified: DB 6hr, Votes 0 (blockchain source of truth)
- [ ] Rollback recovery for failed votes implemented
- [ ] Sandbox transaction reconciliation procedure documented
- [ ] Outbox pattern implemented (outbox_events table, background processor)
- [ ] Database-backed idempotency implemented (vote_request_log table)
- [ ] Recovery drills executed (monthly PostgreSQL, monthly Fabric, quarterly full DR)

## Deployment

- [ ] Rolling deployment procedure defined
- [ ] Zero-downtime deployment tested
- [ ] Rollback procedure tested
- [ ] Pre-deployment verification gates configured
  - [ ] Database migration validation
  - [ ] Configuration validation
  - [ ] Secret validation
  - [ ] Health validation
- [ ] Post-deployment verification gates configured
  - [ ] Gateway healthy
  - [ ] AI healthy
  - [ ] Fabric connected
  - [ ] Database healthy
  - [ ] Synthetic login
  - [ ] Synthetic vote (staging only)
- [ ] Image versioning strategy defined
- [ ] Deployment approval workflow documented
- [ ] Configuration version tracking implemented (`app.configuration.version`)
- [ ] Actuator `/actuator/info` endpoint exposed

## Performance

- [ ] Load testing completed for election-day traffic
- [ ] Capacity planning documented
- [ ] Scaling boundaries identified
- [ ] Bottleneck analysis completed
- [ ] Performance monitoring configured
- [ ] Database connection pool tuning verified
- [ ] Caffeine cache sizing verified
- [ ] Rate limiter capacity verified
- [ ] Resource exhaustion monitoring active (CPU, memory, disk, thread pool, JVM heap, connection pool)

## Compliance

- [ ] Audit trail integrity verified (SHA-256 hashing)
- [ ] Data retention policy enforced
- [ ] Access control verified (admin roles, RBAC)
- [ ] Encryption at rest confirmed (database, backups)
- [ ] Encryption in transit confirmed (TLS)
- [ ] Biometric data handling compliant
- [ ] Voter privacy protections verified
- [ ] Election integrity guarantees documented
- [ ] Immutable recovery audit records implemented (recovery_audit_log table)

## Operational Maturity

- [ ] All runbooks tested in staging environment
- [ ] Team trained on recovery procedures
- [ ] Communication channels established (Slack, email, phone)
- [ ] Escalation matrix documented
- [ ] Post-election review meeting scheduled
- [ ] Lessons learned process defined
- [ ] Continuous improvement cycle established
- [ ] Chaos testing completed (Gateway kill, PostgreSQL kill, Fabric disconnect, AI stop, latency simulation, packet loss)

---

## Database Schema Requirements

### outbox_events (Outbox Pattern)
```sql
CREATE TABLE outbox_events (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(32) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ,
    retry_count INT DEFAULT 0,
    max_retries INT DEFAULT 3,
    last_error TEXT,
    metadata TEXT
);
```

### vote_request_log (Repository-Based Idempotency)
```sql
CREATE TABLE vote_request_log (
    id BIGSERIAL PRIMARY KEY,
    idempotency_key VARCHAR(64) UNIQUE NOT NULL,
    voter_id_hash VARCHAR(64) NOT NULL,
    election_id UUID NOT NULL,
    candidate_id UUID NOT NULL,
    status VARCHAR(16) NOT NULL,
    transaction_id VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ,
    retry_count INT DEFAULT 0,
    last_error TEXT
);
```

### backup_catalog
```sql
CREATE TABLE backup_catalog (
    id BIGSERIAL PRIMARY KEY,
    backup_id VARCHAR(64) UNIQUE NOT NULL,
    backup_type VARCHAR(32) NOT NULL,
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

### recovery_audit_log (Immutable Recovery Audit)
```sql
CREATE TABLE recovery_audit_log (
    id BIGSERIAL PRIMARY KEY,
    action_type VARCHAR(32) NOT NULL,
    performed_by VARCHAR(64) NOT NULL,
    performed_at TIMESTAMPTZ NOT NULL,
    target_component VARCHAR(32) NOT NULL,
    details TEXT NOT NULL,
    outcome VARCHAR(16) NOT NULL,
    duration_ms BIGINT,
    metadata TEXT,
    sha256_hash VARCHAR(64) NOT NULL
);
```

### health_history (Health History Retention)
```sql
CREATE TABLE health_history (
    id BIGSERIAL PRIMARY KEY,
    component VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    metrics JSONB,
    recorded_at TIMESTAMPTZ NOT NULL
);
```

---

## Recovery Action Types

| Action Type | Description |
|-------------|-------------|
| DATABASE_RESTORE | Database restored from backup |
| FABRIC_RECOVERY | Fabric network recovered |
| AI_RECOVERY | AI service recovered |
| GATEWAY_RECOVERY | Gateway service recovered |
| BACKUP_CREATED | Backup successfully created |
| BACKUP_VERIFIED | Backup integrity verified |
| BACKUP_RESTORED | Backup restored to database |
| ROLLBACK_PERFORMED | Deployment rolled back |
| EMERGENCY_SHUTDOWN | Emergency shutdown executed |
| CONFIGURATION_CHANGE | Configuration modified |
| INCIDENT_RESOLVED | Incident resolved |

---

## Evidence References

| Item | Evidence File | Evidence Location |
|------|---------------|-------------------|
| No Docker restart policies | `docker-compose.yml` | Lines 1-70 (no `restart:` key) |
| No Docker health checks | `docker-compose.yml` | Lines 1-70 (no `healthcheck:` key) |
| No persistent volumes | `docker-compose.yml` | Lines 41-50 (PostgreSQL config) |
| Health checks exist | `SystemHealthService.java` | Lines 39-45 (`@Scheduled(fixedRate = 10000)`) |
| Actuator configured | `application.yml` | Lines 54-62 (management endpoints) |
| AI health endpoint | `ai-verification/src/main.py` | Lines 74-80 (`/health` endpoint) |
| Double-vote prevention | `VoteController.java` | Lines 83-86 (`hasVoted` check) |
| Vote rollback on failure | `VoteController.java` | Lines 113-118 (rollback recovery) |
| Sandbox fallback | `VotingLedgerService.java` | Lines 77-80 (sandbox mode) |
| PII masking | `SafeLog.java` | Lines 7-16 (id masking) |
| Audit integrity | `AuditAnalyticsService.java` | Lines 112-126 (SHA-256 computation) |
| Secrets in plaintext | `.env` | Lines 1-14 (hardcoded secrets) |
| Rate limiting | `RateLimitService.java` | Lines 22-27 (12 attempts/60s) |
| Optimistic locking | `Election.java` | Line 42 (`@Version` annotation) |
| Metrics persistence | `OperationsMetricsService.java` | Lines 43-68 (60-second interval) |
| In-memory metrics | `DashboardMetricsService.java` | Lines 16-17 (AtomicLong counters) |
| No outbox pattern | `VoteController.java` | Lines 88-123 (direct DB + Fabric) |
| No idempotency keys | `VoteController.java` | Lines 58-124 (no idempotency) |
| No config version | `application.yml` | Lines 1-62 (no version tracking) |
| No /actuator/info | `application.yml` | Lines 54-62 (only health exposed) |
| No resource monitoring | `SystemHealthService.java` | Lines 17-181 (health only) |
| No health history | `SystemHealthService.java` | Lines 34-37 (volatile state only) |
| No circuit breaker | `VotingLedgerService.java` | Lines 67-85 (no resilience patterns) |
