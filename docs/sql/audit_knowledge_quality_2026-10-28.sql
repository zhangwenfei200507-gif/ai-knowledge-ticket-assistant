-- Read-only audit queries. Do not run as a repair script.
-- Every mutation requires an approved, versioned migration or review action.

-- Empty or incomplete published articles.
SELECT id, title, category, status, updated_time
FROM knowledge_articles
WHERE status = 'PUBLISHED'
  AND (title IS NULL OR btrim(title) = '' OR content IS NULL OR length(btrim(content)) < 80)
ORDER BY id;

-- Duplicate article titles in the same domain.
SELECT lower(btrim(category)) AS category, lower(btrim(title)) AS normalized_title,
       count(*) AS article_count, array_agg(id ORDER BY id) AS article_ids
FROM knowledge_articles
WHERE status = 'PUBLISHED'
GROUP BY lower(btrim(category)), lower(btrim(title))
HAVING count(*) > 1
ORDER BY article_count DESC;

-- Published articles with obvious process-template residue.
SELECT id, title, category
FROM knowledge_articles
WHERE status = 'PUBLISHED'
  AND content ~* '(Knowledge Candidate|Observed answer|Purpose\s*:|Title\s*:|Runbook|SOP|Source document|Source chunk)';

-- Empty, duplicate, or orphaned semantic chunks.
SELECT id, article_id, section_title, chunk_index, length(coalesce(content, '')) AS content_length
FROM knowledge_article_chunks
WHERE content IS NULL OR btrim(content) = '' OR article_id IS NULL
ORDER BY article_id, chunk_index, id;

SELECT article_id, md5(lower(regexp_replace(content, E'\\s+', ' ', 'g'))) AS content_signature,
       count(*) AS chunk_count, array_agg(id ORDER BY id) AS chunk_ids
FROM knowledge_article_chunks
WHERE status <> 'ARCHIVED'
GROUP BY article_id, md5(lower(regexp_replace(content, E'\\s+', ' ', 'g')))
HAVING count(*) > 1
ORDER BY chunk_count DESC;

-- Documents with chunks that are not in a searchable lifecycle state.
SELECT d.id, d.original_filename, d.status, count(dc.id) AS chunk_count
FROM documents d
LEFT JOIN document_chunks dc ON dc.document_id = d.id
WHERE coalesce(d.status, '') NOT IN ('ACTIVE', 'ARCHIVED')
GROUP BY d.id, d.original_filename, d.status
ORDER BY d.id;

-- Web evidence that lacks reliable provenance or is still pending.
SELECT id, question, status, created_time
FROM web_evidence
WHERE status IN ('NEEDS_REVIEW', 'APPROVED')
  AND (question IS NULL OR btrim(question) = '' OR url IS NULL OR btrim(url) = '')
ORDER BY id;

-- Evidence sources that need metadata review.
SELECT id, evidence_id, title, url, source_type, official_source, source_status
FROM web_evidence_sources
WHERE url IS NULL OR btrim(url) = '' OR source_type IS NULL OR source_status IS NULL
ORDER BY evidence_id, id;

-- AI logs whose persisted trace is missing or whose answer is not grounded.
SELECT id, question, answer_mode, answer_engine, retrieval_failure_reason, created_time
FROM ai_question_logs
WHERE retrieval_trace_json IS NULL
   OR answer_mode IS NULL
   OR answer_engine IN ('NONE', 'DETERMINISTIC_FALLBACK');
