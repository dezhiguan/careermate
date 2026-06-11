ALTER TABLE agent_messages
  ADD COLUMN metadata JSONB;

COMMENT ON COLUMN agent_messages.metadata IS '结构化卡片/action 等,例 {"card":{...}}';
