-- A5 评测：LLM-as-judge 打分落库
CREATE TABLE IF NOT EXISTS eval_results (
    id                BIGSERIAL PRIMARY KEY,
    eval_suite_id     VARCHAR(64) NOT NULL,
    scenario_id       VARCHAR(64) NOT NULL,
    model_provider    VARCHAR(64),
    judge_provider    VARCHAR(64),
    judge_scores      JSONB,                -- {relevance, correctness, citation_faithfulness, ...}
    overall_score     DOUBLE PRECISION,
    token_in          INT,
    token_out         INT,
    cost              DOUBLE PRECISION,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_eval_results_suite ON eval_results(eval_suite_id, scenario_id);

COMMENT ON TABLE eval_results IS 'A5 评测：多 provider × scenario 的 LLM-as-judge 打分（含引用忠实度）';
