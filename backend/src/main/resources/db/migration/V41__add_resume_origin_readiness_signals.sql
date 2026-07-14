-- P1 无上传冷启动三级降级建档：为简历增加 来源 / 就绪度 / 信号来源 三列
-- origin/readiness 带默认值，存量简历语义不变（视为已上传、已就绪）
ALTER TABLE resumes
    ADD COLUMN IF NOT EXISTS origin         VARCHAR(32) NOT NULL DEFAULT 'UPLOAD',
    ADD COLUMN IF NOT EXISTS readiness      VARCHAR(32) NOT NULL DEFAULT 'READY',
    ADD COLUMN IF NOT EXISTS source_signals JSONB;

-- 约束：与代码枚举一致（DRAFT_SKELETON 仅冷启动骨架使用）
ALTER TABLE resumes
    ADD CONSTRAINT chk_resumes_origin CHECK (origin IN ('UPLOAD', 'MANUAL', 'COLD_START'));
ALTER TABLE resumes
    ADD CONSTRAINT chk_resumes_readiness CHECK (readiness IN ('READY', 'DRAFT_SKELETON'));

COMMENT ON COLUMN resumes.origin IS 'P1 简历来源：UPLOAD/MANUAL/COLD_START';
COMMENT ON COLUMN resumes.readiness IS 'P1 就绪度：READY/DRAFT_SKELETON（草稿骨架未填完不可导出）';
COMMENT ON COLUMN resumes.source_signals IS 'P1 冷启动信号来源 JSON';
