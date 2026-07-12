-- Operations Center Migration Script
-- Execute this against the voting_db database

-- 1. Create incident_status enum
DO $$ BEGIN
    CREATE TYPE incident_status AS ENUM ('OPEN', 'ACKNOWLEDGED', 'INVESTIGATING', 'RESOLVED');
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

-- 2. Create incident_severity enum
DO $$ BEGIN
    CREATE TYPE incident_severity AS ENUM ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL');
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

-- 3. Create alert_severity enum
DO $$ BEGIN
    CREATE TYPE alert_severity AS ENUM ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL');
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

-- 4. Create alert_status enum
DO $$ BEGIN
    CREATE TYPE alert_status AS ENUM ('ACTIVE', 'ACKNOWLEDGED', 'RESOLVED');
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

-- 5. Create security_event_type enum
DO $$ BEGIN
    CREATE TYPE security_event_type AS ENUM (
        'FAILED_LOGIN',
        'FAILED_ADMIN_LOGIN',
        'RATE_LIMIT',
        'ACCOUNT_LOCKOUT',
        'TOKEN_REVOKED',
        'BIOMETRIC_FAILURE',
        'BIOMETRIC_REENROLLMENT',
        'SUSPICIOUS_ACTIVITY',
        'FABRIC_FAILURE',
        'SYSTEM_ERROR'
    );
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

-- 6. Enhance election_incidents table
ALTER TABLE election_incidents
ADD COLUMN IF NOT EXISTS assigned_to VARCHAR(64),
ADD COLUMN IF NOT EXISTS acknowledged_at TIMESTAMPTZ,
ADD COLUMN IF NOT EXISTS investigating_at TIMESTAMPTZ,
ADD COLUMN IF NOT EXISTS priority VARCHAR(16),
ADD COLUMN IF NOT EXISTS comments TEXT;

-- 7. Create incident_assignment_history table
CREATE TABLE IF NOT EXISTS incident_assignment_history (
    id BIGSERIAL PRIMARY KEY,
    incident_id BIGINT NOT NULL,
    assigned_from VARCHAR(64),
    assigned_to VARCHAR(64) NOT NULL,
    assigned_by VARCHAR(64) NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL,
    reason TEXT,
    FOREIGN KEY (incident_id) REFERENCES election_incidents(id)
);

-- 8. Create operations_alerts table
CREATE TABLE IF NOT EXISTS operations_alerts (
    id BIGSERIAL PRIMARY KEY,
    severity alert_severity NOT NULL,
    status alert_status NOT NULL DEFAULT 'ACTIVE',
    source VARCHAR(32) NOT NULL,
    message TEXT NOT NULL,
    parent_alert_id BIGINT,
    acknowledged_by VARCHAR(64),
    acknowledged_at TIMESTAMPTZ,
    resolved_by VARCHAR(64),
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    metadata TEXT,
    FOREIGN KEY (parent_alert_id) REFERENCES operations_alerts(id)
);

-- 9. Create security_events table
CREATE TABLE IF NOT EXISTS security_events (
    id BIGSERIAL PRIMARY KEY,
    event_type security_event_type NOT NULL,
    severity VARCHAR(16) NOT NULL,
    source VARCHAR(32) NOT NULL,
    ip_hash VARCHAR(64),
    user_hash VARCHAR(64),
    details TEXT,
    occurred_at TIMESTAMPTZ NOT NULL
);

-- 10. Create operations_metrics_history table
CREATE TABLE IF NOT EXISTS operations_metrics_history (
    id BIGSERIAL PRIMARY KEY,
    metric_type VARCHAR(32) NOT NULL,
    metric_name VARCHAR(64) NOT NULL,
    metric_value BIGINT NOT NULL,
    election_id UUID,
    recorded_at TIMESTAMPTZ NOT NULL
);

-- 11. Create audit_exports table
CREATE TABLE IF NOT EXISTS audit_exports (
    id BIGSERIAL PRIMARY KEY,
    generated_at TIMESTAMPTZ NOT NULL,
    generated_by VARCHAR(64) NOT NULL,
    record_count INT NOT NULL,
    sha256_hash VARCHAR(64) NOT NULL,
    file_path VARCHAR(512)
);

-- 12. Create indexes for query performance
CREATE INDEX IF NOT EXISTS idx_incidents_status ON election_incidents(status);
CREATE INDEX IF NOT EXISTS idx_incidents_severity ON election_incidents(severity);
CREATE INDEX IF NOT EXISTS idx_incidents_assigned_to ON election_incidents(assigned_to);
CREATE INDEX IF NOT EXISTS idx_incidents_created_at ON election_incidents(created_at);

CREATE INDEX IF NOT EXISTS idx_assignment_history_incident ON incident_assignment_history(incident_id);
CREATE INDEX IF NOT EXISTS idx_assignment_history_assigned_at ON incident_assignment_history(assigned_at);

CREATE INDEX IF NOT EXISTS idx_alerts_status ON operations_alerts(status);
CREATE INDEX IF NOT EXISTS idx_alerts_severity ON operations_alerts(severity);
CREATE INDEX IF NOT EXISTS idx_alerts_source ON operations_alerts(source);
CREATE INDEX IF NOT EXISTS idx_alerts_created_at ON operations_alerts(created_at);
CREATE INDEX IF NOT EXISTS idx_alerts_parent ON operations_alerts(parent_alert_id);

CREATE INDEX IF NOT EXISTS idx_security_events_type ON security_events(event_type);
CREATE INDEX IF NOT EXISTS idx_security_events_occurred ON security_events(occurred_at);
CREATE INDEX IF NOT EXISTS idx_security_events_severity ON security_events(severity);

CREATE INDEX IF NOT EXISTS idx_metrics_type_time ON operations_metrics_history(metric_type, recorded_at);
CREATE INDEX IF NOT EXISTS idx_metrics_election ON operations_metrics_history(election_id, recorded_at);

CREATE INDEX IF NOT EXISTS idx_audit_exports_generated ON audit_exports(generated_at);
