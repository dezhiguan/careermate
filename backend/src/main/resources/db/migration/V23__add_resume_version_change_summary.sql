ALTER TABLE resume_versions
  ADD COLUMN IF NOT EXISTS change_summary TEXT;

ALTER TABLE resume_versions
  ADD CONSTRAINT chk_resume_versions_change_summary_len
  CHECK (change_summary IS NULL OR char_length(change_summary) <= 200);

COMMENT ON COLUMN resume_versions.change_summary IS 'AI 生成的自然语言改写说明，200 字以内';

UPDATE resume_versions
SET change_summary = '对比原简历，我已根据目标 JD 调整表达重点，突出更匹配的技能、项目经历和岗位关键词。'
WHERE change_summary IS NULL;
