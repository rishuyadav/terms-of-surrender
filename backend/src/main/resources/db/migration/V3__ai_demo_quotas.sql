-- Lifetime pseudonymous quotas intentionally outlive the 24-hour report retention.
CREATE TABLE ai_demo_visitors (
    visitor_hash VARCHAR(64) PRIMARY KEY,
    analyses_used INTEGER NOT NULL DEFAULT 0,
    questions_used INTEGER NOT NULL DEFAULT 0
);

-- Cached Q&A responses follow report deletion/retention; only counters are permanent.
CREATE TABLE ai_demo_requests (
    request_hash VARCHAR(64) PRIMARY KEY,
    visitor_hash VARCHAR(64),
    operation VARCHAR(12) NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    analysis_id VARCHAR(36) NOT NULL,
    state VARCHAR(12) NOT NULL,
    response_json TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_ai_demo_requests_analysis ON ai_demo_requests(analysis_id);
CREATE INDEX idx_ai_demo_requests_created ON ai_demo_requests(created_at);

CREATE TABLE ai_demo_nonces (
    nonce VARCHAR(36) PRIMARY KEY,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Old queued jobs deliberately default to preview, even when AI is later enabled.
ALTER TABLE analyses ADD COLUMN selected_mode VARCHAR(24) NOT NULL DEFAULT 'RULE_BASED_PREVIEW';
ALTER TABLE analyses ADD COLUMN preview_reason VARCHAR(40);
ALTER TABLE analyses ADD COLUMN qa_available BOOLEAN NOT NULL DEFAULT FALSE;
