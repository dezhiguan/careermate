package com.careermate.observability;

import com.careermate.agent.session.AgentSessionService;
import com.careermate.llm.dto.ChatResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class LlmChatTraceRecorder {

    public static final String TOOL_NAME_LLM_CHAT = "llm_chat";

    private final AgentSessionService agentSessionService;
    private final ObjectMapper objectMapper;

    public LlmChatTraceRecorder(AgentSessionService agentSessionService, ObjectMapper objectMapper) {
        this.agentSessionService = agentSessionService;
        this.objectMapper = objectMapper;
    }

    public void record(ChatResponse response, boolean success, long latencyMs, String errorCode) {
        String sessionId = MDC.get(MdcKeys.SESSION_ID);
        String userIdRaw = MDC.get(MdcKeys.USER_ID);
        if (!StringUtils.hasText(sessionId) || !StringUtils.hasText(userIdRaw)) {
            return;
        }
        Long userId;
        try {
            userId = Long.parseLong(userIdRaw.trim());
        } catch (NumberFormatException e) {
            return;
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        if (response != null) {
            summary.put("provider", response.getProvider());
            summary.put("model", response.getModel());
            summary.put("inputTokens", response.getInputTokens());
            summary.put("outputTokens", response.getOutputTokens());
            summary.put("finishReason", response.getFinishReason());
        }
        String responseSummary = writeJson(summary);
        agentSessionService.recordTrace(
                userId,
                sessionId,
                TOOL_NAME_LLM_CHAT,
                "{}",
                responseSummary,
                success ? "SUCCESS" : "FAILED",
                latencyMs,
                errorCode
        );
    }

    private String writeJson(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            return "{}";
        }
    }
}
