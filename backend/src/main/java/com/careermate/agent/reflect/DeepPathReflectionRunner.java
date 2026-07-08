package com.careermate.agent.reflect;

import com.careermate.llm.LlmClient;
import com.careermate.llm.dto.ChatMessage;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.dto.ChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * A3：把反思闭环接入 deep-path 的适配器。
 *
 * <p>deep-path 时以"生成草稿答案"为 act 跑 {@link ReflectiveAgentEngine}，再把最终 plan 的成功标准
 * 与最后一轮差距，转成一段"最终作答要求"注入 system prompt——使最终流式答案满足自检标准。
 * 仅暴露修订要求，不回显 reflection 原文。反思关闭或异常时返回空串，静默降级。
 */
@Slf4j
@Component
public class DeepPathReflectionRunner {

    private final ReflectiveAgentEngine engine;
    private final LlmClient llmClient;
    private final ReflectionProperties properties;

    public DeepPathReflectionRunner(ReflectiveAgentEngine engine, LlmClient llmClient,
                                    ReflectionProperties properties) {
        this.engine = engine;
        this.llmClient = llmClient;
        this.properties = properties;
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /**
     * 跑反思闭环，返回要注入 system prompt 的"最终作答要求"；未启用/异常返回空串。
     */
    public String refine(String systemPrompt, String userMessage) {
        if (!properties.isEnabled()) {
            return "";
        }
        try {
            String runId = UUID.randomUUID().toString();
            ReflectiveRunResult result = engine.run(runId, userMessage,
                    plan -> draftAct(systemPrompt, plan, userMessage));
            return buildRequirement(result);
        } catch (Exception e) {
            log.warn("deep-path 反思闭环失败，跳过（不影响对话）: {}", e.getMessage());
            return "";
        }
    }

    private String draftAct(String systemPrompt, AgentPlan plan, String userMessage) {
        ChatResponse resp = llmClient.chat(ChatRequest.builder()
                .messages(List.of(
                        ChatMessage.builder().role("system")
                                .content(systemPrompt + "\n\n本轮成功标准：" + plan.successCriteria()).build(),
                        ChatMessage.builder().role("user").content(userMessage).build()))
                .build());
        return resp == null ? "" : resp.getContent();
    }

    /** 由最终 plan 成功标准 + 最后一轮差距，构造注入 prompt 的最终作答要求。 */
    static String buildRequirement(ReflectiveRunResult result) {
        if (result == null || result.finalPlan() == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\n\n【最终作答要求·已自检】\n");
        List<String> criteria = result.finalPlan().successCriteria();
        if (criteria != null && !criteria.isEmpty()) {
            sb.append("必须满足：").append(String.join("；", criteria)).append('\n');
        }
        Reflection last = result.lastReflection();
        if (last != null && last.gaps() != null && !last.gaps().isEmpty() && !result.reachedConsensus()) {
            sb.append("尤其补齐以下尚未覆盖处：").append(String.join("；", last.gaps())).append('\n');
        }
        return sb.length() > "\n\n【最终作答要求·已自检】\n".length() ? sb.toString() : "";
    }
}
