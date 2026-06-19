ALTER TABLE users
    ADD COLUMN IF NOT EXISTS platform_role VARCHAR(32) NOT NULL DEFAULT 'USER',
    ADD COLUMN IF NOT EXISTS session_version BIGINT NOT NULL DEFAULT 0;

UPDATE users
SET platform_role = COALESCE(NULLIF(role, ''), 'USER')
WHERE platform_role IS NULL OR platform_role = '';
