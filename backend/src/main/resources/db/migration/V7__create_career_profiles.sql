CREATE TABLE career_profiles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE,
    target_role VARCHAR(120),
    target_city VARCHAR(120),
    seniority VARCHAR(80),
    work_mode VARCHAR(80),
    skill_keywords JSONB NOT NULL DEFAULT '[]'::jsonb,
    preference_summary VARCHAR(500),
    source VARCHAR(40) NOT NULL DEFAULT 'agent',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_career_profiles_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_career_profiles_user_id ON career_profiles(user_id);
