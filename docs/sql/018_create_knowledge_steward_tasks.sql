CREATE TABLE IF NOT EXISTS knowledge_steward_tasks (
    id BIGSERIAL PRIMARY KEY,
    source_type VARCHAR(30) NOT NULL,
    source_id BIGINT NOT NULL,
    source_origin VARCHAR(30),
    source_title VARCHAR(300),
    source_question TEXT,
    source_excerpt TEXT,
    recommended_action VARCHAR(40) NOT NULL,
    target_domain VARCHAR(80) NOT NULL,
    target_article_id BIGINT,
    target_article_title VARCHAR(300),
    target_section VARCHAR(120),
    domain_confidence NUMERIC(5, 2) NOT NULL DEFAULT 0,
    coverage_confidence NUMERIC(5, 2) NOT NULL DEFAULT 0,
    source_quality NUMERIC(5, 2) NOT NULL DEFAULT 0,
    duplicate_risk NUMERIC(5, 2) NOT NULL DEFAULT 0,
    proposal_title VARCHAR(300) NOT NULL,
    proposal_content TEXT NOT NULL,
    reasoning TEXT,
    warnings TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'NEEDS_ADMIN_REVIEW',
    created_by BIGINT,
    reviewed_by BIGINT,
    converted_article_id BIGINT,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_time TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_knowledge_steward_tasks_source_active
    ON knowledge_steward_tasks (source_type, source_id)
    WHERE status IN ('NEEDS_ADMIN_REVIEW', 'APPROVED');

CREATE INDEX IF NOT EXISTS idx_knowledge_steward_tasks_status
    ON knowledge_steward_tasks (status, created_time DESC);

CREATE INDEX IF NOT EXISTS idx_knowledge_steward_tasks_domain
    ON knowledge_steward_tasks (target_domain, status);
