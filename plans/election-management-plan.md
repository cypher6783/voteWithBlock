# Election & Candidate Management System — Implementation Plan

This architecture plan designs a production-grade Election and Candidate Management System for the Votetrust Ledger. It incorporates strict lifecycle freeze rules, optimistic locking, scheduled transitions, UUID identity, and active-candidate filtering.

## 1. Domain Models

### Election Domain
**Entity:** `Election.java`
**Table:** `elections`
*   `id` (UUID PK)
*   `electionCode` (VARCHAR UNIQUE) — Human readable (e.g., `PRESIDENTIAL_2027`)
*   `title` (VARCHAR)
*   `description` (TEXT)
*   `type` (Enum: `PRESIDENTIAL`, `GUBERNATORIAL`, `PARLIAMENTARY`, `UNION`, `REFERENDUM`, `CUSTOM`)
*   `status` (Enum: `DRAFT`, `SCHEDULED`, `OPEN`, `CLOSED`, `ARCHIVED`)
*   `startDate` (TIMESTAMPTZ)
*   `endDate` (TIMESTAMPTZ)
*   `version` (INTEGER) — `@Version` for optimistic locking
*   `createdAt`, `updatedAt` (TIMESTAMPTZ)
*   `createdBy` (VARCHAR)

### Candidate Domain
**Entity:** `Candidate.java`
**Table:** `candidates`
*   `id` (UUID PK)
*   `electionId` (UUID FK)
*   `name` (VARCHAR)
*   `party` (VARCHAR)
*   `manifesto` (TEXT)
*   `imageUrl` (VARCHAR)
*   `displayOrder` (INT) — Enforced `UNIQUE(election_id, display_order)`
*   `isActive` (BOOLEAN)
*   `version` (INTEGER) — `@Version` for optimistic locking
*   `createdAt`, `updatedAt` (TIMESTAMPTZ)

### Incident Domain
**Entity:** `ElectionIncident.java`
**Table:** `election_incidents`
*   `id` (BIGSERIAL PK)
*   `electionId` (UUID FK)
*   `severity` (VARCHAR)
*   `status` (VARCHAR)
*   `title` (VARCHAR)
*   `description` (TEXT)
*   `createdBy` (VARCHAR)
*   `createdAt`, `resolvedAt` (TIMESTAMPTZ)

## 2. Election Lifecycle & Enforcement

### 2.1 Status Transitions & Scheduler
Transitions (`SCHEDULED` → `OPEN`, `OPEN` → `CLOSED`) will be handled automatically based on `startDate` and `endDate` by `ElectionLifecycleScheduler.java` using Spring's `@Scheduled`.

### 2.2 Freeze Rules & Concurrency
*   When status is `OPEN`, modifications to the election or its candidates are prohibited at the service layer, barring emergency closure (`OPEN` → `CLOSED`).
*   `@Version` optimistic locking ensures no race conditions bypass the freeze checks.
*   **No Deletion Rule:** Candidates and Elections can never be deleted. Deactivation (`isActive = false`) or Archiving (`status = ARCHIVED`) is the only permitted mechanism.

### 2.3 Single Active Election Rule
The `ElectionService` will enforce a strict constraint: Only one election can be in the `OPEN` state at any given time.

## 3. Blockchain Result Strategy
Hyperledger Fabric remains the sole source of truth for vote tallies. To prevent excessive load on the blockchain nodes, an `ElectionResultsCacheService` will be implemented with a TTL of 5-10 seconds to serve recent ledger queries.

## 4. Administrative API Design
*   **Elections:** `POST /elections`, `PUT /elections/{id}` (No DELETE)
*   **Candidates:** `POST /elections/{id}/candidates`, `PUT /candidates/{id}`, `PUT /candidates/{id}/deactivate` (No DELETE)
*   **Results:** `GET /elections/{id}/results` (Uses Cache)
*   **Incidents:** `POST /elections/{id}/incidents`, `PUT /incidents/{id}/resolve`
*   **Public:** `GET /api/elections/active` — Returns the currently `OPEN` election and **only** its `isActive = true` candidates.

## 5. Prioritized Implementation Roadmap

### Phase 3A (Data & Infrastructure)
1.  `database_migrations_v3_elections.sql`: `elections`, `candidates`, `election_incidents` tables with UUIDs, unique constraints, and `version`.
2.  `ElectionStatus` and `ElectionType` enums.
3.  JPA Entities with `@Version` and Repositories.

### Phase 3B (Services & Enforcement)
1.  `ElectionService` & `CandidateService` (Freeze rules, single-active rule, optimistic locking).
2.  `ElectionLifecycleScheduler`.
3.  `ElectionResultsCacheService`.

### Phase 3C (APIs & UI Integration)
1.  Admin and Public Controllers.
2.  Refactor `VoteController` / `InputValidationService`.
3.  Refactor `BallotScreen.tsx` to use `/api/elections/active`.

### Phase 3D (Admin Interface)
1.  Next.js pages: `/app/admin/elections`, `/app/admin/candidates`, `/app/admin/incidents`.
