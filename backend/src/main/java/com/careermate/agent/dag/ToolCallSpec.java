package com.careermate.agent.dag;

import java.util.List;
import java.util.Map;

/**
 * B2：LLM 一轮输出的单个工具调用声明。
 *
 * @param id        本次调用 id（用于被 dependsOn 引用 / 占位符 $id[..].field）
 * @param tool      工具名
 * @param args      参数（可含占位符 "$otherId[0].field"）
 * @param dependsOn 依赖的其它调用 id（拓扑排序用）
 */
public record ToolCallSpec(String id, String tool, Map<String, Object> args, List<String> dependsOn) {

    public List<String> dependsOnOrEmpty() {
        return dependsOn == null ? List.of() : dependsOn;
    }
}
