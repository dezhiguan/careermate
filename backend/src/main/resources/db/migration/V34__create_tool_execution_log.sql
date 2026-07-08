-- B2 多工具并发 DAG：工具执行日志（可观测）
CREATE TABLE IF NOT EXISTS tool_execution_log (
    id              BIGSERIAL PRIMARY KEY,
    batch_id        VARCHAR(64),               -- 同一 LLM 输出的一组 tool call
    call_id         VARCHAR(64),               -- ToolCallSpec.id
    tool_name       VARCHAR(64) NOT NULL,
    attempt_no      INT NOT NULL DEFAULT 0,
    depends_on      JSONB,
    status          VARCHAR(20),               -- SUCCESS/RETRY/FALLBACK/FAILED/SCHEMA_INVALID
    duration_ms     INT,
    result_summary  TEXT,
    error_code      VARCHAR(40),
    error_message   TEXT,
    trace_id        VARCHAR(64),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_tool_log_batch ON tool_execution_log(batch_id);
CREATE INDEX IF NOT EXISTS idx_tool_log_name_status ON tool_execution_log(tool_name, status, created_at);

COMMENT ON TABLE tool_execution_log IS 'B2 工具 DAG 执行日志：每次尝试的状态/耗时/错误';
