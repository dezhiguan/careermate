-- V36: 登录体系增强（邮箱验证、账号锁定、会话管理、密码历史）

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS email_verified       BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS email_verified_at    TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS username_updated_at  TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS login_failed_count   INT          NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS login_locked_until   TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS terms_agreed_at      TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS terms_version        VARCHAR(16),
    ADD COLUMN IF NOT EXISTS onboarding_completed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS pending_deletion_at  TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS deletion_scheduled_at TIMESTAMPTZ;

CREATE TABLE IF NOT EXISTS user_login_sessions (
    id          VARCHAR(64)  PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_name VARCHAR(128),
    ip_address  VARCHAR(64),
    user_agent  VARCHAR(512),
    remember_me BOOLEAN      NOT NULL DEFAULT FALSE,
    expires_at  TIMESTAMPTZ  NOT NULL,
    last_active TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_uls_user_id ON user_login_sessions(user_id, expires_at);
CREATE INDEX IF NOT EXISTS idx_uls_expires_at ON user_login_sessions(expires_at);

CREATE TABLE IF NOT EXISTS user_password_history (
    id            BIGSERIAL    PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_uph_user_id ON user_password_history(user_id, created_at DESC);
