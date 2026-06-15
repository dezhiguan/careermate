CREATE TABLE IF NOT EXISTS agent_artifacts (
  id            BIGSERIAL PRIMARY KEY,
  artifact_id   VARCHAR(36) NOT NULL UNIQUE,
  user_id       BIGINT NOT NULL,
  session_id    VARCHAR(64),
  artifact_type VARCHAR(32) NOT NULL,
  title         VARCHAR(128) NOT NULL,
  summary       VARCHAR(500),
  ref_type      VARCHAR(32) NOT NULL,
  ref_id        VARCHAR(64) NOT NULL,
  metadata      JSONB,
  status        VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
  created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_agent_artifacts_user_created
  ON agent_artifacts(user_id, created_at DESC);

CREATE INDEX idx_agent_artifacts_user_type_created
  ON agent_artifacts(user_id, artifact_type, created_at DESC);

CREATE INDEX idx_agent_artifacts_user_session_created
  ON agent_artifacts(user_id, session_id, created_at DESC);

CREATE INDEX idx_agent_artifacts_user_ref
  ON agent_artifacts(user_id, ref_type, ref_id);

COMMENT ON TABLE agent_artifacts IS 'Agent 产物轻量索引层，不存正文';
