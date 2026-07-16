-- 暂存区：收藏但还没动的 JD（不占投递看板，一键转为机会）。user 级、软删。
CREATE TABLE IF NOT EXISTS saved_jobs (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    jd_doc_id   BIGINT      NOT NULL,       -- RAGForge JD docId（数字）
    company     VARCHAR(128),
    role_title  VARCHAR(200),
    saved_at    TIMESTAMP   NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMP
);

-- 同一用户同一 JD 只留一条收藏
CREATE UNIQUE INDEX IF NOT EXISTS uq_saved_jobs_user_jd
    ON saved_jobs(user_id, jd_doc_id)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_saved_jobs_user_time
    ON saved_jobs(user_id, saved_at DESC);
