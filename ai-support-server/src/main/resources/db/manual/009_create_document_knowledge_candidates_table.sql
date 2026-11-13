CREATE TABLE IF NOT EXISTS document_knowledge_candidates (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL,
    chunk_id BIGINT,
    title VARCHAR(180) NOT NULL,
    content TEXT NOT NULL,
    category VARCHAR(50),
    importance_score NUMERIC(5, 2),
    suggested_article_id BIGINT,
    suggested_action VARCHAR(30) NOT NULL DEFAULT 'CREATE_ARTICLE',
    status VARCHAR(30) NOT NULL DEFAULT 'NEEDS_REVIEW',
    created_by BIGINT,
    reviewed_by BIGINT,
    converted_article_id BIGINT,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_time TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_document_knowledge_candidates_status
ON document_knowledge_candidates (status);

CREATE INDEX IF NOT EXISTS idx_document_knowledge_candidates_document_id
ON document_knowledge_candidates (document_id);

CREATE INDEX IF NOT EXISTS idx_document_knowledge_candidates_created_time
ON document_knowledge_candidates (created_time DESC);
