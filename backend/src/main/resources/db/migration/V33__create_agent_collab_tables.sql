-- B1 Writer-Critic Debate：进程内多 agent 协作会话与消息（独立于 chat 的 agent_messages）
CREATE TABLE IF NOT EXISTS agent_collab_session (
    id             VARCHAR(64) PRIMARY KEY,
    type           VARCHAR(32) NOT NULL,          -- DEBATE
    participants   JSONB,                          -- ["writer","critic"]
    config         JSONB,                          -- {maxRounds,consensusThreshold}
    status         VARCHAR(32) NOT NULL,           -- RUNNING/CONSENSUS/MAX_ROUND/ABORTED
    started_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at    TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS agent_collab_message (
    id             BIGSERIAL PRIMARY KEY,
    session_id     VARCHAR(64) NOT NULL REFERENCES agent_collab_session(id),
    from_agent     VARCHAR(64) NOT NULL,           -- writer / critic
    to_agent       VARCHAR(64) NOT NULL,
    msg_type       VARCHAR(32),                    -- DRAFT / REVIEW / REVISION
    round_no       INT NOT NULL,
    payload        JSONB,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_collab_msg_session ON agent_collab_message(session_id, round_no);

COMMENT ON TABLE agent_collab_session IS 'B1 Writer-Critic debate 协作会话';
COMMENT ON TABLE agent_collab_message IS 'B1 debate 每轮消息链（writer 出稿 / critic 评审）';
