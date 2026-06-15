CREATE TABLE IF NOT EXISTS ai_question_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    question TEXT NOT NULL,
    answer TEXT NOT NULL,
    source_article_id BIGINT,
    source_title VARCHAR(150),
    suggest_create_ticket BOOLEAN NOT NULL DEFAULT FALSE,
    created_ticket_id BIGINT,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
