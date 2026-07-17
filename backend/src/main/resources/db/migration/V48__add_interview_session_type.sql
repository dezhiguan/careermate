-- 面试记录区分「真实复盘 / 模拟面试」（设计 05 资产·面试记录 类型过滤）。
-- 存量均为模拟练习，默认 MOCK；真实面试复盘录入时置 REAL。
ALTER TABLE interview_sessions ADD COLUMN IF NOT EXISTS session_type VARCHAR(10) NOT NULL DEFAULT 'MOCK';

COMMENT ON COLUMN interview_sessions.session_type IS '面试类型：MOCK 模拟 / REAL 真实复盘';
