-- A4 长期语义记忆（pgvector）。扩展由 DBA 预装；已装时 IF NOT EXISTS 为 no-op，不需超管。
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS user_long_term_memory (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT NOT NULL,
    fact_type      VARCHAR(20) NOT NULL,          -- PREFERENCE/SKILL/EXPERIENCE/GOAL/CONSTRAINT
    fact_text      TEXT NOT NULL,
    confidence     DOUBLE PRECISION NOT NULL DEFAULT 0.6,
    embedding      vector(1024),
    superseded_by  BIGINT,                        -- 被哪条 fact 取代（矛盾/更新链，不删旧）
    deleted        BOOLEAN NOT NULL DEFAULT false, -- 用户"忘掉这条"软删
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ltm_user ON user_long_term_memory(user_id);
CREATE INDEX IF NOT EXISTS idx_ltm_embedding ON user_long_term_memory
    USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

COMMENT ON TABLE user_long_term_memory IS 'A4 长期语义记忆：episodic 蒸馏出的结构化 fact + 1024 维向量';
