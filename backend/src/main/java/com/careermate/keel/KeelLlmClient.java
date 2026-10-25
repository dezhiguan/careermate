package com.careermate.keel;

import com.careermate.llm.LlmClient;
import com.careermate.llm.StreamCallback;
import com.careermate.llm.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** All native CareerMate model calls execute through the real SDK ctx.llm. */
public class KeelLlmClient implements LlmClient {
    private final KeelRuntimeClient runtime;
    private final ObjectMapper json;

    public KeelLlmClient(KeelRuntimeClient runtime, ObjectMapper json) {
        this.runtime = runtime;
        this.json = json;
    }

    private Map<String, Object> body(ChatRequest request) {
        var body = new LinkedHashMap<String, Object>();
        body.put("messages", request.getMessages().stream().map(m -> Map.of(
                "role", m.getRole() == null ? "user" : "agent".equals(m.getRole()) ? "assistant" : m.getRole(),
                "content", m.getContent() == null ? "" : m.getContent())).toList());
        body.put("model", request.getModel());
        body.put("maxTokens", request.getMaxTokens());
        body.put("temperature", request.getTemperature());
        var prompt = KeelPromptEvidence.find(request.getMessages());
        if (prompt != null) {
            body.put("promptName", prompt.name());
            body.put("promptVersion", prompt.version());
        }
        return body;
    }

    @Override public ChatResponse chat(ChatRequest request) {
        return response(runtime.post("llm", body(request)));
    }

    @Override public void streamChat(ChatRequest request, StreamCallback callback) {
        var body = body(request);
        body.put("stream", true);
        try {
            runtime.stream(body, event -> {
                if (event.has("token")) callback.onToken(event.get("token").asText());
                if (event.has("final")) callback.onComplete(response(event.get("final")));
            });
        } catch (RuntimeException e) {
            callback.onError(e);
        }
    }

    @Override public ToolCallResponse toolCall(ToolCallRequest request) {
        var body = body(ChatRequest.builder().messages(request.getMessages()).model(request.getModel()).build());
        body.put("tools", request.getTools());
        var result = runtime.post("llm", body);
        return ToolCallResponse.builder().content(result.path("content").asText())
                .model(result.path("model").asText()).provider("keel")
                .toolCalls(json.convertValue(result.path("toolCalls"), List.class)).build();
    }

    private ChatResponse response(JsonNode result) {
        return ChatResponse.builder().content(result.path("content").asText())
                .model(result.path("model").asText()).provider("keel")
                .inputTokens(result.hasNonNull("inputTokens") ? result.get("inputTokens").asInt() : null)
                .outputTokens(result.hasNonNull("outputTokens") ? result.get("outputTokens").asInt() : null)
                .finishReason("stop").build();
    }
}
