-- Rollback is intentionally explicit and destructive. Review the deployment snapshot first.
-- Do not run automatically. Preserve the audit export before dropping observability fields.

ALTER TABLE ai_question_logs
    DROP COLUMN IF EXISTS total_latency_ms,
    DROP COLUMN IF EXISTS retrieval_trace_json,
    DROP COLUMN IF EXISTS llm_provider,
    DROP COLUMN IF EXISTS llm_model,
    DROP COLUMN IF EXISTS llm_input_tokens,
    DROP COLUMN IF EXISTS llm_output_tokens,
    DROP COLUMN IF EXISTS llm_total_tokens,
    DROP COLUMN IF EXISTS llm_estimated_cost,
    DROP COLUMN IF EXISTS llm_currency,
    DROP COLUMN IF EXISTS llm_usage_estimated,
    DROP COLUMN IF EXISTS llm_retry_count,
    DROP COLUMN IF EXISTS llm_latency_ms,
    DROP COLUMN IF EXISTS llm_request_status,
    DROP COLUMN IF EXISTS llm_error_category;

ALTER TABLE web_evidence_sources
    DROP COLUMN IF EXISTS source_type,
    DROP COLUMN IF EXISTS official_source,
    DROP COLUMN IF EXISTS published_time,
    DROP COLUMN IF EXISTS retrieved_time,
    DROP COLUMN IF EXISTS source_status;
