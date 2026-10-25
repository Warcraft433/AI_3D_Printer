-- Flyway V3: Refresh Token Rotation and Security Table for PrintGuard AI
-- Supports token family revocation, hash lookup, and audit trail

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL REFERENCES auth_user(id) ON DELETE CASCADE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_hash ON refresh_tokens(token_hash);

-- Enable Row Level Security (RLS) for least-privilege tenant isolation
ALTER TABLE refresh_tokens ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies WHERE tablename = 'refresh_tokens' AND policyname = 'users_manage_own_refresh_tokens'
    ) THEN
        CREATE POLICY users_manage_own_refresh_tokens ON refresh_tokens
            FOR ALL
            USING (user_id = NULLIF(current_setting('app.current_user_id', true), '')::bigint);
    END IF;
END $$;
