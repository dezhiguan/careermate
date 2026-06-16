package com.careermate.resume.version.workflow;

/**
 * Workflow 在某一步失败时的结构化信息，用于 trace 与失败卡片。
 */
public record GenerateResumeWorkflowFailure(
        GenerateResumeWorkflowStep failedStep,
        String errorCode,
        String userMessage,
        boolean retryable
) {
}
