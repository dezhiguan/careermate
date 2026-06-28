-- 去除租户模型：CareerMate 纯个人化，resume_versions.tenant_id 恒为默认值、无任何访问依赖，删除之。
ALTER TABLE resume_versions DROP COLUMN IF EXISTS tenant_id;
