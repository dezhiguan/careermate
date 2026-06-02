CREATE TABLE agent_sessions (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    intent VARCHAR(64),
    task_type VARCHAR(64),
    title VARCHAR(255),
    total_latency_ms BIGINT,
    llm_latency_ms BIGINT,
    input_tokens INTEGER,
    output_tokens INTEGER,
    estimated_cost NUMERIC(12, 6),
    model_provider VARCHAR(64),
    model_name VARCHAR(128),
    tool_call_count INTEGER NOT NULL DEFAULT 0,
    error_code VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_agent_sessions_session_id UNIQUE (session_id),
    CONSTRAINT fk_agent_sessions_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_agent_sessions_user_id ON agent_sessions(user_id);
CREATE INDEX idx_agent_sessions_session_id ON agent_sessions(session_id);
CREATE INDEX idx_agent_sessions_user_status_created ON agent_sessions(user_id, status, created_at DESC);

CREATE TABLE agent_messages (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    message_type VARCHAR(32) NOT NULL DEFAULT 'text',
    sequence_no INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_agent_messages_session_id FOREIGN KEY (session_id) REFERENCES agent_sessions(id) ON DELETE CASCADE,
    CONSTRAINT fk_agent_messages_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_agent_messages_session_sequence ON agent_messages(session_id, sequence_no);
CREATE INDEX idx_agent_messages_user_id ON agent_messages(user_id);

CREATE TABLE agent_tool_calls (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    message_id BIGINT,
    tool_name VARCHAR(128) NOT NULL,
    tool_layer VARCHAR(64),
    request_params_summary JSONB NOT NULL DEFAULT '{}'::jsonb,
    response_summary JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(32) NOT NULL DEFAULT 'SUCCESS',
    latency_ms BIGINT,
    rag_latency_ms BIGINT,
    fallback_used BOOLEAN NOT NULL DEFAULT FALSE,
    error_code VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_agent_tool_calls_session_id FOREIGN KEY (session_id) REFERENCES agent_sessions(id) ON DELETE CASCADE,
    CONSTRAINT fk_agent_tool_calls_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_agent_tool_calls_message_id FOREIGN KEY (message_id) REFERENCES agent_messages(id) ON DELETE SET NULL
);

CREATE INDEX idx_agent_tool_calls_session_id ON agent_tool_calls(session_id);
CREATE INDEX idx_agent_tool_calls_user_id ON agent_tool_calls(user_id);
CREATE INDEX idx_agent_tool_calls_tool_name ON agent_tool_calls(tool_name);
CREATE INDEX idx_agent_tool_calls_created_at ON agent_tool_calls(created_at);

CREATE TABLE agent_task_states (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    task_type VARCHAR(64),
    current_step INTEGER NOT NULL DEFAULT 0,
    total_steps INTEGER NOT NULL DEFAULT 0,
    state_data JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(32) NOT NULL DEFAULT 'RUNNING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_agent_task_states_session_id UNIQUE (session_id),
    CONSTRAINT fk_agent_task_states_session_id FOREIGN KEY (session_id) REFERENCES agent_sessions(id) ON DELETE CASCADE,
    CONSTRAINT fk_agent_task_states_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_agent_task_states_session_id ON agent_task_states(session_id);
CREATE INDEX idx_agent_task_states_user_id ON agent_task_states(user_id);
CREATE INDEX idx_agent_task_states_status ON agent_task_states(status);
