ALTER TABLE document_knowledge_candidates
ADD COLUMN IF NOT EXISTS suggested_article_id BIGINT;

ALTER TABLE document_knowledge_candidates
ADD COLUMN IF NOT EXISTS suggested_action VARCHAR(30) NOT NULL DEFAULT 'CREATE_ARTICLE';

CREATE INDEX IF NOT EXISTS idx_document_knowledge_candidates_suggested_article
ON document_knowledge_candidates (suggested_article_id);
