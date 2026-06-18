-- V21 已把无法解析的 target_jd_id 置 NULL；这里加一列保存原始字符串以便事后追溯。
-- 对于 V21 之后才出现脏数据的场景（如 dev/test 库被 truncate 又导入老数据），也能继续接住。

ALTER TABLE resume_versions
  ADD COLUMN IF NOT EXISTS legacy_target_jd_id_raw TEXT;

-- 没有备份历史可恢复，但加注释指引未来运维
COMMENT ON COLUMN resume_versions.legacy_target_jd_id_raw
  IS 'V21 之后保存写入失败的原始 target_jd_id（非 BIGINT 字符串），用于事后追溯';
