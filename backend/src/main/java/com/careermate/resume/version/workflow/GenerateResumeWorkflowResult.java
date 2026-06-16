package com.careermate.resume.version.workflow;

import java.util.Map;

/**
 * Workflow 成功完成后的结果，含前端卡片 Map。
 */
public record GenerateResumeWorkflowResult(Map<String, Object> card) {
}
