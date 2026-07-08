package com.careermate.agent.dag;

/** B2：单个工具最终失败信息。 */
public record ToolFailure(String id, String tool, String errorCode, String errorMessage) {
}
