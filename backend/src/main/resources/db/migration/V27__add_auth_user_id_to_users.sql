ALTER TABLE users
    ADD COLUMN IF NOT EXISTS auth_user_id BIGINT;

CREATE UNIQUE INDEX IF NOT EXISTS uk_users_auth_user_id
    ON users (auth_user_id)
    WHERE auth_user_id IS NOT NULL;
