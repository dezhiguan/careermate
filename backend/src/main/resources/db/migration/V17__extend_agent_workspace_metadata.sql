ALTER TABLE agent_sessions
  ADD COLUMN workspace_metadata JSONB,
  ADD COLUMN goal_text VARCHAR(500);

COMMENT ON COLUMN agent_sessions.workspace_metadata IS 'AgentWorkspace 上下文元数据（JSON）';
COMMENT ON COLUMN agent_sessions.goal_text IS '工作空间目标简述';
