-- 简历版本「已导出」标记（设计 05 资产·简历版本：已导出 ✓）。
-- 导出 PDF/Word 成功后置 true。
ALTER TABLE resume_versions ADD COLUMN IF NOT EXISTS exported BOOLEAN NOT NULL DEFAULT false;

COMMENT ON COLUMN resume_versions.exported IS '是否已导出过 PDF/Word';
