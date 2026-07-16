-- 投递看板：用户「正在准备/推进的一个求职机会」及其阶段。
-- 对应设计稿「准备」看板：一机会一条记录，阶段 准备投递→约面→面试中→Offer谈薪→已结束。
-- 资产（简历版本等）仍 user 级独立，这里仅按 resume_version_id 弱引用，不级联。
CREATE TABLE job_applications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    jd_doc_id BIGINT,                         -- RAGForge JD docId；内推/无 JD 时可空
    company VARCHAR(128),
    role_title VARCHAR(200),
    stage VARCHAR(24) NOT NULL DEFAULT 'PREPARING',  -- PREPARING/INTERVIEW_SCHEDULED/INTERVIEWING/OFFER/CLOSED
    resume_version_id VARCHAR(36),            -- 该机会所用简历版本（弱引用，不外键）
    notes VARCHAR(1000),
    source VARCHAR(40) NOT NULL DEFAULT 'manual',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_active_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,                     -- 软删/归档
    CONSTRAINT fk_job_applications_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_job_applications_user ON job_applications(user_id);
CREATE INDEX idx_job_applications_user_stage ON job_applications(user_id, stage, deleted_at);

-- jd_id 强去重：同一用户对同一条 JD 只保留一条未删记录（内推无 jd 的不受约束）。
CREATE UNIQUE INDEX uq_job_applications_user_jd
    ON job_applications(user_id, jd_doc_id)
    WHERE jd_doc_id IS NOT NULL AND deleted_at IS NULL;
