# Performance Validation Plan

Performance, Load, Scalability & Capacity Validation for Votetrust Ledger.

---

## Metadata

| Field | Value |
|-------|-------|
| Analysis Date | 2026-06-28 |
| Repository | Votetrust Ledger |
| Branch | Main |
| Commit Hash | 5fd1994 |
| Repository Version | 0.0.1-SNAPSHOT |
| Planner | MiMoCode Agent |
| Validation Scope | Performance, Load, Scalability & Capacity |

---

## 1. Executive Summary

Votetrust Ledger is functionally complete across 7 architecture milestones. This plan evaluates production-scale performance readiness. The system uses Caffeine caching, scheduled health checks, rate limiting, and pessimistic/optimistic locking. Key gaps include: no JVM tuning, default HikariCP pool (10 connections), no custom thread pools, no load testing, no @Async, RestTemplate without timeouts, and unbounded rate limiter map.

**Evidence**: `docker-compose.yml` (resource limits), `DashboardCacheService.java` (caching), `SystemHealthService.java` (health checks), `RateLimitService.java` (rate limiting), `application.yml` (configuration)

---

## 2. Current Repository Performance Capabilities

### Implemented

| Capability | Evidence | Status |
|------------|----------|--------|
| Caffeine caching (4 caches) | `DashboardCacheService.java`, `ElectionResultsCacheService.java` | IMPLEMENTED |
| Rate limiting (sliding window) | `RateLimitService.java:17` | IMPLEMENTED |
| Pessimistic locking | `VoterRepository.java:13` | IMPLEMENTED |
| Optimistic locking | `Election.java:41`, `Candidate.java:36` | IMPLEMENTED |
| Scheduled health checks | `SystemHealthService.java:49` | IMPLEMENTED |
| Docker resource limits | `docker-compose.yml:19-113` | IMPLEMENTED |
| Pagination | Multiple services | IMPLEMENTED |
| Health monitoring | `SystemHealthService.java` | IMPLEMENTED |
| Metrics persistence | `OperationsMetricsService.java:43` | IMPLEMENTED |

### Missing

| Capability | Evidence | Status |
|------------|----------|--------|
| JVM tuning | NO EVIDENCE FOUND | NOT_IMPLEMENTED |
| HikariCP pool tuning | NO EVIDENCE FOUND (defaults: maxPoolSize=10) | NOT_IMPLEMENTED |
| Custom thread pools | NO EVIDENCE FOUND | NOT_IMPLEMENTED |
| @Async execution | NO EVIDENCE FOUND | NOT_IMPLEMENTED |
| RestTemplate timeouts | NO EVIDENCE FOUND | NOT_IMPLEMENTED |
| Load testing | NO EVIDENCE FOUND | NOT_IMPLEMENTED |
| Stress testing | NO EVIDENCE FOUND | NOT_IMPLEMENTED |
| JVM metrics | NO EVIDENCE FOUND | NOT_IMPLEMENTED |
| Cache hit ratio metrics | NO EVIDENCE FOUND | NOT_IMPLEMENTED |
| Database query metrics | NO EVIDENCE FOUND | NOT_IMPLEMENTED |
| Outbox queue depth metrics | NO EVIDENCE FOUND | NOT_IMPLEMENTED |
| Connection leak detection | NO EVIDENCE FOUND | NOT_IMPLEMENTED |
| Distributed caching | NO EVIDENCE FOUND | NOT_IMPLEMENTED |

---

## 3. Performance Architecture

### Request Lifecycle (Verified)

```
Frontend (Next.js)
  -> HTTP REST -> Core Gateway (Spring Boot)
    -> JDBC -> PostgreSQL (HikariCP pool, default 10)
    -> HTTP -> AI Service (RestTemplate, no timeout)
    -> gRPC -> Fabric (Fabric Gateway SDK)
  -> Caffeine cache (5-30s TTL)
  -> Background: @Scheduled tasks (4 tasks, single thread)
```

### Synchronous Operations (Verified)

| Operation | File | Latency Path |
|-----------|------|--------------|
| Voter login | `AuthController.java:62-91` | DB query + password hash |
| Biometric challenge | `AuthController.java:93-98` | DB write |
| Biometric confirm | `AuthController.java:100-131` | AI call + DB read + DB write |
| Vote submission | `VoteController.java:58-124` | DB read + DB write + outbox write |
| Election CRUD | `ElectionAdminController.java` | DB read/write |
| Dashboard fetch | `PublicDashboardController.java:24-36` | Cache check + DB/Fabric query |

### Asynchronous Operations (Verified)

| Operation | File | Interval |
|-----------|------|----------|
| Outbox processing | `OutboxProcessorService.java:23` | 5s |
| Health checks | `SystemHealthService.java:49` | 10s |
| Metrics persistence | `OperationsMetricsService.java:43` | 60s |
| Election lifecycle | `ElectionLifecycleScheduler.java:24` | 60s |

### Critical Latency Paths (Verified)

| Path | Components | Risk |
|------|------------|------|
| Vote submission | RateLimit -> SessionToken -> InputValidation -> DB -> Outbox -> Fabric | Low (outbox decouples Fabric) |
| Biometric verify | RateLimit -> PreAuth -> Challenge -> AI Service -> Encryption -> DB | HIGH (AI call blocks) |
| Dashboard refresh | Cache -> ElectionMetrics -> FabricResultsCache | Low (cache hit) |
| Health check | DB (SELECT 1) -> AI (HTTP GET) -> Fabric (isContractAvailable) | Medium (AI timeout) |

---

## 4. Scalability Assessment

### Horizontal Scalability (Verified)

| Component | Stateless | Scale Strategy | Evidence |
|-----------|-----------|----------------|----------|
| Frontend UI | Yes | CDN + multiple instances | `frontend ui/Dockerfile` (static build) |
| Core Gateway | Partial | Load balancer + shared DB | `SecurityConfig.java` (stateless sessions) |
| AI Service | Yes | Multiple instances | Stateless inference |
| PostgreSQL | No | Primary + replicas | Single instance in docker-compose |
| Fabric | No | Multi-org | External network |

### Vertical Scalability (Verified)

| Component | Current Limit | Evidence |
|-----------|---------------|----------|
| Core Gateway | 1G RAM, 1 CPU | `docker-compose.yml:59-63` |
| PostgreSQL | 2G RAM, 2 CPU | `docker-compose.yml:84-88` |
| AI Service | 2G RAM, 2 CPU | `docker-compose.yml:19-23` |
| Frontend UI | 512M RAM, 0.5 CPU | `docker-compose.yml:109-113` |

### Scaling Blockers (Verified)

| Blocker | Evidence | Impact |
|---------|----------|--------|
| Default HikariCP pool (10 connections) | `application.yml:4-12` (no pool config) | Database bottleneck under load |
| Single-threaded scheduler | `@Scheduled` annotations (no thread pool config) | Background task contention |
| No JVM tuning | `Core Gateway/Dockerfile:5` (no flags) | Suboptimal memory usage |
| RestTemplate without timeouts | `FaceVerificationService.java:28` | Thread starvation on AI failure |
| Rate limiter unbounded map | `RateLimitService.java:17` | Memory leak under diverse traffic |

---

## 5. Concurrency Assessment

### Implemented Concurrency (Verified)

| Pattern | File | Evidence |
|---------|------|----------|
| ConcurrentHashMap | `RateLimitService.java:17` | Thread-safe rate limit buckets |
| AtomicInteger | `SystemHealthService.java:35-42` | 8 thread-safe counters |
| AtomicLong | `DashboardMetricsService.java:18-26` | 8 thread-safe counters |
| CopyOnWriteArrayList | `DashboardAlertService.java:18` | Thread-safe alert list |
| volatile | `SystemHealthService.java:44-47` | Thread-safe status reads |
| synchronized | `RateLimitService.java:32` | Per-key locking |
| Pessimistic locking | `VoterRepository.java:13` | `SELECT ... FOR UPDATE` |
| Optimistic locking | `Election.java:41`, `Candidate.java:36` | `@Version` annotation |

### Concurrency Risks (Verified)

| Risk | File | Evidence | Impact |
|------|------|----------|--------|
| Non-atomic latency counters | `VotingLedgerService.java:35-38` | `long` fields read/written without synchronization | Data races under concurrent vote submission |
| Single-threaded scheduler | `@Scheduled` annotations | All 4 tasks share one thread | Long-running task blocks others |
| Unbounded rate limiter map | `RateLimitService.java:17` | Never evicts expired keys | Memory leak under diverse traffic |

### Recommended Thread Pool Architecture (Planned)

```
TaskSchedulerPool (4 threads)
  ├── OutboxProcessorService (5s interval)
  ├── SystemHealthService (10s interval)
  ├── OperationsMetricsService (60s interval)
  └── ElectionLifecycleScheduler (60s interval)

FabricSubmissionPool (2 threads)
  └── OutboxProcessorService Fabric calls

HealthMonitoringPool (1 thread)
  └── SystemHealthService AI health checks
```

**Rationale**: Separating scheduler threads from Fabric submission threads prevents slow Fabric calls from blocking health checks. Dedicated health monitoring pool ensures AI timeout doesn't affect other scheduled tasks.

---

## 6. Database Performance

### Implemented (Verified)

| Feature | File | Evidence |
|---------|------|----------|
| Pessimistic locking | `VoterRepository.java:13` | `@Lock(LockModeType.PESSIMISTIC_WRITE)` |
| Optimistic locking | `Election.java:41`, `Candidate.java:36` | `@Version` |
| Pagination | Multiple services | `PageRequest.of(page, size)` |
| Indexes (15+) | `database_migrations_v4_operations.sql:119-141` | Performance indexes |
| open-in-view disabled | `application.yml:12` | Avoids lazy-loading sessions |

### Not Implemented (Verified)

| Feature | Evidence | Impact |
|---------|----------|--------|
| HikariCP pool tuning | NO EVIDENCE FOUND (defaults: maxPoolSize=10) | Connection exhaustion under load |
| Slow query logging | NO EVIDENCE FOUND | No visibility into query performance |
| Query performance monitoring | NO EVIDENCE FOUND | No baseline metrics |
| Connection leak detection | NO EVIDENCE FOUND | Silent resource exhaustion |
| Database replication | NO EVIDENCE FOUND | Single point of failure |

### Database Bottlenecks (Verified)

| Bottleneck | Evidence | Mitigation |
|------------|----------|------------|
| Unbounded export query | `AuditAnalyticsService.java:80` `PageRequest.of(0, Integer.MAX_VALUE)` | Add pagination limit |
| Default connection pool | `application.yml:4-12` (no HikariCP config) | Configure pool size |
| Pessimistic lock contention | `VoterRepository.java:13` | Monitor lock wait times |

---

## 7. Fabric Performance

### Implemented (Verified)

| Feature | File | Evidence |
|---------|------|----------|
| Connection init | `VotingLedgerService.java:41-57` | `@PostConstruct` |
| Connection cleanup | `VotingLedgerService.java:88-94` | `@PreDestroy` |
| Sandbox fallback | `VotingLedgerService.java:77-80` | When contract unavailable |
| Latency tracking | `VotingLedgerService.java:108-126` | Average submission/query latency |
| Outbox decoupling | `OutboxProcessorService.java:23-55` | Background processing |

### Not Implemented (Verified)

| Feature | Evidence | Impact |
|---------|----------|--------|
| Connection pooling | NO EVIDENCE FOUND | New connection per submission |
| Transaction batching | NO EVIDENCE FOUND | Individual submissions only |
| gRPC connection reuse | NO EVIDENCE FOUND | Connection overhead |

### Expected Throughput Limitations (Operational Assumption)

- Single Fabric peer configuration (`network-config.json`)
- Individual transaction submission (no batching)
- Sandbox mode for offline Fabric (no real throughput measurement)

### Fabric Backpressure Strategy (Verified)

```
Incoming Vote
  -> VoteController (synchronous)
    -> OutboxEventRepository.save (PENDING)
      -> OutboxProcessorService (5s polling)
        -> Queue Length Check
          -> Alert Threshold (OperationsAlertService)
            -> Backpressure Warning
              -> Operations Center Dashboard
```

**Current Implementation**: Outbox pattern decouples vote submission from Fabric. If Fabric becomes slower than incoming votes, the outbox queue grows. No explicit backpressure alert exists.

**Recommended Enhancement**: Monitor `outbox_events` count where `status = 'PENDING'`. Alert when count exceeds threshold (e.g., 100 pending events).

---

## 8. AI Service Performance

### Implemented (Verified)

| Feature | File | Evidence |
|---------|------|----------|
| Model loading | `ai-verification/src/main.py:29-40` | InsightFace `buffalo_l`, CPU |
| Health endpoint | `ai-verification/src/main.py:74-80` | `GET /health` |
| Image quality validation | `ai-verification/src/main.py:92-134` | Resolution, blur, brightness, pose |
| Embedding generation | `ai-verification/src/main.py:156-167` | 512-d normalized |

### Not Implemented (Verified)

| Feature | Evidence | Impact |
|---------|----------|--------|
| Request queuing | NO EVIDENCE FOUND | Synchronous blocking |
| Connection pooling | NO EVIDENCE FOUND | New connection per request |
| GPU acceleration | NO EVIDENCE FOUND | CPU-only inference |
| Batch inference | NO EVIDENCE FOUND | One request at a time |

### Scaling Characteristics (Operational Assumption)

- CPU-only inference limits throughput
- Model loading at startup (one-time cost)
- Synchronous request/response pattern
- Stateless: can run multiple instances

### AI Bottleneck Chain (Verified)

```
Camera Upload
  -> HTTP POST to AI Service
    -> Image Decode (base64 -> numpy)
      -> Quality Validation (resolution, blur, brightness, pose)
        -> Face Detection (InsightFace)
          -> Embedding Generation (512-d)
            -> Response to Gateway
```

**Critical Path**: Face detection + embedding generation (CPU-bound)
**Bottleneck**: Single-threaded FastAPI worker, CPU-only inference

---

## 9. Dashboard Performance

### Implemented (Verified)

| Feature | File | Evidence |
|---------|------|----------|
| Public polling | `dashboard/page.tsx:39` | 30-second interval |
| Admin polling | `admin/operations/page.tsx:37` | 10-second interval |
| Health polling | `admin/operations/health/page.tsx:23` | 10-second interval |
| Security polling | `admin/operations/security/page.tsx` | 5-second interval |
| Public cache | `DashboardCacheService.java:19` | 30s TTL, max 100 |
| Admin cache | `DashboardCacheService.java:25` | 10s TTL, max 10 |
| Health cache | `DashboardCacheService.java:31` | 5s TTL, max 10 |

### Polling Load (Operational Assumption)

| Dashboard | Interval | Per-User Load |
|-----------|----------|---------------|
| Public | 30s | 2 req/min |
| Admin | 10s | 6 req/min |
| Health | 10s | 6 req/min |
| Security | 5s | 12 req/min |

### Aggregate Infrastructure Load (Operational Assumption)

```
100 Admin Users x 6 req/min (10s polling)
  = 600 Admin Requests/minute

+ 500 Public Users x 2 req/min (30s polling)
  = 1,000 Public Requests/minute

= Total: 1,600 Requests/minute to Gateway

+ 4 Scheduled Tasks (background)
  = 1,600 + ~10 = ~1,610 total requests/minute
```

---

## 10. Cache Assessment

### Implemented (Verified)

| Cache | TTL | Max Size | Effectiveness |
|-------|-----|----------|---------------|
| Public Dashboard | 30s | 100 | High (reduces DB/Fabric queries) |
| Admin Dashboard | 10s | 10 | Medium (shorter TTL) |
| Health Metrics | 5s | 10 | Low (very short TTL) |
| Election Results | 10s | 100 | High (prevents Fabric spam) |

### Not Implemented (Verified)

| Feature | Evidence | Impact |
|---------|----------|--------|
| Cache hit ratio metrics | NO EVIDENCE FOUND | No visibility into cache effectiveness |
| Cache warming | NO EVIDENCE FOUND | Cold start penalty |
| Distributed caching | NO EVIDENCE FOUND | Single-instance only |
| Cache invalidation events | NO EVIDENCE FOUND | Manual invalidation only |

### Cache Warm-Up Order (Planned)

After Gateway restart, caches rebuild in this order:
1. **Election cache** (ElectionResultsCacheService) - needed for dashboard
2. **Dashboard cache** (DashboardCacheService public) - needed for public view
3. **Admin cache** (DashboardCacheService admin) - needed for admin view
4. **Health cache** (DashboardCacheService health) - needed for monitoring

**Rationale**: Election data is prerequisite for all dashboard views. Public dashboard has highest user count. Health cache is lowest priority (5s TTL rebuilds quickly).

---

## 11. Resource Utilization

### Docker Resources (Verified)

| Service | Memory | CPUs | JVM Heap (estimated) |
|---------|--------|------|---------------------|
| Core Gateway | 1G | 1.0 | ~256MB (25% default) |
| PostgreSQL | 2G | 2.0 | N/A |
| AI Service | 2G | 2.0 | N/A (Python) |
| Frontend UI | 512M | 0.5 | N/A (Node.js) |

### JVM Settings (Verified)

**Evidence**: `Core Gateway/Dockerfile:5`
```
ENTRYPOINT ["java", "-jar", "app.jar"]
```

No JVM tuning flags. Spring Boot defaults apply:
- Heap: 25% of container memory (~256MB)
- GC: G1GC (JDK 17 default)
- No container-aware flags

### Expected Resource Bottlenecks (Verified)

| Bottleneck | Evidence | Impact |
|------------|----------|--------|
| Gateway heap (256MB) | `Dockerfile:5` (no -Xmx) | OOM under load |
| Default connection pool (10) | `application.yml:4-12` | Connection exhaustion |
| Single-threaded scheduler | `@Scheduled` annotations | Task contention |
| Rate limiter memory | `RateLimitService.java:17` (unbounded) | Memory leak |

---

## 12. Capacity Planning

### Methodology

Capacity estimates are based on repository evidence of:
- HikariCP default pool size (10 connections)
- Pessimistic locking on voter lookups
- Synchronous AI verification calls
- Single Gateway instance (1 CPU / 1G RAM)
- Default JVM heap (~256MB)

**Confidence Level**: LOW (no load testing has been performed)

### Concurrent Users (Operational Assumption)

| Scenario | Estimated Limit | Basis | Confidence |
|----------|-----------------|-------|------------|
| Concurrent voters | ~50 | DB pool (10) + pessimistic locking + synchronous AI | LOW |
| Concurrent admin sessions | ~20 | DB pool + admin token validation + session repo queries | LOW |
| Concurrent dashboard users | ~100 | Caffeine cache absorbs reads, minimal DB impact | MEDIUM |
| Concurrent biometric requests | ~10 | AI service single-threaded, CPU-only inference | LOW |

### Aggregate Dashboard Load (Operational Assumption)

```
100 Admin Users x 6 req/min (10s polling)
  = 600 Admin Requests/minute

+ 500 Public Users x 2 req/min (30s polling)
  = 1,000 Public Requests/minute

= Total: 1,600 Requests/minute to Gateway
```

### Database Growth (Operational Assumption)

| Table | Growth Rate | Retention |
|-------|-------------|-----------|
| admin_action_log | ~1000 records/election | Indefinite |
| security_events | ~500 records/election | 90 days |
| operations_metrics_history | ~1440 records/day (60s interval) | 1 year |
| health_history | ~8640 records/day (10s interval) | 30 days raw, aggregated |
| vote_request_log | 1 record/voter | Indefinite |
| outbox_events | 1 record/vote | Indefinite |

### Audit Log Growth (Operational Assumption)

- 1000 voters x 5 actions each = 5000 admin_action_log records per election
- SHA-256 hash: 64 bytes per record
- Estimated size: ~500KB per election

### Network Performance (Verified)

| Path | Protocol | Latency Sensitivity | Evidence |
|------|----------|--------------------|----|
| Frontend -> Gateway | HTTP REST | Medium (dashboard polling) | `docker-compose.yml` (ports 3000:8000) |
| Gateway -> PostgreSQL | JDBC | High (vote submission) | `application.yml:5` (datasource URL) |
| Gateway -> AI Service | HTTP REST | HIGH (biometric verification) | `FaceVerificationService.java:28` (RestTemplate) |
| Gateway -> Fabric | gRPC | HIGH (vote submission) | `VotingLedgerService.java` (Fabric Gateway SDK) |
| Docker bridge overhead | Internal network | Low (same host) | `docker-compose.yml:66-69` (bridge network) |

**Note**: TLS overhead not yet applicable (development environment). Production deployment will add TLS termination at reverse proxy.

---

## 13. KPI Catalogue

### Authentication

| KPI | Current Metric | Status | Owner | Evidence |
|-----|---------------|--------|-------|----------|
| Login response time | NO EVIDENCE FOUND | NOT_IMPLEMENTED | Operations | `AuthController.java:62-91` |
| Login success rate | NO EVIDENCE FOUND | NOT_IMPLEMENTED | Security | `SecurityEventService.java` |
| Rate limit trigger rate | NO EVIDENCE FOUND | NOT_IMPLEMENTED | Security | `RateLimitService.java` |

### Biometrics

| KPI | Current Metric | Status | Owner | Evidence |
|-----|---------------|--------|-------|----------|
| Enrollment time | NO EVIDENCE FOUND | NOT_IMPLEMENTED | AI Service | `BiometricEnrollmentService.java` |
| Verification time | NO EVIDENCE FOUND | NOT_IMPLEMENTED | AI Service | `BiometricVerificationService.java` |
| AI inference latency | NO EVIDENCE FOUND | NOT_IMPLEMENTED | AI Service | `FaceVerificationService.java` |

### Voting

| KPI | Current Metric | Status | Owner | Evidence |
|-----|---------------|--------|-------|----------|
| Vote submission time | NO EVIDENCE FOUND | NOT_IMPLEMENTED | Gateway | `VoteController.java:58-124` |
| Outbox processing time | NO EVIDENCE FOUND | NOT_IMPLEMENTED | Gateway | `OutboxProcessorService.java:23-55` |
| Fabric submission latency | Implemented | IMPLEMENTED | Blockchain | `VotingLedgerService.java:108-126` |

### Dashboard

| KPI | Current Metric | Status | Owner | Evidence |
|-----|---------------|--------|-------|----------|
| Dashboard refresh latency | NO EVIDENCE FOUND | NOT_IMPLEMENTED | Gateway | `PublicDashboardController.java` |
| Cache hit ratio | NO EVIDENCE FOUND | NOT_IMPLEMENTED | Gateway | `DashboardCacheService.java` |

### Health

| KPI | Current Metric | Status | Owner | Evidence |
|-----|---------------|--------|-------|----------|
| Health check duration | NO EVIDENCE FOUND | NOT_IMPLEMENTED | Platform | `SystemHealthService.java:49` |
| Component availability | Implemented | IMPLEMENTED | Platform | `SystemHealthService.java:44-47` |

### Database

| KPI | Current Metric | Status | Owner | Evidence |
|-----|---------------|--------|-------|----------|
| Query latency | NO EVIDENCE FOUND | NOT_IMPLEMENTED | DBA | `application.yml:4-12` |
| Connection pool usage | NO EVIDENCE FOUND | NOT_IMPLEMENTED | DBA | HikariCP defaults |
| Lock wait time | NO EVIDENCE FOUND | NOT_IMPLEMENTED | DBA | `VoterRepository.java:13` |

### Fabric

| KPI | Current Metric | Status | Owner | Evidence |
|-----|---------------|--------|-------|----------|
| Submission latency | Implemented | IMPLEMENTED | Blockchain | `VotingLedgerService.java:108-126` |
| Query latency | Implemented | IMPLEMENTED | Blockchain | `VotingLedgerService.java:113-126` |
| Transaction success rate | NO EVIDENCE FOUND | NOT_IMPLEMENTED | Blockchain | `OutboxProcessorService.java` |

---

## 14. Performance Risk Register

| Risk | Evidence | Impact | Likelihood | Mitigation |
|------|----------|--------|------------|------------|
| JVM OOM (256MB heap) | `Dockerfile:5` (no -Xmx) | HIGH | HIGH | Add `-XX:MaxRAMPercentage=75.0` |
| Connection pool exhaustion | `application.yml:4-12` (maxPoolSize=10) | HIGH | MEDIUM | Configure HikariCP pool size |
| RestTemplate thread starvation | `FaceVerificationService.java:28` (no timeout) | HIGH | MEDIUM | Add connect/read timeouts |
| Rate limiter memory leak | `RateLimitService.java:17` (unbounded map) | MEDIUM | HIGH | Add periodic cleanup |
| Single-threaded scheduler contention | `@Scheduled` annotations | MEDIUM | MEDIUM | Configure thread pool |
| Unbounded audit export | `AuditAnalyticsService.java:80` (MAX_VALUE) | HIGH | LOW | Add pagination limit |
| Non-atomic latency counters | `VotingLedgerService.java:35-38` | LOW | MEDIUM | Use AtomicLong |
| AI service blocking | `SystemHealthService.java:97` (new RestTemplate) | MEDIUM | MEDIUM | Reuse RestTemplate, add timeout |
| Cache stampede | `DashboardCacheService.java` (no lock) | LOW | LOW | Add Caffeine refreshAfterWrite |
| Database contention | `VoterRepository.java:13` (pessimistic lock) | MEDIUM | MEDIUM | Monitor lock wait times |

---

## 15. Performance Test Strategy

### Staged Execution Order

Performance tests should be executed in this order:
1. **Unit performance** (baseline)
2. **API load** (endpoint throughput)
3. **Integration load** (full flow)
4. **Election-day simulation** (realistic scenario)
5. **24-hour endurance** (memory leak detection)
6. **Chaos under load** (resilience validation)

### Unit Performance Tests

| Test | Status | Evidence |
|------|--------|----------|
| Password hashing performance | NO EVIDENCE FOUND | `PasswordService.java` |
| Cache lookup performance | NO EVIDENCE FOUND | `DashboardCacheService.java` |
| Rate limiter performance | NO EVIDENCE FOUND | `RateLimitService.java` |

### API Performance Tests

| Test | Status | Evidence |
|------|--------|----------|
| Login endpoint latency | NO EVIDENCE FOUND | `AuthController.java:62-91` |
| Vote endpoint latency | NO EVIDENCE FOUND | `VoteController.java:58-124` |
| Dashboard endpoint latency | NO EVIDENCE FOUND | `PublicDashboardController.java` |

### Integration Performance Tests

| Test | Status | Evidence |
|------|--------|----------|
| Full voting cycle latency | NO EVIDENCE FOUND | Auth -> Biometric -> Vote |
| Fabric submission latency | NO EVIDENCE FOUND | `OutboxProcessorService.java` |
| AI inference latency | NO EVIDENCE FOUND | `FaceVerificationService.java` |

### Stress Tests

| Test | Status | Evidence |
|------|--------|----------|
| Concurrent vote submission | NO EVIDENCE FOUND | `VoteController.java` |
| Concurrent login attempts | NO EVIDENCE FOUND | `AuthController.java` |
| Database connection saturation | NO EVIDENCE FOUND | HikariCP defaults |
| Rate limiter under load | NO EVIDENCE FOUND | `RateLimitService.java` |

### Spike Tests

| Test | Status | Evidence |
|------|--------|----------|
| Sudden voter spike | NO EVIDENCE FOUND | Election day simulation |
| Dashboard polling spike | NO EVIDENCE FOUND | Multiple admin users |

### Endurance Tests

| Test | Status | Evidence |
|------|--------|----------|
| 24-hour election simulation | NO EVIDENCE FOUND | Memory leak detection |
| Outbox processing under sustained load | NO EVIDENCE FOUND | `OutboxProcessorService.java` |

---

## 16. Production Capacity Scorecard

| Component | Status | Evidence | Gap |
|-----------|--------|----------|-----|
| Gateway | PARTIALLY_IMPLEMENTED | `Core Gateway/Dockerfile`, `application.yml` | No JVM tuning, default connection pool |
| Database | PARTIALLY_IMPLEMENTED | `docker-compose.yml`, `application.yml` | No HikariCP tuning, no replication |
| Fabric | PARTIALLY_IMPLEMENTED | `VotingLedgerService.java` | No connection pooling, no batching |
| AI Service | PARTIALLY_IMPLEMENTED | `ai-verification/src/main.py` | No request queuing, CPU-only |
| Dashboard | IMPLEMENTED | `DashboardCacheService.java`, `frontend ui/` | Cache metrics missing |
| Operations Center | IMPLEMENTED | `OperationsController.java` | Performance metrics missing |
| Caching | IMPLEMENTED | `DashboardCacheService.java` | Hit ratio metrics missing |
| Logging | PARTIALLY_IMPLEMENTED | SLF4J | No centralized aggregation |
| Monitoring | IMPLEMENTED | `SystemHealthService.java` | JVM metrics missing |
| Recovery | IMPLEMENTED | `OutboxProcessorService.java` | Recovery under load not tested |
| Deployment | IMPLEMENTED | `docker-compose.yml` | No rolling deployment |

---

## 17. Performance Readiness Roadmap

### Critical Before Production (1 week)

| Item | Evidence | Action | Impact |
|------|----------|--------|--------|
| JVM tuning | `Dockerfile:5` (no flags) | Add `-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC` | HIGH |
| Connection pool tuning | `application.yml:4-12` (no HikariCP) | Configure `maximum-pool-size`, `connection-timeout` | HIGH |
| RestTemplate timeouts | `FaceVerificationService.java:28` | Add connect/read timeouts | HIGH |
| Rate limiter cleanup | `RateLimitService.java:17` (unbounded) | Add periodic key eviction | HIGH |
| Core Gateway resources | `docker-compose.yml:59-63` (1G/1CPU) | Increase to 2G/2CPU | HIGH |

### Recommended Before Production (2 weeks)

| Item | Evidence | Action | Impact |
|------|----------|--------|--------|
| Thread pool for schedulers | `@Scheduled` annotations | Configure `@EnableScheduling` with task executor | HIGH |
| Audit export pagination | `AuditAnalyticsService.java:80` (MAX_VALUE) | Add page size limit | MEDIUM |
| Non-atomic counters | `VotingLedgerService.java:35-38` | Use AtomicLong | MEDIUM |
| Health check RestTemplate reuse | `SystemHealthService.java:97` | Create single instance | MEDIUM |
| Connection leak detection | `application.yml` (no HikariCP) | Enable `leak-detection-threshold` | MEDIUM |

### Post-Production Optimization (4 weeks)

| Item | Evidence | Action | Impact |
|------|----------|--------|--------|
| Load testing | NO EVIDENCE FOUND | Implement Gatling/jMeter tests | HIGH |
| JVM metrics | NO EVIDENCE FOUND | Add Micrometer + Prometheus | MEDIUM |
| Cache hit ratio metrics | NO EVIDENCE FOUND | Add Caffeine metrics | MEDIUM |
| Database query metrics | NO EVIDENCE FOUND | Add P6Spy or similar | MEDIUM |
| Connection pooling for Fabric | NO EVIDENCE FOUND | Implement Fabric gateway pool | MEDIUM |
| AI request queuing | NO EVIDENCE FOUND | Add request queue + worker pool | LOW |
| Distributed caching | NO EVIDENCE FOUND | Evaluate Redis | LOW |
