ALTER TABLE agent_sessions
  ADD COLUMN workspace_type VARCHAR(16) NOT NULL DEFAULT 'CHAT',
  ADD COLUMN jd_id          VARCHAR(64),
  ADD COLUMN jd_snapshot    JSONB;

CREATE INDEX idx_agent_sessions_user_type
  ON agent_sessions(user_id, workspace_type, created_at DESC);

COMMENT ON COLUMN agent_sessions.workspace_type IS 'CHAT=纯对话 / JD_PREP=JD 准备空间';
COMMENT ON COLUMN agent_sessions.jd_id IS '当 workspace_type=JD_PREP 时关联的 JD docId';
COMMENT ON COLUMN agent_sessions.jd_snapshot IS 'JD 元信息快照,防 JD 变更影响';
