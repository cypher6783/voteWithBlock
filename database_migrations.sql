-- Biometric System Upgrade Migration Script
-- Execute this against the voting_db database

-- 1. Add new columns to existing voter_accounts table
ALTER TABLE voter_accounts
ADD COLUMN IF NOT EXISTS biometric_status VARCHAR(32) DEFAULT 'NOT_ENROLLED',
ADD COLUMN IF NOT EXISTS failed_biometric_attempts INT DEFAULT 0,
ADD COLUMN IF NOT EXISTS last_failed_biometric_at TIMESTAMPTZ;

-- 2. Create voter_biometrics table
CREATE TABLE IF NOT EXISTS voter_biometrics (
    id BIGSERIAL PRIMARY KEY,
    national_id VARCHAR(64) UNIQUE NOT NULL,
    embedding_ciphertext BYTEA NOT NULL,
    embedding_iv BYTEA NOT NULL,
    embedding_tag BYTEA NOT NULL,
    model_version VARCHAR(32) NOT NULL,
    enrolled_at TIMESTAMPTZ NOT NULL,
    enrollment_ip_hash VARCHAR(64),
    last_verified_at TIMESTAMPTZ,
    version INT NOT NULL,
    FOREIGN KEY (national_id) REFERENCES voter_accounts(national_id)
);

-- 3. Create biometric_challenges table
CREATE TABLE IF NOT EXISTS biometric_challenges (
    id BIGSERIAL PRIMARY KEY,
    challenge_id UUID UNIQUE NOT NULL,
    national_id VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used BOOLEAN DEFAULT FALSE
);

-- 4. Create biometric_attempt_log table
CREATE TABLE IF NOT EXISTS biometric_attempt_log (
    id BIGSERIAL PRIMARY KEY,
    national_id_hash VARCHAR(64),
    attempt_type VARCHAR(16),
    outcome VARCHAR(16),
    similarity_score FLOAT,
    threshold_used FLOAT,
    ip_hash VARCHAR(64),
    attempted_at TIMESTAMPTZ,
    model_version VARCHAR(32)
);
