UPDATE document_knowledge_candidates
SET suggested_action = 'REORGANIZE_KNOWLEDGE'
WHERE suggested_action = 'MERGE_ARTICLE';

UPDATE document_knowledge_candidates
SET suggested_action = 'CREATE_KNOWLEDGE_PACK'
WHERE suggested_action = 'CREATE_ARTICLE';
