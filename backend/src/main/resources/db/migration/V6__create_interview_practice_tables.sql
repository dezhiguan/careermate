CREATE TABLE interview_sessions (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT       NOT NULL,
    resume_id           BIGINT,
    job_match_id        BIGINT,
    title               VARCHAR(128) NOT NULL,
    status              VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    total_questions     INT          NOT NULL DEFAULT 0,
    answered_questions  INT          NOT NULL DEFAULT 0,
    average_score       INT,
    summary             TEXT,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_interview_sessions_user_id FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_interview_sessions_resume_id FOREIGN KEY (resume_id) REFERENCES resumes (id) ON DELETE SET NULL,
    CONSTRAINT fk_interview_sessions_job_match_id FOREIGN KEY (job_match_id) REFERENCES job_matches (id) ON DELETE SET NULL,
    CONSTRAINT chk_interview_sessions_status CHECK (status IN ('ACTIVE', 'COMPLETED', 'DELETED')),
    CONSTRAINT chk_interview_sessions_average_score CHECK (average_score IS NULL OR average_score BETWEEN 0 AND 100)
);

CREATE INDEX idx_interview_sessions_user_created ON interview_sessions (user_id, created_at DESC);
CREATE INDEX idx_interview_sessions_user_status ON interview_sessions (user_id, status);
CREATE INDEX idx_interview_sessions_resume_id ON interview_sessions (resume_id);
CREATE INDEX idx_interview_sessions_job_match_id ON interview_sessions (job_match_id);

CREATE TABLE interview_questions (
    id                BIGSERIAL PRIMARY KEY,
    session_id        BIGINT       NOT NULL,
    user_id           BIGINT       NOT NULL,
    question_no       INT          NOT NULL,
    question_type     VARCHAR(32)  NOT NULL,
    question_text     TEXT         NOT NULL,
    reference_points  JSONB        NOT NULL DEFAULT '[]'::jsonb,
    answer_text       TEXT,
    score             INT,
    feedback          TEXT,
    strengths         JSONB        NOT NULL DEFAULT '[]'::jsonb,
    improvements      JSONB        NOT NULL DEFAULT '[]'::jsonb,
    status            VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_interview_questions_session_id FOREIGN KEY (session_id) REFERENCES interview_sessions (id) ON DELETE CASCADE,
    CONSTRAINT fk_interview_questions_user_id FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_interview_questions_type CHECK (question_type IN ('PROJECT', 'SKILL', 'GAP', 'BEHAVIOR', 'SYSTEM_DESIGN')),
    CONSTRAINT chk_interview_questions_status CHECK (status IN ('PENDING', 'ANSWERED')),
    CONSTRAINT chk_interview_questions_score CHECK (score IS NULL OR score BETWEEN 0 AND 100)
);

CREATE INDEX idx_interview_questions_session_no ON interview_questions (session_id, question_no);
CREATE INDEX idx_interview_questions_user_id ON interview_questions (user_id);
CREATE INDEX idx_interview_questions_status ON interview_questions (status);
