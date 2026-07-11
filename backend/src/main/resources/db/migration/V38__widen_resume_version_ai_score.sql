-- aiScore(ATS 参考分) 取值范围 0-100，而原列 DECIMAL(3,1) 上限仅 99.9，满分 100 会溢出。
-- 扩至 DECIMAL(4,1)（上限 999.9）以容纳 100.0。
ALTER TABLE resume_versions
  ALTER COLUMN ai_score TYPE DECIMAL(4,1);
