CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(64)  NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    email           VARCHAR(128),
    role            VARCHAR(32)  NOT NULL DEFAULT 'USER',
    status          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE INDEX idx_users_username ON users (username);
CREATE INDEX idx_users_email ON users (email);
CREATE INDEX idx_users_status ON users (status);

CREATE TABLE user_profiles (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT       NOT NULL,
    skills           JSONB        NOT NULL DEFAULT '[]'::jsonb,
    experience_years NUMERIC(4, 1),
    target_positions JSONB        NOT NULL DEFAULT '[]'::jsonb,
    target_companies JSONB        NOT NULL DEFAULT '[]'::jsonb,
    preferences      JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_user_profiles_user_id UNIQUE (user_id),
    CONSTRAINT fk_user_profiles_user_id FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_user_profiles_user_id ON user_profiles (user_id);

CREATE TABLE security_audit_logs (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT,
    action_type    VARCHAR(64)  NOT NULL,
    action_detail  TEXT,
    resource_type  VARCHAR(64),
    resource_id    VARCHAR(64),
    success        BOOLEAN      NOT NULL DEFAULT TRUE,
    failure_reason TEXT,
    ip_address     VARCHAR(64),
    user_agent     VARCHAR(512),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_security_audit_logs_user_id FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX idx_security_audit_logs_user_id ON security_audit_logs (user_id);
CREATE INDEX idx_security_audit_logs_action_type ON security_audit_logs (action_type);
CREATE INDEX idx_security_audit_logs_created_at ON security_audit_logs (created_at);
CREATE INDEX idx_security_audit_logs_resource ON security_audit_logs (resource_type, resource_id);
