package com.careermate.agent.tool.springai;

import com.careermate.agent.sse.SseEmitterService;
import com.careermate.agent.sse.SseEventType;
import com.careermate.agent.tool.AgentToolContext;
import com.careermate.agent.tool.AgentToolDefinition;
import com.careermate.agent.tool.AgentToolExecutionService;
import com.careermate.agent.tool.AgentToolRegistry;
import com.careermate.agent.tool.AgentToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class SpringAiToolCallbackFactory {

    /**
     * 工具名 → 前端卡片类型：这些工具（围绕 JD 的六项能力）执行后，除把结果喂回 LLM 外，
     * 额外向对话流 emit 一张专属卡片（走 UI_ACTION 通道，与 RESUME_GENERATED 一致）。
     */
    private static final Map<String, String> CARD_TYPES = Map.of(
            "get_company_atmosphere", "COMPANY_ATMOSPHERE",
            "generate_jd_aware_questions", "INTERVIEW_QUESTIONS",
            "get_salary_guidance", "SALARY_GUIDANCE",
            "propose_stage_confirmation", "STAGE_CONFIRM"
    );

    private final AgentToolRegistry registry;
    private final AgentToolExecutionService executionService;
    private final ObjectMapper objectMapper;
    private final SseEmitterService sseEmitterService;

    public SpringAiToolCallbackFactory(
            AgentToolRegistry registry,
            AgentToolExecutionService executionService,
            ObjectMapper objectMapper,
            SseEmitterService sseEmitterService
    ) {
        this.registry = registry;
        this.executionService = executionService;
        this.objectMapper = objectMapper;
        this.sseEmitterService = sseEmitterService;
    }

    public List<ToolCallback> createCallbacks(AgentToolContext baseContext) {
        if (baseContext == null || baseContext.getUserId() == null) {
            throw new IllegalArgumentException("AgentToolContext.userId is required for Spring AI tool callbacks");
        }
        return registry.listDefinitions().stream()
                .filter(AgentToolDefinition::isEnabled)
                .filter(definition -> isSupported(definition, baseContext))
                .map(definition -> toCallback(definition, baseContext))
                .toList();
    }

    private boolean isSupported(AgentToolDefinition definition, AgentToolContext baseContext) {
        return registry.findByName(definition.getName())
                .map(tool -> tool.supports(baseContext))
                .orElse(false);
    }

    private ToolCallback toCallback(AgentToolDefinition definition, AgentToolContext baseContext) {
        String inputSchema = AgentToolJsonSchemaConverter.toInputJsonSchema(definition, objectMapper);
        return FunctionToolCallback.builder(definition.getName(), (Map<String, Object> args, ToolContext toolContext) -> {
            Map<String, Object> parsed = args == null ? Map.of() : new LinkedHashMap<>(args);
            rejectIdentityArgs(parsed);
            AgentToolContext context = AgentToolContext.builder()
                    .userId(baseContext.getUserId())
                    .sessionId(baseContext.getSessionId())
                    .userMessage(baseContext.getUserMessage())
                    .args(parsed)
                    .build();
            AgentToolResult result = executionService.execute(context, definition.getName());
            emitRenderableCard(baseContext.getSessionId(), result);
            return toResultPayload(result);
        })
                .description(definition.getDescription())
                .inputSchema(inputSchema)
                .inputType(new ParameterizedTypeReference<Map<String, Object>>() {
                })
                .build();
    }

    /** 若工具结果可渲染成专属卡片，向该会话 emit 一张 UI_ACTION 卡片；失败/无数据/非卡片工具则跳过。 */
    private void emitRenderableCard(String sessionId, AgentToolResult result) {
        if (sessionId == null || sseEmitterService == null) {
            return;
        }
        Map<String, Object> card = buildRenderableCard(result);
        if (card == null) {
            return;
        }
        try {
            sseEmitterService.send(sessionId, SseEventType.UI_ACTION, Map.of("card", card));
        } catch (Exception e) {
            log.warn("emit tool card failed: tool={}, err={}",
                    result == null ? null : result.getToolName(), e.getMessage());
        }
    }

    /** 构造前端可渲染的卡片（type + 工具 data 平铺）；仅对已登记的卡片工具、成功且有数据时返回。 */
    static Map<String, Object> buildRenderableCard(AgentToolResult result) {
        if (result == null || !result.isSuccess()) {
            return null;
        }
        String type = CARD_TYPES.get(result.getToolName());
        if (type == null) {
            return null;
        }
        Map<String, Object> data = result.getData();
        if (data == null || data.isEmpty()) {
            return null;
        }
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("type", type);
        card.putAll(data);
        return card;
    }

    private Map<String, Object> toResultPayload(AgentToolResult result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("toolName", result.getToolName());
        payload.put("success", result.isSuccess());
        payload.put("summary", result.getSummary());
        payload.put("data", result.getData() == null ? Map.of() : result.getData());
        payload.put("errorMessage", result.getErrorMessage());
        return payload;
    }

    private void rejectIdentityArgs(Map<String, Object> args) {
        if (args.containsKey("userId") || args.containsKey("sessionId")) {
            throw new IllegalArgumentException("Tool callback must not accept userId or sessionId arguments");
        }
    }
}
