# Real-Time Election Dashboard & Monitoring System Architecture Plan

## 1. Executive Summary
This architecture plan outlines the implementation of the Real-Time Election Dashboard & Monitoring System for the Votetrust Ledger. It leverages existing Next.js frontend structure, Spring Boot Gateway, Postgres Database, and Hyperledger Fabric connections to deliver a secure, scalable dashboard supporting both public election visibility and secure administrative operations.

## 2. Current Repository Capabilities
* **Elections**: Fully modeled in database (`elections` and `candidates` tables).
* **Blockchain**: `VotingLedgerService` provides read/write access.
* **Incidents**: Database already includes `election_incidents` with severity and status.
* **Caching**: `ElectionResultsCacheService` handles 10-second result caching.

## 3. Existing Reusable Components
* Next.js `/admin` routes.
* Spring Boot Admin Controllers (`ElectionAdminController.java`, `ElectionIncidentController.java`).
* Caffeine Cache (`ElectionResultsCacheService.java`).
* `VotingLedgerService` for blockchain communication.

## 4. Public Dashboard Architecture
* **Purpose**: Show active election, countdown, total registered voters, turnout, and progress.
* **Security**: Publicly accessible. No biometric or PII data exposed. Avoid exposing internal UUIDs publicly.
* **Data Flow**: `PublicElectionController` -> `ElectionMetricsService` -> `DashboardCacheService`.
* **API Contract**: 
  `GET /api/public/dashboard`
  Returns `PublicDashboardDto`:
  ```json
  {
    "electionTitle": "...",
    "electionCode": "...",
    "status": "OPEN",
    "turnoutPercentage": 42.1,
    "totalVotesCast": 18234,
    "registeredVoters": 43000,
    "countdownSeconds": 12345
  }
  ```
* **Presentation Rules**:
  * **SCHEDULED**: Show countdown to open.
  * **OPEN**: Show countdown to close.
  * **CLOSED**: Show election closed, results pending.
  * **ARCHIVED**: Show historical context.
* **Polling Interval**: 30 seconds.

## 5. Administrative Dashboard Architecture
* **Purpose**: Operational overview, metrics, and deeper security analysis.
* **Views**:
  * **Election Metrics**: Turnout, total votes.
  * **Biometric Metrics**: Success/fail rates from AI service.
  * **Security Metrics**: Rate-limit hits, auth failures.
  * **Operational Metrics**: DB, AI, Gateway, Fabric connection statuses.
* **Polling Interval**: 10 seconds.
* **Security**: Guarded by `AdminAuthController`. Requires ELECTION_ADMIN or SUPER_ADMIN access levels. Uses `AdminDashboardDto` and `SecurityDashboardDto`.

## 6. Incident Dashboard Architecture
* **Purpose**: Track, investigate, and resolve issues.
* **Data Layer**: Based on existing `election_incidents` table. No incident deletion ever (use RESOLVED status).
* **Fields**: `severity` (LOW, MEDIUM, HIGH, CRITICAL), `status`, `title`, `description`.
* **Permissions**:
  * **AUDITOR**: Read only.
  * **ELECTION_ADMIN**: Create, Update, Investigate, Resolve.
  * **SUPER_ADMIN**: All actions (Delete prohibited).

## 7. Metrics & Dashboard Services
* **Dashboard DTO Layer**: 
  * APIs must only expose purpose-built DTOs: `PublicDashboardDto`, `AdminDashboardDto`, `MonitoringDashboardDto`, `SecurityDashboardDto`.
* **DashboardMetricsService**:
  * Persists summary counters fed by `BiometricAttemptLog`, `AdminActionLog`, and `ElectionIncident`.
  * Prevents expensive historical operational metric queries.
* **DashboardCacheService**:
  * Caches Public metrics, Admin metrics, Health metrics, and Incident summaries.
* **DashboardAlertService**:
  * Triggers alerts for: Fabric disconnected, AI service offline, Database degraded, Critical incident opened, Biometric failures spike.
  * Displayed centrally on `/admin/dashboard`.

## 8. Real-Time Update Strategy
* **Recommendation**: **Polling**.
* **Refresh Intervals**:
  * **Public Dashboard**: 30 seconds.
  * **Admin Dashboard**: 10 seconds.
  * **Monitoring Dashboard**: 5 seconds.

## 9. Blockchain Monitoring Architecture
* **Strategy**: Monitor `VotingLedgerService` method invocations.
* **Metrics**:
  * Last Successful Transaction
  * Last Failed Transaction
  * Average Submission Latency
  * Average Query Latency

## 10. Health Monitoring Architecture
* **Service**: `SystemHealthService`
* **Responsibilities**: Aggregate statuses across components, calculate overall state, and return dashboard DTO (`HEALTHY`, `DEGRADED`, `CRITICAL`).
* **Gateway**: Tracked via standard Spring Boot Actuator/Health checks.
* **AI Service**: Health check endpoint tracking connection failures from `Gateway`.
* **Database**: Standard HikariCP connection pool monitoring.
* **Fabric**: Ledger connection metrics via `VotingLedgerService`.

## 11. Dashboard Security Model
* **PUBLIC**: Read-only access to `/api/public/dashboard`.
* **AUDITOR**: Read-only access to blockchain monitoring and election incident tables.
* **ELECTION_ADMIN**: Manage `election_incidents`, full read access to election/biometric metrics.
* **SUPER_ADMIN**: Full access to all administrative metrics, rate-limits, and gateway health.

## 12. Frontend Architecture
* **Repository Context**: Using existing `frontend ui/src/app` architecture.
* **Routes**:
  * `/dashboard` (Public, unprotected).
  * `/admin/dashboard` (Protected admin layout).
  * `/admin/incidents` (Incident management table based on `election_incidents`).
  * `/admin/monitoring` (System health and Fabric metrics).
  * `/admin/security` (Auth failure logs, rate-limit events).

## 13. Prioritized Implementation Roadmap
### Dashboard Phase A
* Dashboard DTOs
* `DashboardCacheService`
* `ElectionMetricsService`
* `SystemHealthService`

### Dashboard Phase B
* Public dashboard APIs
* Admin dashboard APIs
* Incident APIs

### Dashboard Phase C
* Public UI
* Admin UI
* Monitoring UI
* Security UI

### Dashboard Phase D
* Alerts (`DashboardAlertService`)
* Caching
* Performance tuning
