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
