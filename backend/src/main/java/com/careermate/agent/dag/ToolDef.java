package com.careermate.agent.dag;

import java.util.List;

/**
 * B2：工具执行策略。
 *
 * @param tool           工具名
 * @param fallbackTool   降级工具（可空）
 * @param maxRetry       最大重试次数
 * @param requiredFields 输出必须非空的字段（轻量早校验；缺失视为脏输出触发重试）
 */
public record ToolDef(String tool, String fallbackTool, int maxRetry, List<String> requiredFields) {
    public List<String> requiredFieldsOrEmpty() {
        return requiredFields == null ? List.of() : requiredFields;
    }
}
