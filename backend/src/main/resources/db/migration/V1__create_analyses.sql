CREATE TABLE analyses (
    id VARCHAR(36) PRIMARY KEY,
    session_hash VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    stage VARCHAR(120) NOT NULL,
    input_json TEXT NOT NULL,
    report_json TEXT,
    error_code VARCHAR(80),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    lease_until TIMESTAMP WITH TIME ZONE,
    version INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_analyses_session ON analyses(session_hash, created_at);
CREATE INDEX idx_analyses_queue ON analyses(status, created_at);
CREATE INDEX idx_analyses_expiry ON analyses(expires_at);

-- Serialize admission checks across API replicas, not just within one JVM.
CREATE TABLE queue_guard (id INTEGER PRIMARY KEY);
INSERT INTO queue_guard (id) VALUES (1);

-- Separate from reports so deleting a report cannot reset the hourly allowance.
CREATE TABLE analysis_admissions (
    id VARCHAR(36) PRIMARY KEY,
    session_hash VARCHAR(64) NOT NULL,
    admitted_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_admissions_session ON analysis_admissions(session_hash, admitted_at);
