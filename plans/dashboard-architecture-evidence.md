# Dashboard Architecture Evidence

## 1. Existing Election Architecture
* **File:** `c:\Users\ISAIAH\Desktop\VOTE WITH BLOCK\database_migrations_v3_elections.sql`
* **Entity:** `elections` table
* **Evidence:** The database contains an `elections` table with fields: `id`, `election_code`, `title`, `description`, `type`, `status`, `start_date`, `end_date`.
* **Explanation:** This provides the foundation for the Public Dashboard to display active election titles, countdowns (via start/end dates), and status.

* **File:** `c:\Users\ISAIAH\Desktop\VOTE WITH BLOCK\database_migrations_v3_elections.sql`
* **Entity:** `candidates` table
* **Evidence:** The database contains a `candidates` table linked to `election_id`.
* **Explanation:** We can aggregate candidate performance by linking election context to candidate metrics.

## 2. Existing Admin Architecture
* **File:** `c:\Users\ISAIAH\Desktop\VOTE WITH BLOCK\Core Gateway\src\main\java\com\votewithblock\gateway\controller\AdminController.java`
* **Class/function/entity:** `AdminController`, `ElectionAdminController`, `ElectionIncidentController`
* **Evidence:** Existing controllers dedicated to administrative functions exist in the `controller` package.
* **Explanation:** The new dashboard metrics and incidents endpoints can be integrated into this existing administrative controller structure.

## 3. Existing Incident Architecture
* **File:** `c:\Users\ISAIAH\Desktop\VOTE WITH BLOCK\database_migrations_v3_elections.sql`
* **Entity:** `election_incidents` table
* **Evidence:** The database contains an `election_incidents` table with fields: `id`, `election_id`, `severity`, `status`, `title`, `description`, `created_by`, `created_at`, `resolved_at`.
* **Explanation:** This is direct evidence of support for an Incident Management Dashboard with states, severities, and timestamps for tracking.

## 4. Real-Time Update Context
* **File:** `c:\Users\ISAIAH\Desktop\VOTE WITH BLOCK\Core Gateway\src\main\java\com\votewithblock\gateway\service\ElectionResultsCacheService.java`
* **Class/function/entity:** `ElectionResultsCacheService`
* **Evidence:** Uses Caffeine cache configured with `.expireAfterWrite(10, TimeUnit.SECONDS)`.
* **Explanation:** The backend is designed for periodic fetching (every 10 seconds), suggesting a polling-based architecture rather than streaming sockets to protect the ledger.

* **File:** `c:\Users\ISAIAH\Desktop\VOTE WITH BLOCK\frontend ui\src\components\AuditScreen.tsx` (and `BiometricScreen.tsx`)
* **Class/function/entity:** `setInterval`
* **Evidence:** Frontend uses `setInterval` extensively for timers and periodic status updates.
* **Explanation:** Polling is the existing pattern for state updates in the Next.js frontend.

## 5. Blockchain Monitoring Capabilities
* **File:** `c:\Users\ISAIAH\Desktop\VOTE WITH BLOCK\Core Gateway\src\main\java\com\votewithblock\gateway\service\VotingLedgerService.java`
* **Class/function/entity:** `VotingLedgerService`
* **Evidence:** Connects to Fabric network using `org.hyperledger.fabric.client.Gateway`. Handles direct gRPC proposals and logs exceptions when nodes are offline.
* **Explanation:** Blockchain health can be monitored by observing the connection state and tracking exceptions from the `submitTransaction` calls.

## 6. Infrastructure Context
* **File:** `c:\Users\ISAIAH\Desktop\VOTE WITH BLOCK\docker-compose.yml`
* **Class/function/entity:** Docker services definition
* **Evidence:** Exposes `core-gateway`, `postgres-db`, `ai-service`, and `frontend-ui` under `voting-network`.
* **Explanation:** Health monitoring will need to check these exact 4 components to assess overall operational health.
