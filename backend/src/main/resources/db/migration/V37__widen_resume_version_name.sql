-- BUG-07: version_name 原为 VARCHAR(64)，而更新 DTO 放行至 128 字符，
-- 用户改名 65~128 字符时触发 DB 层异常并裸露为 500 系统异常。
-- 扩至 VARCHAR(255)，覆盖用户改名上限与系统生成的“针对【公司】职位 · vN”显示名。
ALTER TABLE resume_versions
  ALTER COLUMN version_name TYPE VARCHAR(255);
