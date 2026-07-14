-- P2 确定性事实校验 Gate：为简历版本增加 fact_check 结果列
-- 内容形如 {"status":"PASS|SUSPECT|ERROR","unsourcedFacts":[...],"checkedAt":"..."}，null 表示未校验
ALTER TABLE resume_versions
    ADD COLUMN IF NOT EXISTS fact_check JSONB;

COMMENT ON COLUMN resume_versions.fact_check IS 'P2 确定性事实校验结果 JSON：status(PASS/SUSPECT/ERROR) + unsourcedFacts + checkedAt';
