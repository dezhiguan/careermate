CREATE TABLE resumes (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT       NOT NULL,
    title        VARCHAR(128) NOT NULL,
    content      TEXT         NOT NULL,
    source_type  VARCHAR(32)  NOT NULL DEFAULT 'TEXT',
    is_default   BOOLEAN      NOT NULL DEFAULT FALSE,
    status       VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_resumes_user_id FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_resumes_source_type CHECK (source_type IN ('TEXT')),
    CONSTRAINT chk_resumes_status CHECK (status IN ('ACTIVE', 'DELETED'))
);

CREATE INDEX idx_resumes_user_id_created_at ON resumes (user_id, created_at DESC);
CREATE INDEX idx_resumes_user_id_default ON resumes (user_id, is_default);
CREATE INDEX idx_resumes_user_id_status ON resumes (user_id, status);
