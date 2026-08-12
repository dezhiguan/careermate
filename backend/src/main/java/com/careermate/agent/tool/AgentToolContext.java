package com.careermate.agent.tool;

import com.careermate.agent.path.AgentPathMode;
import lombok.Builder;
import lombok.Value;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

@Value
@Builder
public class AgentToolContext {

    Long userId;
    String sessionId;
    String userMessage;
    @Builder.Default
    Map<String, Object> args = Collections.emptyMap();
    /** A2：本轮执行路径分层，供 deep-only 能力（带引用检索、反思）gate。默认 FAST。 */
    @Builder.Default
    AgentPathMode pathMode = AgentPathMode.FAST;

    /**
     * 本轮已由意图路由执行过的工具名。
     *
     * <p>写入类工具有两条互不知情的执行路径：意图路由先执行一次（走 trace 和 SSE 事件），
     * Spring AI 的 function-calling 又把同一批工具挂给模型，模型可能再调一次（不留痕）。
     * 结果是一次「帮我建个任务」落库两条。这里记下已执行的工具，让回调工厂把它们摘掉。
     */
    @Builder.Default
    Set<String> executedToolNames = Collections.emptySet();

    public boolean isDeep() {
        return pathMode == AgentPathMode.DEEP;
    }

    public boolean alreadyExecuted(String toolName) {
        return toolName != null && executedToolNames.contains(toolName);
    }
}
