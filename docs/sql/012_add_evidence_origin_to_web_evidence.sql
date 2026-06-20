ALTER TABLE web_evidence
ADD COLUMN IF NOT EXISTS evidence_origin VARCHAR(30) NOT NULL DEFAULT 'WEB_SEARCH';

UPDATE web_evidence
SET evidence_origin = 'LLM_GENERATED'
WHERE suggested_article_content ILIKE '%Model-generated answer:%'
  AND evidence_origin = 'WEB_SEARCH';

CREATE INDEX IF NOT EXISTS idx_web_evidence_origin
ON web_evidence (evidence_origin);
