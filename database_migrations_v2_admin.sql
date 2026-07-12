-- Admin Portal Upgrades Migration Script
-- Execute this against the voting_db database

-- 1. Create admin_accounts table
CREATE TABLE IF NOT EXISTS admin_accounts (
    username VARCHAR(64) PRIMARY KEY,
    full_name VARCHAR(128),
    email VARCHAR(256) UNIQUE,
    role VARCHAR(32) NOT NULL,
    password_salt VARCHAR(64) NOT NULL,
    password_hash VARCHAR(128) NOT NULL,
    mfa_secret VARCHAR(64),
    mfa_enabled BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    failed_login_attempts INT DEFAULT 0,
    last_failed_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    last_login_at TIMESTAMPTZ,
    created_by VARCHAR(64)
);

-- 2. Create admin_sessions table
CREATE TABLE IF NOT EXISTS admin_sessions (
    jti VARCHAR(64) PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    session_type VARCHAR(32) NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (username) REFERENCES admin_accounts(username)
);

-- 3. Create admin_action_log table
CREATE TABLE IF NOT EXISTS admin_action_log (
    id BIGSERIAL PRIMARY KEY,
    admin_username_hash VARCHAR(64) NOT NULL,
    action VARCHAR(64) NOT NULL,
    target_entity_type VARCHAR(32),
    target_entity_id_hash VARCHAR(64),
    outcome VARCHAR(16) NOT NULL,
    ip_hash VARCHAR(64) NOT NULL,
    performed_at TIMESTAMPTZ NOT NULL,
    metadata TEXT
);

-- 4. Create necessary indexes for query performance
CREATE INDEX IF NOT EXISTS idx_admin_sessions_username ON admin_sessions(username);
CREATE INDEX IF NOT EXISTS idx_admin_sessions_expires_at ON admin_sessions(expires_at);
CREATE INDEX IF NOT EXISTS idx_admin_action_log_performed_at ON admin_action_log(performed_at);

-- Future considerations documented in schema:
-- Future: MFA recovery codes
-- Future: Admin password reset tokens
-- Future: Admin account lockout configuration options
