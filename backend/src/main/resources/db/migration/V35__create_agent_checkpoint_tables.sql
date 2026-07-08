-- B3 Checkpoint 崩溃恢复 + HITL
CREATE TABLE IF NOT EXISTS agent_run (
    run_id          VARCHAR(64) PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    session_id      VARCHAR(64),
    workflow_name   VARCHAR(64),
    status          VARCHAR(20) NOT NULL,      -- RUNNING/PAUSED/DONE/FAILED
    parent_run_id   VARCHAR(64),
    started_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at     TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_run_user_status ON agent_run(user_id, status);

CREATE TABLE IF NOT EXISTS agent_checkpoint (
    id              BIGSERIAL PRIMARY KEY,
    run_id          VARCHAR(64) NOT NULL REFERENCES agent_run(run_id),
    step_index      INT NOT NULL,
    step_name       VARCHAR(64),
    state_blob      BYTEA NOT NULL,            -- 压缩后的 AgentState
    state_size      INT,
    state_hash      VARCHAR(64),               -- 相邻相同去重
    is_paused       BOOLEAN NOT NULL DEFAULT false,
    pause_reason    VARCHAR(80),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_ckpt_run_step ON agent_checkpoint(run_id, step_index);

COMMENT ON TABLE agent_run IS 'B3 Agent 运行(可断点续跑/HITL暂停)';
COMMENT ON TABLE agent_checkpoint IS 'B3 每节点边界 state 快照(压缩+hash去重)';
