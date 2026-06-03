CREATE TABLE job_matches (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT       NOT NULL,
    resume_id        BIGINT,
    job_title        VARCHAR(128) NOT NULL,
    company_name     VARCHAR(128),
    jd_content       TEXT         NOT NULL,
    match_score      INT          NOT NULL DEFAULT 0,
    match_level      VARCHAR(32)  NOT NULL DEFAULT 'UNKNOWN',
    matched_skills   JSONB        NOT NULL DEFAULT '[]'::jsonb,
    missing_skills   JSONB        NOT NULL DEFAULT '[]'::jsonb,
    strengths        JSONB        NOT NULL DEFAULT '[]'::jsonb,
    risks            JSONB        NOT NULL DEFAULT '[]'::jsonb,
    suggestions      JSONB        NOT NULL DEFAULT '[]'::jsonb,
    analysis_summary TEXT,
    status           VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_job_matches_user_id FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_job_matches_resume_id FOREIGN KEY (resume_id) REFERENCES resumes (id) ON DELETE SET NULL,
    CONSTRAINT chk_job_matches_match_score CHECK (match_score BETWEEN 0 AND 100),
    CONSTRAINT chk_job_matches_match_level CHECK (match_level IN ('HIGH', 'MEDIUM', 'LOW', 'UNKNOWN')),
    CONSTRAINT chk_job_matches_status CHECK (status IN ('ACTIVE', 'DELETED'))
);

CREATE INDEX idx_job_matches_user_id_created_at ON job_matches (user_id, created_at DESC);
CREATE INDEX idx_job_matches_user_id_status ON job_matches (user_id, status);
CREATE INDEX idx_job_matches_resume_id ON job_matches (resume_id);
CREATE INDEX idx_job_matches_match_score ON job_matches (match_score DESC);
