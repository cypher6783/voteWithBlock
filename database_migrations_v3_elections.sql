-- Election & Candidate Management System Migration Script
-- Execute this against the voting_db database

-- 1. Create elections table
CREATE TABLE IF NOT EXISTS elections (
    id UUID PRIMARY KEY,
    election_code VARCHAR(64) UNIQUE NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    start_date TIMESTAMPTZ NOT NULL,
    end_date TIMESTAMPTZ NOT NULL,
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(64)
);

-- 2. Create candidates table
CREATE TABLE IF NOT EXISTS candidates (
    id UUID PRIMARY KEY,
    election_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    party VARCHAR(128),
    manifesto TEXT,
    image_url VARCHAR(512),
    display_order INT NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    FOREIGN KEY (election_id) REFERENCES elections(id),
    CONSTRAINT uk_election_display_order UNIQUE (election_id, display_order)
);

-- 3. Create election_incidents table
CREATE TABLE IF NOT EXISTS election_incidents (
    id BIGSERIAL PRIMARY KEY,
    election_id UUID NOT NULL,
    severity VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    created_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ,
    FOREIGN KEY (election_id) REFERENCES elections(id)
);

-- 4. Create necessary indexes
CREATE INDEX IF NOT EXISTS idx_elections_status ON elections(status);
CREATE INDEX IF NOT EXISTS idx_candidates_election_id ON candidates(election_id);
CREATE INDEX IF NOT EXISTS idx_incidents_election_id ON election_incidents(election_id);
