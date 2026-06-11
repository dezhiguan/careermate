CREATE TABLE IF NOT EXISTS resume_versions (
  id                BIGSERIAL PRIMARY KEY,
  version_id        VARCHAR(36) NOT NULL UNIQUE,
  user_id           BIGINT NOT NULL,
  tenant_id         BIGINT NOT NULL DEFAULT 1,
  session_id        VARCHAR(64),
  source_resume_id  BIGINT,
  parent_version_id VARCHAR(36),
  target_jd_id      VARCHAR(64),
  target_jd_label   VARCHAR(128),
  version_name      VARCHAR(64) NOT NULL,
  content_markdown  TEXT NOT NULL,
  optimization_notes JSONB,
  ai_score          DECIMAL(3,1),
  created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_resume_versions_user_session
  ON resume_versions(user_id, session_id, created_at DESC);

COMMENT ON TABLE resume_versions IS 'AI 按 JD 生成的简历版本(本期不同步 RAGForge)';
