CREATE TABLE IF NOT EXISTS agent_pending_actions (
  id           BIGSERIAL PRIMARY KEY,
  action_id    VARCHAR(64) NOT NULL UNIQUE,
  user_id      BIGINT NOT NULL,
  session_id   VARCHAR(64) NOT NULL,
  action_type  VARCHAR(48) NOT NULL,
  status       VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  payload      JSONB,
  expires_at   TIMESTAMP WITH TIME ZONE NOT NULL,
  consumed_at  TIMESTAMP WITH TIME ZONE,
  created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_agent_pending_actions_user_session
  ON agent_pending_actions(user_id, session_id, created_at DESC);

CREATE INDEX idx_agent_pending_actions_status_expires
  ON agent_pending_actions(status, expires_at);

COMMENT ON TABLE agent_pending_actions IS 'HITL 待确认高风险动作，不存完整 JD/简历正文';
