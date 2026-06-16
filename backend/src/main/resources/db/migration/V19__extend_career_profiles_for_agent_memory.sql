ALTER TABLE career_profiles
    ADD COLUMN IF NOT EXISTS target_salary_range VARCHAR(120),
    ADD COLUMN IF NOT EXISTS weakness_keywords JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS interview_weakness_summary VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS memory_summary VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS memory_updated_at TIMESTAMP;

ALTER TABLE agent_sessions
    ADD COLUMN IF NOT EXISTS conversation_summary TEXT,
    ADD COLUMN IF NOT EXISTS conversation_summary_updated_at TIMESTAMPTZ;
