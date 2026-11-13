ALTER TABLE ai_question_logs
    ADD COLUMN IF NOT EXISTS answer_mode VARCHAR(30),
    ADD COLUMN IF NOT EXISTS answer_engine VARCHAR(30),
    ADD COLUMN IF NOT EXISTS internal_attempted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS internal_sufficient BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS web_attempted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS web_succeeded BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS web_sufficient BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS llm_attempted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS llm_succeeded BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS retrieval_failure_reason VARCHAR(100);

UPDATE ai_question_logs
SET answer_mode = CASE
        WHEN source_type IN ('ARTICLE', 'DOCUMENT') THEN 'INTERNAL_RAG'
        WHEN source_type = 'WEB' THEN 'WEB_SEARCH'
        WHEN source_type = 'LLM' THEN 'AI_ONLY'
        ELSE 'NONE'
    END,
    answer_engine = CASE
        WHEN source_type = 'LLM' THEN 'LLM'
        ELSE 'DETERMINISTIC'
    END
WHERE answer_mode IS NULL OR answer_engine IS NULL;

ALTER TABLE ai_question_logs
    ALTER COLUMN answer_mode SET NOT NULL,
    ALTER COLUMN answer_engine SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_ai_question_logs_answer_mode_created
    ON ai_question_logs (answer_mode, created_time DESC);
