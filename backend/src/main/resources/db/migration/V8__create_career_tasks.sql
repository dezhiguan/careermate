CREATE TABLE career_tasks (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(1000),
    category VARCHAR(40) NOT NULL,
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    status VARCHAR(20) NOT NULL DEFAULT 'TODO',
    due_date DATE,
    source VARCHAR(40) NOT NULL DEFAULT 'manual',
    related_type VARCHAR(40),
    related_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_career_tasks_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_career_tasks_user_id ON career_tasks(user_id);
CREATE INDEX idx_career_tasks_user_status ON career_tasks(user_id, status);
CREATE INDEX idx_career_tasks_user_deleted ON career_tasks(user_id, deleted_at);
