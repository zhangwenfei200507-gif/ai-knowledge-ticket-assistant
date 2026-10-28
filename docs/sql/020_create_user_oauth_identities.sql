CREATE TABLE IF NOT EXISTS user_oauth_identities (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider VARCHAR(20) NOT NULL CHECK (provider IN ('google', 'github')),
    provider_subject VARCHAR(255) NOT NULL,
    linked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_oauth_provider_subject UNIQUE (provider, provider_subject),
    CONSTRAINT uq_user_oauth_provider_per_user UNIQUE (user_id, provider)
);

CREATE INDEX IF NOT EXISTS idx_user_oauth_identities_user_id
    ON user_oauth_identities(user_id);
