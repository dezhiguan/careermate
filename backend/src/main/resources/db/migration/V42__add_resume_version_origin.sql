-- P4 简历就地编辑：为版本增加 origin 列，区分 AI 生成版 / 手工编辑派生版
-- 决策②=B：首次手工编辑 AI 版会 fork 出 origin=MANUAL_EDIT 的新版本，保留 AI 原版可追溯
ALTER TABLE resume_versions
    ADD COLUMN IF NOT EXISTS origin VARCHAR(32) NOT NULL DEFAULT 'GENERATED';

ALTER TABLE resume_versions
    ADD CONSTRAINT chk_resume_versions_origin CHECK (origin IN ('GENERATED', 'MANUAL_EDIT'));

COMMENT ON COLUMN resume_versions.origin IS 'P4 版本来源：GENERATED / MANUAL_EDIT';
