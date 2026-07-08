-- A2 补漏：持久化 fast/deep 分层判定到会话
ALTER TABLE agent_sessions
    ADD COLUMN IF NOT EXISTS path_mode VARCHAR(10);

COMMENT ON COLUMN agent_sessions.path_mode IS 'Agent 执行路径分层：FAST（单轮直答）/ DEEP（深度多轮）';
