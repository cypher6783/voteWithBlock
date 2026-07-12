# Administrative Monitoring & Operations Center — Architecture Plan

## 1. Executive Summary

The Operations Center transforms the Votetrust Ledger from a monitoring dashboard into a full election operations platform. This plan is grounded in repository evidence and builds upon existing components: `ElectionIncidentController`, `DashboardAlertService`, `SystemHealthService`, `DashboardMetricsService`, `AdminActionLog`, and the existing frontend admin pages.

**Key Finding**: The repository already has foundational components for incidents, alerts, health monitoring, and audit logging. The Operations Center must extend these foundations with persistence, lifecycle management, role-based access, and a unified operations frontend.

**Production-Grade Refinements Incorporated**:
1. Incident lifecycle enforced as enums (`IncidentStatus`, `IncidentSeverity`)
2. Alert severity/status enforced as enums (`AlertSeverity`, `AlertStatus`)
3. Assignment history preserved via `incident_assignment_history` table
4. Alerts are immutable (no DELETE operations, permanent retention)
5. Security events classified via `SecurityEventType` enum
6. Health checks use consecutive failure thresholds (prevent flapping)
7. Audit export includes integrity hash (SHA-256)
8. Alerts support correlation via `parentAlertId`
9. Election Day metrics preserved permanently (compressed archives)
10. Frontend routes enforce role-aware navigation

---

## 2. Current Repository Capabilities

### What Exists (Evidence-Backed)

| Component | Status | Evidence Location |
|---|---|---|
| Incident CRUD | Basic (OPEN→RESOLVED only) | `ElectionIncidentController.java:23-47` |
| Alert detection | In-memory only, no persistence | `DashboardAlertService.java:16-55` |
| Health monitoring | Mock/hardcoded, no real checks | `SystemHealthService.java:17-44` |
| Security metrics | In-memory AtomicLong counters | `DashboardMetricsService.java:18-27` |
| Audit logging | AdminActionLog table + repository | `AdminActionLog.java`, `AdminActionLogRepository.java` |
| Admin auth | Full HMAC-SHA256 token system | `AdminTokenService.java`, `AdminAuthFilter.java` |
| Role system | 3 roles: SUPER_ADMIN, ELECTION_ADMIN, AUDITOR | `AdminRole.java` |
| Frontend admin pages | 6 pages under `/admin/*` | `frontend ui/src/app/admin/*/page.tsx` |

### What Does Not Exist

- Incident lifecycle states (ACKNOWLEDGED, INVESTIGATING)
- Incident assignment/ownership
- Alert persistence or workflow
- Real health checks (all mocked)
- Historical metrics aggregation
- Audit log search/filter/pagination
- Election operations view (velocity, timeline, turnout trends)
- Data retention policies
- `/admin/operations/*` frontend routes

---

## 3. Existing Reusable Components

### 3.1 ElectionIncidentController (Extend, Don't Replace)
- **Current**: `POST /election/{electionId}`, `PUT /{id}/resolve`, `GET /election/{electionId}`
- **Reuse**: Keep existing endpoints. Add new endpoints for lifecycle transitions and filtering.
- **Evidence**: `ElectionIncidentController.java:19-48`

### 3.2 DashboardAlertService (Extend with Persistence)
- **Current**: In-memory `CopyOnWriteArrayList<Alert>`, methods: `evaluateAlerts()`, `registerIncidentAlert()`, `registerBiometricSpikeAlert()`
- **Reuse**: Keep alert detection logic. Add persistence layer and lifecycle methods.
- **Evidence**: `DashboardAlertService.java:16-55`

### 3.3 SystemHealthService (Replace Mock with Real Checks)
- **Current**: Returns hardcoded `HEALTHY` for all components
- **Reuse**: Keep DTO structure (`MonitoringDashboardDto`). Replace mock logic with actual health probes.
- **Evidence**: `SystemHealthService.java:17-44`

### 3.4 DashboardMetricsService (Extend with Persistence)
- **Current**: In-memory `AtomicLong` counters for biometric and security metrics
- **Reuse**: Keep counter increment methods. Add periodic persistence and historical aggregation.
- **Evidence**: `DashboardMetricsService.java:14-77`

### 3.5 DashboardCacheService (Extend)
- **Current**: Caffeine caches with 5s/10s/30s TTLs
- **Reuse**: Add cache entries for new Operations Center metrics.
- **Evidence**: `DashboardCacheService.java:16-51`

### 3.6 AdminActionLog + Repository (Add Query Methods)
- **Current**: `JpaRepository` with no custom queries
- **Reuse**: Add custom query methods for filtering, pagination, and aggregation.
- **Evidence**: `AdminActionLogRepository.java:8-9`

### 3.7 AdminRole Enum (Extend with Granular Permissions)
- **Current**: `SUPER_ADMIN`, `ELECTION_ADMIN`, `AUDITOR`
- **Reuse**: Keep enum. Add permission mapping in authorization layer.
- **Evidence**: `AdminRole.java:3-7`

### 3.8 RateLimitService (Reuse for Alert Thresholds)
- **Current**: Sliding window rate limiter with configurable thresholds
- **Reuse**: Apply same pattern for rate-limit spike detection.
- **Evidence**: `RateLimitService.java:15-42`

---

## 4. Incident Operations Architecture

### 4.1 Enhanced ElectionIncident Entity

**Current State** (evidence: `ElectionIncident.java:22-39`):
```
severity: String (free-form)
status: String (free-form)
```

**Proposed Changes**:
- Add `assignedTo` (VARCHAR) — username of assigned admin
- Add `acknowledgedAt` (TIMESTAMPTZ) — when incident was acknowledged
- Add `investigatingAt` (TIMESTAMPTZ) — when investigation started
- Add `priority` (VARCHAR) — operational priority level
- Add `comments` (TEXT) — investigator notes

**New Enums** (replace free-form strings):
```java
public enum IncidentStatus {
    OPEN,
    ACKNOWLEDGED,
    INVESTIGATING,
    RESOLVED
}

public enum IncidentSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
```

**Lifecycle States** (currently only OPEN→RESOLVED):
```
OPEN → ACKNOWLEDGED → INVESTIGATING → RESOLVED
```

### 4.1a Assignment History Table

**New Entity**: `IncidentAssignmentHistory`

```java
@Entity
@Table(name = "incident_assignment_history")
public class IncidentAssignmentHistory {
    @Id @GeneratedValue
    private Long id;
    private Long incidentId;
    private String assignedFrom;    // null for initial assignment
    private String assignedTo;
    private String assignedBy;
    private OffsetDateTime assignedAt;
    private String reason;
}
```

**Rationale**: Preserves reassignment history for post-election investigations. Current `assignedTo` only tracks current owner.

### 4.1b No-Delete Policy for Incidents

**Architectural Rule**: Incidents are never deleted.
- No DELETE endpoint in controller
- No delete operations in repository
- Historical incidents remain permanently available
- Aligns with project's no-delete philosophy

### 4.2 Incident Controller Extensions

**Current Endpoints** (evidence: `ElectionIncidentController.java:23-47`):
- `POST /election/{electionId}` — Create
- `PUT /{id}/resolve` — Resolve
- `GET /election/{electionId}` — List by election

**New Endpoints**:
- `PUT /{id}/acknowledge` — Transition to ACKNOWLEDGED
- `PUT /{id}/investigate` — Transition to INVESTIGATING
- `PUT /{id}/assign` — Assign to admin
- `GET /` — List all incidents (cross-election, with filters)
- `GET /{id}` — Get incident details
- `GET /stats` — Incident statistics by severity/status

### 4.3 Workflow Enforcement

**State Machine**:
```
OPEN → ACKNOWLEDGED (requires SUPER_ADMIN or ELECTION_ADMIN)
ACKNOWLEDGED → INVESTIGATING (requires assigned admin)
INVESTIGATING → RESOLVED (requires SUPER_ADMIN or assigned ELECTION_ADMIN)
Any → OPEN (reopen, requires SUPER_ADMIN)
```

### 4.4 Role Permissions

| Action | SUPER_ADMIN | ELECTION_ADMIN | AUDITOR |
|---|---|---|---|
| Create incident | Yes | Yes | No |
| Acknowledge incident | Yes | Yes | No |
| Investigate incident | Yes | Yes (assigned only) | No |
| Resolve incident | Yes | Yes (assigned only) | No |
| Assign incident | Yes | No | No |
| Reopen incident | Yes | No | No |
| View incidents | Yes | Yes (own election) | Yes |
| View incident stats | Yes | Yes (own election) | Yes |

---

## 5. Security Operations Architecture

### 5.1 Authentication Events

**Current Tracking** (evidence: `DashboardMetricsService.java:23-26`):
- `loginFailures` (AtomicLong, in-memory)
- `adminLoginFailures` (AtomicLong, in-memory)

**Enhanced Tracking**:
- Failed voter logins (persist to `admin_action_log` with action `VOTER_LOGIN_FAIL`)
- Failed admin logins (already logged via `AdminAuthController.java:67`)
- Account lockouts (persist from `BiometricVerificationService.java:39-48`)
- Session revocations (track via `AdminSession.revoked` updates)

### 5.2 Biometric Events

**Current Tracking** (evidence: `DashboardMetricsService.java:18-21`):
- `successfulVerifications`, `failedVerifications`, `lockouts`, `reEnrollmentRequests`

**Enhanced Tracking**:
- Verification failures (already logged in `biometric_attempt_log`)
- Re-enrollment requests (track `PENDING_REENROLLMENT` outcomes)
- Suspicious biometric activity (multiple lockouts from same IP hash)

### 5.3 Rate Limiting Events

**Current Tracking** (evidence: `DashboardMetricsService.java:25-26`):
- `rateLimitEvents` (AtomicLong, in-memory)
- `suspiciousActivities` (AtomicLong, in-memory)

**Enhanced Tracking**:
- Voter abuse attempts (rate limit hits on `/api/auth/login`, `/api/vote/cast`)
- Admin abuse attempts (rate limit hits on `/api/admin/auth/login`)
- Aggregate by IP hash for pattern detection

### 5.4 Aggregation Strategy

**Real-time**: Keep `AtomicLive` counters for dashboard display (5-second refresh).

**Persisted**: Periodic batch write (every 60 seconds) to new `security_events` table.

**New Enum** (classify security events):
```java
public enum SecurityEventType {
    FAILED_LOGIN,
    FAILED_ADMIN_LOGIN,
    RATE_LIMIT,
    ACCOUNT_LOCKOUT,
    TOKEN_REVOKED,
    BIOMETRIC_FAILURE,
    BIOMETRIC_REENROLLMENT,
    SUSPICIOUS_ACTIVITY,
    FABRIC_FAILURE,
    SYSTEM_ERROR
}
```

**Table Schema**:
```sql
CREATE TABLE security_events (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(32) NOT NULL,  -- SecurityEventType enum
    severity VARCHAR(16) NOT NULL,
    source VARCHAR(32) NOT NULL,
    ip_hash VARCHAR(64),
    user_hash VARCHAR(64),
    details TEXT,
    occurred_at TIMESTAMPTZ NOT NULL,
    INDEX idx_security_events_type (event_type),
    INDEX idx_security_events_occurred (occurred_at)
);
```

### 5.5 Data Retention Strategy

- **Security events**: 90-day retention, then archive to cold storage
- **Biometric attempt logs**: 1-year retention (regulatory compliance)
- **Admin action logs**: Indefinite retention (audit trail)
- **Incidents**: Indefinite retention (election records)

---

## 6. Audit Analytics Architecture

### 6.1 Current Audit Capabilities

**AdminActionLog** (evidence: `AdminActionLog.java:22-34`):
- Fields: `admin_username_hash`, `action`, `target_entity_type`, `target_entity_id_hash`, `outcome`, `ip_hash`, `performed_at`, `metadata`
- Actions logged: `CREATE_ELECTION`, `UPDATE_ELECTION`, `CHANGE_STATUS_*`, `ADMIN_LOGIN_*`, `ADMIN_LOGOUT`, `BOOTSTRAP_ADMIN_CREATED`

**BiometricAttemptLog** (evidence: `BiometricAttemptLog.java`):
- Fields: `national_id_hash`, `attempt_type`, `outcome`, `similarity_score`, `threshold_used`, `ip_hash`, `attempted_at`, `model_version`

### 6.2 Enhanced Audit Logging

**New Actions to Log**:
- `CREATE_INCIDENT`, `ACKNOWLEDGE_INCIDENT`, `INVESTIGATE_INCIDENT`, `RESOLVE_INCIDENT`, `ASSIGN_INCIDENT`
- `CREATE_CANDIDATE`, `UPDATE_CANDIDATE`, `DELETE_CANDIDATE`
- `ACKNOWLEDGE_ALERT`, `RESOLVE_ALERT`
- `VIEW_AUDIT_LOG`, `EXPORT_AUDIT_LOG`

### 6.3 Audit Query API

**New Repository Methods** for `AdminActionLogRepository`:
```java
Page<AdminActionLog> findByActionContaining(String action, Pageable pageable);
Page<AdminActionLog> findByAdminUsernameHashAndPerformedAtBetween(
    String adminHash, OffsetDateTime start, OffsetDateTime end, Pageable pageable);
Page<AdminActionLog> findByTargetEntityTypeAndTargetEntityIdHash(
    String entityType, String entityIdHash, Pageable pageable);
List<AdminActionLog> findTop100ByOrderByPerformedAtDesc();
@Query("SELECT a.action, COUNT(a) FROM AdminActionLog a GROUP BY a.action")
List<Object[]> countByActionGrouped();
```

### 6.4 Audit Visualization

**Dashboard Cards**:
- Total actions (last 24h, 7d, 30d)
- Actions by type (pie chart data)
- Actions by admin (bar chart data)
- Timeline of recent actions
- Failed actions (security events)

**Search Capabilities**:
- By admin (username hash)
- By action type
- By target entity type
- By date range
- By outcome (SUCCESS/FAIL)

**Filtering Capabilities**:
- Severity filter (for security-relevant actions)
- Role filter
- Time range filter
- Entity type filter

### 6.5 Audit Export Integrity

**Export Response Format**:
```java
public class AuditExport {
    private String generatedAt;
    private String generatedBy;
    private int recordCount;
    private String sha256Hash;      // SHA-256 of export contents
    private List<AdminActionLog> records;
}
```

**Integrity Verification**:
- Compute SHA-256 of serialized records before sending
- Store export metadata in `audit_exports` table for later verification
- Enables detection of modified audit files

---

## 7. Alert Management Architecture

### 7.1 OperationsAlertService (New Service)

**Current State** (evidence: `DashboardAlertService.java:16-55`):
- In-memory `CopyOnWriteArrayList<Alert>`
- Methods: `evaluateAlerts()`, `registerIncidentAlert()`, `registerBiometricSpikeAlert()`

**Proposed**: Replace with `OperationsAlertService` that extends `DashboardAlertService` with persistence.

### 7.2 Alert Entity

**New Enums** (enforce type safety):
```java
public enum AlertSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

public enum AlertStatus {
    ACTIVE,
    ACKNOWLEDGED,
    RESOLVED
}
```

**Entity**:
```java
@Entity
@Table(name = "operations_alerts")
public class OperationsAlert {
    @Id @GeneratedValue
    private Long id;
    @Enumerated(EnumType.STRING)
    private AlertSeverity severity;
    @Enumerated(EnumType.STRING)
    private AlertStatus status;
    private String source;          // FABRIC, AI_SERVICE, DATABASE, GATEWAY, INCIDENT, BIOMETRIC, AUTH, RATE_LIMIT
    private String message;
    private Long parentAlertId;     // Correlation: group related alerts
    private String acknowledgedBy;
    private OffsetDateTime acknowledgedAt;
    private String resolvedBy;
    private OffsetDateTime resolvedAt;
    private OffsetDateTime createdAt;
    private String metadata;        // JSON blob for source-specific details
}
```

### 7.2a Alert Correlation

**New Field**: `parentAlertId`

- Groups related alerts into a single operational incident
- Example: Fabric down + Database degraded → both reference same parent
- Enables root cause analysis across correlated failures

### 7.2b No-Delete Policy for Alerts

**Architectural Rule**: Alerts are never deleted.
- No DELETE endpoint in controller
- No delete operations in repository
- Historical alerts remain permanently available
- Aligns with project's no-delete philosophy

### 7.3 Alert Sources

| Source | Trigger | Severity |
|---|---|---|
| FABRIC | Fabric disconnected | CRITICAL |
| AI_SERVICE | AI service offline | HIGH |
| DATABASE | Database connection degraded | CRITICAL |
| GATEWAY | Gateway degraded | HIGH |
| INCIDENT | Critical incident created | CRITICAL |
| BIOMETRIC | Biometric failure spike (>10 in 5min) | HIGH |
| AUTH | Authentication attack spike (>5 in 1min) | CRITICAL |
| RATE_LIMIT | Rate-limit spike (>50 in 1min) | MEDIUM |

### 7.4 Alert Lifecycle

```
ACTIVE → ACKNOWLEDGED (requires SUPER_ADMIN or ELECTION_ADMIN)
ACKNOWLEDGED → RESOLVED (requires same admin who acknowledged, or SUPER_ADMIN)
ACTIVE → RESOLVED (direct resolution by SUPER_ADMIN)
```

### 7.5 Alert API

- `GET /api/admin/operations/alerts` — List all alerts (with filters)
- `GET /api/admin/operations/alerts/active` — List active alerts
- `GET /api/admin/operations/alerts/stats` — Alert statistics
- `PUT /api/admin/operations/alerts/{id}/acknowledge` — Acknowledge alert
- `PUT /api/admin/operations/alerts/{id}/resolve` — Resolve alert

---

## 8. Election Operations Architecture

### 8.1 Election Operations View

**Current State** (evidence: `ElectionMetricsService.java:16-57`):
- `getPublicMetrics()` returns turnout, vote count, countdown
- `calculateTotalVotes()` returns mock value (18234)
- `calculateCountdown()` calculates time remaining

**Enhanced Metrics**:
- Active election status (real-time)
- Election timeline (start, end, current position)
- Election health (incident count, alert count)
- Turnout trend (votes per minute over time)
- Voting velocity (current rate vs. average rate)
- Operational warnings (upcoming deadlines, capacity issues)

### 8.2 Election Health Score

**Calculation**:
```
health_score = 100 - (critical_incidents * 20) - (high_incidents * 10) - (active_alerts * 5)
```

**Status Mapping**:
- 80-100: HEALTHY
- 50-79: DEGRADED
- 0-49: CRITICAL

### 8.3 Voting Velocity Tracking

**New Entity**:
```sql
CREATE TABLE election_metrics_snapshots (
    id BIGSERIAL PRIMARY KEY,
    election_id UUID NOT NULL,
    total_votes BIGINT,
    votes_this_minute INT,
    votes_last_5_minutes INT,
    turnout_percentage DECIMAL(5,2),
    incident_count INT,
    alert_count INT,
    health_score INT,
    captured_at TIMESTAMPTZ NOT NULL,
    FOREIGN KEY (election_id) REFERENCES elections(id)
);
```

**Refresh Interval**: Every 60 seconds during active election

### 8.4 Election Operations API

- `GET /api/admin/operations/elections/{id}/status` — Real-time election status
- `GET /api/admin/operations/elections/{id}/timeline` — Election timeline data
- `GET /api/admin/operations/elections/{id}/health` — Election health score
- `GET /api/admin/operations/elections/{id}/velocity` — Voting velocity data
- `GET /api/admin/operations/elections/{id}/warnings` — Operational warnings

---

## 9. Service Health Architecture

### 9.1 Real Health Checks (Replace Mocks)

**Current State** (evidence: `SystemHealthService.java:17-44`):
- Returns hardcoded `HEALTHY` for all components

**Proposed Health Checks**:

| Service | Check Method | Timeout | Threshold |
|---|---|---|---|
| Gateway | Self-check (Spring Actuator `/actuator/health`) | 2s | Response < 500ms |
| Database | JDBC `SELECT 1` query | 3s | Connection pool > 20% available |
| AI Service | HTTP GET `/health` | 5s | Model loaded = true |
| Fabric | `contract.evaluateTransaction("Ping")` | 10s | Response received |

### 9.2 Escalation Thresholds

**Consecutive Failure Thresholds** (prevent dashboard flapping):

| Status | Condition |
|---|---|
| HEALTHY | 3 consecutive successes after DEGRADED/CRITICAL |
| DEGRADED | 3 consecutive failures |
| CRITICAL | 5 consecutive failures |

**Rationale**: Single transient network issues should not trigger status changes. Consecutive failures ensure stability before escalation.

**Implementation Pattern**:
```java
private int consecutiveFailures = 0;
private int consecutiveSuccesses = 0;

public ComponentHealth checkService(String serviceName) {
    boolean success = performHealthCheck(serviceName);
    if (success) {
        consecutiveFailures = 0;
        consecutiveSuccesses++;
        if (consecutiveSuccesses >= 3) return ComponentHealth.HEALTHY;
    } else {
        consecutiveSuccesses = 0;
        consecutiveFailures++;
        if (consecutiveFailures >= 5) return ComponentHealth.CRITICAL;
        if (consecutiveFailures >= 3) return ComponentHealth.DEGRADED;
    }
    return currentStatus; // No change
}
```

### 9.3 SystemHealthService Redesign

**Replace**: Mock implementation with actual health probes
**Keep**: `MonitoringDashboardDto` structure

**New Implementation Pattern**:
```java
@ComponentHealth checkGateway() {
    // Call actuator/health endpoint
    // Measure response time
    // Return ComponentHealth with status and details
}

@ComponentHealth checkDatabase() {
    // Execute SELECT 1 via JdbcTemplate
    // Check connection pool metrics
    // Return ComponentHealth
}

@ComponentHealth checkAiService() {
    // HTTP GET http://ai-service:8000/health
    // Parse response for modelLoaded field
    // Return ComponentHealth
}

@ComponentHealth checkFabric() {
    // If contract != null: evaluateTransaction("Ping")
    // Else: return DEGRADED with "Sandbox mode"
    // Return ComponentHealth
}
```

### 9.4 Health Check Cache

- Cache health results for 10 seconds (align with `DashboardCacheService` health cache TTL)
- Avoid overwhelming downstream services with health checks

---

## 10. OperationsMetricsService Architecture

### 10.1 New Unified Service

**Purpose**: Aggregate all operations metrics into a single service.

**Metrics Domains**:
1. **Security Metrics**: Login failures, lockouts, rate limit events
2. **Election Metrics**: Turnout, vote count, velocity, health score
3. **Incident Metrics**: Open incidents, severity distribution, resolution time
4. **Health Metrics**: Component statuses, uptime, latency

### 10.2 Refresh Intervals

| Metric Type | Refresh Interval | Cache TTL |
|---|---|---|
| Security (real-time) | 5 seconds | 5 seconds |
| Election (real-time) | 10 seconds | 10 seconds |
| Incident (near-real-time) | 30 seconds | 30 seconds |
| Health (periodic) | 10 seconds | 10 seconds |
| Historical (aggregated) | 60 seconds | 60 seconds |

### 10.3 Historical Aggregation

**New Table**:
```sql
CREATE TABLE operations_metrics_history (
    id BIGSERIAL PRIMARY KEY,
    metric_type VARCHAR(32) NOT NULL,
    metric_name VARCHAR(64) NOT NULL,
    metric_value BIGINT NOT NULL,
    election_id UUID,
    recorded_at TIMESTAMPTZ NOT NULL,
    INDEX idx_metrics_type_time (metric_type, recorded_at),
    INDEX idx_metrics_election (election_id, recorded_at)
);
```

**Aggregation Strategy**:
- Raw data: 1-minute resolution (last 24 hours)
- Hourly aggregates: Last 7 days
- Daily aggregates: Last 90 days
- Purge raw data after 24 hours, keep aggregates

### 10.4 Cache Boundaries

- **L1 (In-Memory)**: Real-time counters (AtomicLong), 5-second refresh
- **L2 (Caffeine)**: Dashboard DTOs, 10-second TTL
- **L3 (Database)**: Historical aggregates, 60-second refresh

---

## 11. Role Authorization Matrix

### 11.1 Current Role System

**Evidence**: `AdminRole.java` defines `SUPER_ADMIN`, `ELECTION_ADMIN`, `AUDITOR`

### 11.2 Operations Function Access Matrix

| Function | SUPER_ADMIN | ELECTION_ADMIN | AUDITOR |
|---|---|---|---|
| **Incidents** | | | |
| View all incidents | Yes | Yes (own election) | Yes (read-only) |
| Create incident | Yes | Yes | No |
| Acknowledge incident | Yes | Yes | No |
| Investigate incident | Yes | Yes (assigned) | No |
| Resolve incident | Yes | Yes (assigned) | No |
| Assign incident | Yes | No | No |
| Reopen incident | Yes | No | No |
| **Security** | | | |
| View security dashboard | Yes | Yes | Yes (read-only) |
| View security events | Yes | Yes (own election) | Yes |
| Acknowledge security alert | Yes | Yes | No |
| Resolve security alert | Yes | Yes | No |
| **Audit** | | | |
| View audit log | Yes | Yes (own actions) | Yes (read-only) |
| Search audit log | Yes | Yes (own actions) | Yes |
| Export audit log | Yes | No | Yes (read-only) |
| **Alerts** | | | |
| View all alerts | Yes | Yes (own election) | Yes (read-only) |
| Acknowledge alert | Yes | Yes | No |
| Resolve alert | Yes | Yes | No |
| **Health** | | | |
| View system health | Yes | Yes | Yes |
| View service details | Yes | No | Yes (read-only) |
| **Election Operations** | | | |
| View election status | Yes | Yes (own election) | Yes |
| View election health | Yes | Yes (own election) | Yes |
| View voting velocity | Yes | Yes (own election) | Yes |
| View operational warnings | Yes | Yes (own election) | Yes |
| **Metrics** | | | |
| View operations metrics | Yes | Yes (own election) | Yes (read-only) |
| View historical metrics | Yes | Yes (own election) | Yes (read-only) |
| Export metrics | Yes | No | Yes (read-only) |

---

## 12. Frontend Architecture

### 12.1 Route Structure

**New Routes** under `/admin/operations/`:

```
/admin/operations                    → Operations Dashboard (overview)
/admin/operations/incidents          → Incident Operations Center
/admin/operations/security           → Security Operations Dashboard
/admin/operations/audit              → Audit Analytics Center
/admin/operations/alerts             → Alert Management
/admin/operations/health             → Service Health Center
/admin/operations/elections          → Election Operations View
```

### 12.2 Components

**Shared Components** (reusable across pages):
- `StatusBadge` — Severity/status indicators (reuses pattern from `incidents/page.tsx:59-63`)
- `MetricCard` — Metric display cards (reuses pattern from `monitoring/page.tsx:77-85`)
- `StatusCard` — Service health cards (reuses pattern from `monitoring/page.tsx:64-75`)
- `DataTable` — Filterable/sortable table (reuses pattern from `incidents/page.tsx:37-80`)
- `TimelineChart` — Time-series chart for metrics
- `AlertBanner` — Active alert notification bar

**Page-Specific Components**:
- `IncidentDetailModal` — Incident details with lifecycle actions
- `SecurityEventTimeline` — Chronological security event display
- `AuditLogTable` — Searchable/filterable audit log
- `AlertList` — Alert management with acknowledge/resolve actions
- `HealthDashboard` — Service health grid with status cards
- `ElectionOpsPanel` — Election-specific operations view

### 12.3 Navigation

**Admin Sidebar** (extend existing pattern):
```
Dashboard
├── Election Management
├── Candidate Management
├── Security Monitoring
├── Incident Management
Operations Center
├── Operations Dashboard
├── Incidents
├── Security Events
├── Audit Log
├── Alerts
├── Service Health
├── Election Operations
System Monitoring
```

### 12.3a Frontend Route Protection

**Architectural Rule**: Every `/admin/operations/*` page enforces both:
1. Backend authorization (already planned via `@PreAuthorize`)
2. Frontend role-aware navigation (hide unauthorized routes)

**Implementation**:
- Store role in `localStorage.getItem('adminRole')`
- Filter navigation items based on role
- Redirect unauthorized access attempts
- Users without permission neither access nor see unauthorized routes

### 12.4 Data Flows

**Real-time Updates**: Polling pattern (existing pattern in `admin/dashboard/page.tsx:23`)
- Security metrics: 5-second interval
- Health metrics: 10-second interval
- Incidents/alerts: 30-second interval
- Audit log: Manual refresh or 60-second interval

**Authentication**: Same pattern as existing admin pages
- Token in `localStorage.getItem('adminToken')`
- Header: `Authorization: Bearer {token}`

---

## 13. API Design

### 13.1 Incident Operations API

```
GET    /api/admin/operations/incidents                    → List incidents (paginated, filtered)
GET    /api/admin/operations/incidents/{id}               → Get incident details
POST   /api/admin/operations/incidents                    → Create incident
PUT    /api/admin/operations/incidents/{id}/acknowledge   → Acknowledge incident
PUT    /api/admin/operations/incidents/{id}/investigate   → Start investigation
PUT    /api/admin/operations/incidents/{id}/assign        → Assign to admin
PUT    /api/admin/operations/incidents/{id}/resolve       → Resolve incident
PUT    /api/admin/operations/incidents/{id}/reopen        → Reopen incident
GET    /api/admin/operations/incidents/stats              → Incident statistics
```

### 13.2 Security Operations API

```
GET    /api/admin/operations/security/events              → List security events (paginated, filtered)
GET    /api/admin/operations/security/events/stats        → Security event statistics
GET    /api/admin/operations/security/metrics             → Real-time security metrics
GET    /api/admin/operations/security/threats             → Threat detection summary
```

### 13.3 Audit Analytics API

```
GET    /api/admin/operations/audit/logs                   → List audit logs (paginated, filtered)
GET    /api/admin/operations/audit/logs/{id}              → Get audit log details
GET    /api/admin/operations/audit/stats                  → Audit statistics
GET    /api/admin/operations/audit/export                 → Export audit logs (CSV/JSON)
```

### 13.4 Alert Management API

```
GET    /api/admin/operations/alerts                       → List alerts (paginated, filtered)
GET    /api/admin/operations/alerts/active                → List active alerts
GET    /api/admin/operations/alerts/stats                 → Alert statistics
PUT    /api/admin/operations/alerts/{id}/acknowledge      → Acknowledge alert
PUT    /api/admin/operations/alerts/{id}/resolve          → Resolve alert
```

### 13.5 Service Health API

```
GET    /api/admin/operations/health                       → System health overview
GET    /api/admin/operations/health/{service}             → Specific service health
GET    /api/admin/operations/health/history               → Health check history
```

### 13.6 Election Operations API

```
GET    /api/admin/operations/elections/{id}/status        → Election status
GET    /api/admin/operations/elections/{id}/timeline      → Election timeline
GET    /api/admin/operations/elections/{id}/health        → Election health score
GET    /api/admin/operations/elections/{id}/velocity      → Voting velocity data
GET    /api/admin/operations/elections/{id}/warnings      → Operational warnings
GET    /api/admin/operations/elections/{id}/metrics       → Election metrics history
```

### 13.7 Operations Metrics API

```
GET    /api/admin/operations/metrics                      → Current operations metrics
GET    /api/admin/operations/metrics/history              → Historical metrics
GET    /api/admin/operations/metrics/summary              → Aggregated metrics summary
```

---

## 14. Data Retention Strategy

### 14.1 Retention Policies

| Data Type | Retention Period | Storage Tier |
|---|---|---|
| Security events | 90 days | Hot → Archive |
| Biometric attempt logs | 1 year | Hot → Warm |
| Admin action logs | Indefinite | Hot |
| Incidents | Indefinite | Hot |
| Alerts | Indefinite | Hot |
| Operations metrics (raw) | 24 hours | Hot (deleted) |
| Operations metrics (hourly) | 7 days | Hot |
| Operations metrics (daily) | 90 days | Warm |
| **Election Day metrics** | **Permanent** | **Compressed Archive** |
| Health check history | 7 days | Hot |

### 14.1a Election Day Metrics Preservation

**Special Rule**: Election Day metrics are never purged.
- Compress (gzip) after election ends
- Archive to cold storage
- Preserve permanently for historical analysis
- Election analytics are historically valuable

### 14.2 Purge Strategy

**Scheduled Jobs**:
- `@Scheduled(cron = "0 0 2 * * ?")` — Daily at 2 AM: Purge expired data
- Security events older than 90 days → Archive to cold storage, then delete
- Raw metrics older than 24 hours → Delete
- Hourly metrics older than 7 days → Aggregate to daily, delete hourly
- Daily metrics older than 90 days → Archive, then delete

### 14.3 Archive Format

- CSV export for audit/compliance
- JSON export for system integration
- Compressed archives (gzip) for cold storage

---

## 15. Deployment Considerations

### 15.1 Database Migrations

**New Tables Required**:
1. `operations_alerts` — Alert persistence
2. `security_events` — Security event logging
3. `operations_metrics_history` — Historical metrics

**Table Modifications**:
1. `election_incidents` — Add `assignedTo`, `acknowledgedAt`, `investigatingAt`, `priority`, `comments`

### 15.2 Service Layer Changes

**New Services**:
- `OperationsAlertService` (extends `DashboardAlertService`)
- `OperationsMetricsService` (unified metrics aggregation)
- `SecurityEventService` (security event persistence)
- `AuditAnalyticsService` (audit log query/aggregation)
- `ElectionOperationsService` (election-specific operations)

**Modified Services**:
- `SystemHealthService` — Replace mock with real health checks
- `DashboardMetricsService` — Add periodic persistence
- `DashboardCacheService` — Add cache entries for new metrics

### 15.3 Frontend Build

- No new dependencies required (existing Next.js + Tailwind)
- Add new route pages under `/admin/operations/`
- Reuse existing component patterns (StatusCard, MetricCard, DataTable)

### 15.4 Docker Compose Updates

**No new services required** — Operations Center runs within existing `core-gateway` service.

**Optional**: Add Redis for distributed caching (currently using Caffeine in-memory).

### 15.5 Performance Considerations

- Health checks: Max 4 concurrent checks (one per service)
- Metrics aggregation: Background thread, non-blocking
- Audit log queries: Paginated, max 100 records per page
- Alert evaluation: Periodic (every 30 seconds), not real-time

---

## 16. Prioritized Implementation Roadmap

### Phase 1: Foundation (Week 1-2)
1. ✅ Create database migration for `operations_alerts`, `security_events`, `operations_metrics_history`
2. ✅ Enhance `ElectionIncident` entity with lifecycle fields
3. ✅ Add custom query methods to `AdminActionLogRepository`
4. ✅ Create `OperationsAlertService` with persistence

### Phase 2: Core Services (Week 3-4)
1. ✅ Replace `SystemHealthService` mock with real health checks
2. ✅ Create `SecurityEventService` for event persistence
3. ✅ Create `OperationsMetricsService` for unified metrics
4. ✅ Create `AuditAnalyticsService` for audit log queries

### Phase 3: API Layer (Week 5-6)
1. ✅ Implement Incident Operations API endpoints
2. ✅ Implement Security Operations API endpoints
3. ✅ Implement Audit Analytics API endpoints
4. ✅ Implement Alert Management API endpoints
5. ✅ Implement Service Health API endpoints
6. ✅ Implement Election Operations API endpoints

### Phase 4: Frontend (Week 7-8)
1. ✅ Create Operations Dashboard overview page
2. ✅ Create Incident Operations Center page
3. ✅ Create Security Operations Dashboard page
4. ✅ Create Audit Analytics Center page
5. ✅ Create Alert Management page
6. ✅ Create Service Health Center page
7. ✅ Create Election Operations View page

### Phase 5: Polish (Week 9-10)
1. ✅ Add data retention purge jobs
2. ✅ Add role-based access control enforcement
3. ✅ Add historical metrics aggregation
4. ✅ Performance optimization and caching
5. ✅ End-to-end testing

---

## Appendix A: Database Schema Summary

### New Tables

```sql
-- Operations Alerts
CREATE TABLE operations_alerts (
    id BIGSERIAL PRIMARY KEY,
    severity VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    source VARCHAR(32) NOT NULL,
    message TEXT NOT NULL,
    acknowledged_by VARCHAR(64),
    acknowledged_at TIMESTAMPTZ,
    resolved_by VARCHAR(64),
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    metadata TEXT
);

-- Security Events
CREATE TABLE security_events (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(32) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    source VARCHAR(32) NOT NULL,
    ip_hash VARCHAR(64),
    user_hash VARCHAR(64),
    details TEXT,
    occurred_at TIMESTAMPTZ NOT NULL
);

-- Operations Metrics History
CREATE TABLE operations_metrics_history (
    id BIGSERIAL PRIMARY KEY,
    metric_type VARCHAR(32) NOT NULL,
    metric_name VARCHAR(64) NOT NULL,
    metric_value BIGINT NOT NULL,
    election_id UUID,
    recorded_at TIMESTAMPTZ NOT NULL
);
```

### Modified Tables

```sql
-- Enhanced Election Incidents
ALTER TABLE election_incidents
ADD COLUMN assigned_to VARCHAR(64),
ADD COLUMN acknowledged_at TIMESTAMPTZ,
ADD COLUMN investigating_at TIMESTAMPTZ,
ADD COLUMN priority VARCHAR(16),
ADD COLUMN comments TEXT;
```

---

## Appendix B: Technology Stack

| Layer | Technology | Evidence |
|---|---|---|
| Backend | Spring Boot (Java) | `pom.xml`, `GatewayApplication.java` |
| Frontend | Next.js (React, TypeScript) | `frontend ui/src/app/` |
| Database | PostgreSQL 15 | `docker-compose.yml:41-50` |
| Cache | Caffeine (in-memory) | `DashboardCacheService.java:3-4` |
| Blockchain | Hyperledger Fabric | `VotingLedgerService.java:6-8` |
| AI Service | FastAPI (Python) | `ai-verification/src/main.py:4-5` |
| Auth | HMAC-SHA256 tokens | `AdminTokenService.java:9-10` |
| Security | Spring Security | `SecurityConfig.java:5-6` |
