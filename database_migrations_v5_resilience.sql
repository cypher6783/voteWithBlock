-- Resilience Architecture Migration Script
-- Execute this against the voting_db database

-- 1. Create outbox_events table for reliable event delivery
CREATE TABLE IF NOT EXISTS outbox_events (
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

-- 2. Create vote_request_log table for database-backed idempotency
CREATE TABLE IF NOT EXISTS vote_request_log (
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

-- 3. Create backup_catalog table
CREATE TABLE IF NOT EXISTS backup_catalog (
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

-- 4. Create recovery_audit_log table for immutable recovery audit
CREATE TABLE IF NOT EXISTS recovery_audit_log (
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

-- 5. Create health_history table for trend analysis
CREATE TABLE IF NOT EXISTS health_history (
    id BIGSERIAL PRIMARY KEY,
    component VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    metrics JSONB,
    recorded_at TIMESTAMPTZ NOT NULL
);

-- 6. Create indexes for query performance
CREATE INDEX IF NOT EXISTS idx_outbox_status ON outbox_events(status);
CREATE INDEX IF NOT EXISTS idx_outbox_created ON outbox_events(created_at);
CREATE INDEX IF NOT EXISTS idx_outbox_event_type ON outbox_events(event_type);

CREATE INDEX IF NOT EXISTS idx_vrl_idempotency ON vote_request_log(idempotency_key);
CREATE INDEX IF NOT EXISTS idx_vrl_voter ON vote_request_log(voter_id_hash);
CREATE INDEX IF NOT EXISTS idx_vrl_created ON vote_request_log(created_at);

CREATE INDEX IF NOT EXISTS idx_backup_type ON backup_catalog(backup_type);
CREATE INDEX IF NOT EXISTS idx_backup_created ON backup_catalog(created_at);
CREATE INDEX IF NOT EXISTS idx_backup_verified ON backup_catalog(verified);

CREATE INDEX IF NOT EXISTS idx_recovery_action ON recovery_audit_log(action_type);
CREATE INDEX IF NOT EXISTS idx_recovery_component ON recovery_audit_log(target_component);
CREATE INDEX IF NOT EXISTS idx_recovery_performed ON recovery_audit_log(performed_at);

CREATE INDEX IF NOT EXISTS idx_health_component ON health_history(component);
CREATE INDEX IF NOT EXISTS idx_health_recorded ON health_history(recorded_at);
CREATE INDEX IF NOT EXISTS idx_health_component_recorded ON health_history(component, recorded_at);
