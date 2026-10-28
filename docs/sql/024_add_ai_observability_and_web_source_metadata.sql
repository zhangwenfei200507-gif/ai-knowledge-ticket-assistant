-- Controlled post-023 schema extension.
-- Apply only after 022 and 023 have been applied successfully.
-- This file is intentionally idempotent; it is not executed by the application in this phase.

ALTER TABLE ai_question_logs
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
