package com.careermate.agent.tool.springai;

import com.careermate.agent.tool.AgentToolContext;
import com.careermate.agent.tool.AgentToolDefinition;
import com.careermate.agent.tool.AgentToolExecutionService;
import com.careermate.agent.tool.AgentToolRegistry;
import com.careermate.agent.tool.AgentToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class SpringAiToolCallbackFactory {

    private final AgentToolRegistry registry;
    private final AgentToolExecutionService executionService;
    private final ObjectMapper objectMapper;

    public SpringAiToolCallbackFactory(
            AgentToolRegistry registry,
            AgentToolExecutionService executionService,
            ObjectMapper objectMapper
    ) {
        this.registry = registry;
        this.executionService = executionService;
        this.objectMapper = objectMapper;
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
            return toResultPayload(result);
        })
                .description(definition.getDescription())
                .inputSchema(inputSchema)
                .inputType(new ParameterizedTypeReference<Map<String, Object>>() {
                })
                .build();
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
