package com.careermate.resume.version.workflow;

/**
 * 按 JD 生成简历 Workflow 的显式步骤。
 * T12 HITL 可在 SAVE_VERSION 前插入人工确认步骤。
 */
public enum GenerateResumeWorkflowStep {

    LOAD_WORKSPACE,
    LOAD_RESUME,
    LOAD_JD,
    ANALYZE_GAP,
    GENERATE_RESUME,
    QUALITY_CHECK,
    SAVE_VERSION,
    EMIT_CARD;

    private static final String TRACE_PREFIX = "workflow.generate_resume_from_jd.";

    public String traceName() {
        return TRACE_PREFIX + name();
    }
}
