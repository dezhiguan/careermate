package com.careermate.keel;

import com.careermate.agent.dto.AgentMessageResponse;

import java.util.List;

/** Picks the reply Keel should show for one CareerMate turn. */
public final class KeelReplies {
    private KeelReplies() {}

    public static String lastAgent(List<AgentMessageResponse> messages) {
        String found = null;
        if (messages == null) {
            return null;
        }
        for (var message : messages) {
            if (message != null && "agent".equals(message.getRole()) && message.getContent() != null
                    && !message.getContent().isBlank()) {
                found = message.getContent();
            }
        }
        return found;
    }

    public static String finalEvent(String answer, String traceId, String runId) {
        return "event: final\ndata: {\"answer\":" + json(answer) + ",\"trace_id\":" + json(traceId)
                + ",\"run_id\":" + json(runId) + "}\n\n";
    }

    public static String errorEvent(String code, String message, String traceId, String runId) {
        return "event: error\ndata: {\"code\":" + json(code) + ",\"message\":" + json(message)
                + ",\"trace_id\":" + json(traceId) + ",\"run_id\":" + json(runId) + ",\"retryable\":false}\n\n";
    }

    static String json(String value) {
        var text = value == null ? "" : value;
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\"";
    }
}
