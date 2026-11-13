CREATE TABLE IF NOT EXISTS web_evidence (
    id BIGSERIAL PRIMARY KEY,
    question TEXT NOT NULL,
    query VARCHAR(300) NOT NULL,
    title VARCHAR(300),
    url TEXT,
    domain VARCHAR(200),
    snippet TEXT,
    extracted_content TEXT,
    suggested_article_title VARCHAR(150),
    suggested_article_content TEXT,
    category VARCHAR(50),
    credibility_score NUMERIC(5, 2),
    status VARCHAR(30) NOT NULL DEFAULT 'NEEDS_REVIEW',
    created_by BIGINT,
    reviewed_by BIGINT,
    converted_article_id BIGINT,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_time TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_web_evidence_status ON web_evidence(status);
CREATE INDEX IF NOT EXISTS idx_web_evidence_created_time ON web_evidence(created_time DESC);
CREATE INDEX IF NOT EXISTS idx_web_evidence_domain ON web_evidence(domain);
