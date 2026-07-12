-- #5.10：简历生成运行态持久化，支撑崩溃/重启后的自愈（陈旧 RUNNING → FAILED，可重试）。
CREATE TABLE IF NOT EXISTS resume_generation_run (
    run_id      VARCHAR(64) PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    session_id  VARCHAR(64),
    jd_id       VARCHAR(64),
    status      VARCHAR(20) NOT NULL,      -- RUNNING/SUCCESS/FAILED
    error       VARCHAR(500),
    started_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_resume_gen_run_status ON resume_generation_run(status, started_at);
