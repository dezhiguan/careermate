-- A3 反思闭环：Plan-Act-Reflect-Revise 落库，便于回放
CREATE TABLE IF NOT EXISTS agent_plan (
    id                BIGSERIAL PRIMARY KEY,
    run_id            VARCHAR(64) NOT NULL,
    round_no          INT NOT NULL,
    goals             JSONB,
    subgoals          JSONB,
    success_criteria  JSONB,
    revised_from      BIGINT,
    sanity_status     VARCHAR(20),          -- PASSED / REJECTED / RETRIED
    sanity_reason     TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_agent_plan_run ON agent_plan(run_id, round_no);

CREATE TABLE IF NOT EXISTS agent_reflection (
    id                BIGSERIAL PRIMARY KEY,
    run_id            VARCHAR(64) NOT NULL,
    round_no          INT NOT NULL,
    plan_id           BIGINT,
    satisfied         BOOLEAN NOT NULL,
    confidence        DOUBLE PRECISION,
    gaps              JSONB,
    suggestions       JSONB,
    verdict           VARCHAR(20),          -- REVISE / ACCEPT / FAIL
    reflector_model   VARCHAR(64),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_agent_reflection_run ON agent_reflection(run_id, round_no);

COMMENT ON TABLE agent_plan IS 'A3 反思闭环：每轮 plan（含修订链与 sanity 结果）';
COMMENT ON TABLE agent_reflection IS 'A3 反思闭环：每轮 reflector 判定（跨家模型评分）';
