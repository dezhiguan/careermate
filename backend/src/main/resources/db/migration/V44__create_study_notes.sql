-- 八股题库（个人）：用户从平台题库收录的题 + 自己的手写答案，user 级、跨机会复用。
CREATE TABLE IF NOT EXISTS study_notes (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT      NOT NULL,
    question      TEXT        NOT NULL,
    question_hash VARCHAR(64) NOT NULL,           -- 归一化问题的 sha256，用于同用户去重
    skill_tag     VARCHAR(64),                    -- 技能分类（Java并发/MySQL/算法/行为面…）
    answer        TEXT,                           -- 用户手写答案
    created_at    TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP   NOT NULL DEFAULT now(),
    deleted_at    TIMESTAMP
);

-- 同一用户同一题只留一条（软删不占用）
CREATE UNIQUE INDEX IF NOT EXISTS uq_study_notes_user_q
    ON study_notes(user_id, question_hash)
    WHERE deleted_at IS NULL;

-- 按用户 + 技能 + 最近更新检索
CREATE INDEX IF NOT EXISTS idx_study_notes_user_skill
    ON study_notes(user_id, skill_tag, updated_at DESC);
