package com.careermate.agent.tool;

import com.careermate.llm.LlmClient;
import com.careermate.llm.LlmProperties;
import com.careermate.llm.dto.ChatMessage;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.dto.ChatResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class AgentLlmIntentRecognizer {

    private static final Pattern JSON_BLOCK = Pattern.compile("\\{[\\s\\S]*\\}");

    private static final String SYSTEM_PROMPT_TEMPLATE = """
        你是 CareerMate 求职 Agent 的意图识别器。
        根据用户消息，判断需要调用哪个工具（最多 1 个），并提取必要参数。
        严格输出 JSON，不加任何解释，不加 markdown 围栏。

        可用工具：
        %s

        如果用户消息是普通对话、问答，不需要调用工具，toolName 输出 null。

        输出格式（严格）：
        {"toolName": "工具名 或 null", "args": {}}
        """;

    private final LlmClient llmClient;
    private final LlmProperties llmProperties;
    private final ObjectMapper objectMapper;
    private final AgentToolRouter fallbackRouter;
    private final AgentToolRegistry toolRegistry;

    public AgentLlmIntentRecognizer(
        LlmClient llmClient,
        LlmProperties llmProperties,
        ObjectMapper objectMapper,
        AgentToolRouter fallbackRouter,
        AgentToolRegistry toolRegistry
    ) {
        this.llmClient = llmClient;
        this.llmProperties = llmProperties;
        this.objectMapper = objectMapper;
        this.fallbackRouter = fallbackRouter;
        this.toolRegistry = toolRegistry;
    }

    /**
     * LLM 识别优先，任何失败（含 mock provider）回退到 fallbackRouter。
     */
    public Optional<AgentToolRouter.RoutedTool> route(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return Optional.empty();
        }
        if (isMockProvider()) {
            return fallbackRouter.route(userMessage);
        }
        try {
            return routeByLlm(userMessage);
        } catch (Exception e) {
            log.warn("LLM 意图识别失败，回退 regex: err={}", e.getMessage());
            return fallbackRouter.route(userMessage);
        }
    }

    private Optional<AgentToolRouter.RoutedTool> routeByLlm(String userMessage) throws Exception {
        String text = userMessage.length() > 1000
            ? userMessage.substring(0, 1000) + "..."
            : userMessage;

        ChatRequest request = ChatRequest.builder()
            .messages(List.of(
                ChatMessage.builder().role("system").content(buildSystemPrompt()).build(),
                ChatMessage.builder().role("user").content(text).build()
            ))
            .temperature(0.0)
            .build();
        ChatResponse response = llmClient.chat(request);

        if (response == null || response.getContent() == null || response.getContent().isBlank()) {
            return fallbackRouter.route(userMessage);
        }

        String raw = response.getContent().trim();
        Matcher m = JSON_BLOCK.matcher(raw);
        if (!m.find()) {
            log.warn("LLM intent 输出非 JSON，回退 regex: head={}", raw.substring(0, Math.min(80, raw.length())));
            return fallbackRouter.route(userMessage);
        }

        JsonNode node = objectMapper.readTree(m.group());
        String toolName = node.path("toolName").isNull() || node.path("toolName").isMissingNode()
            ? null
            : node.path("toolName").asText(null);

        if (toolName == null || toolName.equals("null") || toolName.isBlank()) {
            return Optional.empty();
        }

        if (!toolRegistry.knownToolNames().contains(toolName)) {
            log.warn("LLM intent 返回未知工具 {}，回退 regex", toolName);
            return fallbackRouter.route(userMessage);
        }

        Map<String, Object> args = new java.util.LinkedHashMap<>();
        JsonNode argsNode = node.path("args");
        if (argsNode.isObject()) {
            argsNode.fields().forEachRemaining(entry -> {
                JsonNode v = entry.getValue();
                if (!v.isNull()) {
                    args.put(entry.getKey(), v.asText());
                }
            });
        }
        if ("create_job_match".equals(toolName)) {
            args.put("jdContent", userMessage);
        }

        return Optional.of(new AgentToolRouter.RoutedTool(toolName, args));
    }

    private String buildSystemPrompt() {
        StringBuilder tools = new StringBuilder();
        for (AgentToolDefinition definition : toolRegistry.listDefinitions()) {
            tools.append("- ")
                    .append(definition.getName())
                    .append("：")
                    .append(definition.getDescription())
                    .append('\n');
        }
        return SYSTEM_PROMPT_TEMPLATE.formatted(tools.toString().trim());
    }

    private boolean isMockProvider() {
        String p = llmProperties.getProvider();
        return p == null || p.isBlank() || "mock".equalsIgnoreCase(p.trim());
    }
}
