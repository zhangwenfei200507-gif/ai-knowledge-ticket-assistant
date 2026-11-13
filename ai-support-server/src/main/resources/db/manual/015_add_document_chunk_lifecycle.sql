ALTER TABLE document_chunks
    ADD COLUMN IF NOT EXISTS status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE document_chunks
    ADD COLUMN IF NOT EXISTS updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

UPDATE document_chunks
SET status = 'ACTIVE'
WHERE status IS NULL;

UPDATE document_chunks
SET updated_time = created_time
WHERE updated_time IS NULL;

CREATE INDEX IF NOT EXISTS idx_document_chunks_status
    ON document_chunks (status);

CREATE INDEX IF NOT EXISTS idx_document_chunks_document_status
    ON document_chunks (document_id, status, chunk_index);
