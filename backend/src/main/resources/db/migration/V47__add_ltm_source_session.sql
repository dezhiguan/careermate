-- A4 长期记忆可追溯来源会话（设计要求 source_session_id）。
-- 蒸馏改为按会话粒度后，每条 fact 记来源 JD 线会话 id（= agent_sessions.id / agent_messages.session_id）。
-- 聚合/无法归属场景下为空。
ALTER TABLE user_long_term_memory ADD COLUMN IF NOT EXISTS source_session_id BIGINT;

COMMENT ON COLUMN user_long_term_memory.source_session_id IS '该 fact 蒸馏自哪条会话（可选追溯）；聚合场景为空';
