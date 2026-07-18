-- 面试记录「弱项」标记（设计 05 资产·面试记录：弱项·系统设计）。
-- 会话完成时按题型平均分算出最弱题型，存此字段。
ALTER TABLE interview_sessions ADD COLUMN IF NOT EXISTS weakness VARCHAR(40);

COMMENT ON COLUMN interview_sessions.weakness IS '本场最弱题型（平均分最低且低于阈值）；无则空';
