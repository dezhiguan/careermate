package com.careermate.agent.dag;

import java.util.List;
import java.util.Map;

/**
 * B2：一批工具执行的聚合结果。partial-success：成功与失败并存，失败也透明返回（供喂回 LLM）。
 *
 * @param successes id → 工具输出
 * @param failures  失败列表
 */
public record ToolBatchResult(Map<String, Object> successes, List<ToolFailure> failures) {

    public boolean allSucceeded() {
        return failures.isEmpty();
    }

    public boolean allFailed() {
        return successes.isEmpty() && !failures.isEmpty();
    }
}
