ALTER TABLE ai_question_logs
    ADD COLUMN IF NOT EXISTS answer_mode VARCHAR(40),
    ADD COLUMN IF NOT EXISTS answer_engine VARCHAR(40),
    ADD COLUMN IF NOT EXISTS internal_attempted BOOLEAN,
    ADD COLUMN IF NOT EXISTS internal_sufficient BOOLEAN,
    ADD COLUMN IF NOT EXISTS web_attempted BOOLEAN,
    ADD COLUMN IF NOT EXISTS web_succeeded BOOLEAN,
    ADD COLUMN IF NOT EXISTS web_sufficient BOOLEAN,
    ADD COLUMN IF NOT EXISTS llm_attempted BOOLEAN,
    ADD COLUMN IF NOT EXISTS llm_succeeded BOOLEAN,
    ADD COLUMN IF NOT EXISTS retrieval_failure_reason TEXT,
    ADD COLUMN IF NOT EXISTS total_latency_ms BIGINT,
    ADD COLUMN IF NOT EXISTS retrieval_trace_json JSONB,
    ADD COLUMN IF NOT EXISTS llm_provider VARCHAR(80),
    ADD COLUMN IF NOT EXISTS llm_model VARCHAR(120),
    ADD COLUMN IF NOT EXISTS llm_input_tokens INTEGER,
    ADD COLUMN IF NOT EXISTS llm_output_tokens INTEGER,
    ADD COLUMN IF NOT EXISTS llm_total_tokens INTEGER,
    ADD COLUMN IF NOT EXISTS llm_estimated_cost NUMERIC(18, 8),
    ADD COLUMN IF NOT EXISTS llm_currency VARCHAR(8),
    ADD COLUMN IF NOT EXISTS llm_usage_estimated BOOLEAN,
    ADD COLUMN IF NOT EXISTS llm_retry_count INTEGER,
    ADD COLUMN IF NOT EXISTS llm_latency_ms BIGINT,
    ADD COLUMN IF NOT EXISTS llm_request_status VARCHAR(30),
    ADD COLUMN IF NOT EXISTS llm_error_category VARCHAR(80);

-- Keep this migration self-contained for installations that were baselined
-- before the external web-source table was copied into the Flyway history.
CREATE TABLE IF NOT EXISTS web_evidence_sources (
    id BIGSERIAL PRIMARY KEY,
    evidence_id BIGINT NOT NULL REFERENCES web_evidence(id) ON DELETE CASCADE,
    title VARCHAR(300),
    url TEXT NOT NULL,
    domain VARCHAR(200),
    excerpt TEXT,
    credibility_score NUMERIC(5, 2),
    source_rank INTEGER NOT NULL,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (evidence_id, url)
);

CREATE INDEX IF NOT EXISTS idx_web_evidence_sources_evidence_rank
    ON web_evidence_sources (evidence_id, source_rank);

ALTER TABLE web_evidence_sources
    ADD COLUMN IF NOT EXISTS source_type VARCHAR(60),
    ADD COLUMN IF NOT EXISTS official_source BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS published_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS retrieved_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS source_status VARCHAR(40) NOT NULL DEFAULT 'RETRIEVED';

CREATE INDEX IF NOT EXISTS idx_ai_question_logs_llm_status_created
    ON ai_question_logs (llm_request_status, created_time DESC);

CREATE INDEX IF NOT EXISTS idx_web_evidence_sources_quality
    ON web_evidence_sources (official_source DESC, credibility_score DESC, source_rank);
