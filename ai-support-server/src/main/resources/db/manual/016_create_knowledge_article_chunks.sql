CREATE TABLE IF NOT EXISTS knowledge_article_chunks (
    id BIGSERIAL PRIMARY KEY,
    article_id BIGINT NOT NULL REFERENCES knowledge_articles(id),
    chunk_index INTEGER NOT NULL,
    section_title VARCHAR(160) NOT NULL,
    section_type VARCHAR(50) NOT NULL,
    concept_tags VARCHAR(500),
    content TEXT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_knowledge_article_chunks_article_status
    ON knowledge_article_chunks (article_id, status, chunk_index);

CREATE INDEX IF NOT EXISTS idx_knowledge_article_chunks_section_type
    ON knowledge_article_chunks (section_type);

CREATE INDEX IF NOT EXISTS idx_knowledge_article_chunks_content
    ON knowledge_article_chunks USING gin (to_tsvector('english', content));

CREATE INDEX IF NOT EXISTS idx_knowledge_article_chunks_tags
    ON knowledge_article_chunks USING gin (to_tsvector('english', coalesce(concept_tags, '')));
