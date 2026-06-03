package com.careermate.agent.tool;

import lombok.Builder;
import lombok.Value;

import java.util.Collections;
import java.util.Map;

@Value
@Builder
public class AgentToolResult {

    String toolName;
    boolean success;
    String summary;
    @Builder.Default
    Map<String, Object> data = Collections.emptyMap();
    String errorMessage;

    public static AgentToolResult success(String toolName, String summary, Map<String, Object> data) {
        return AgentToolResult.builder()
                .toolName(toolName)
                .success(true)
                .summary(summary)
                .data(data == null ? Collections.emptyMap() : data)
                .build();
    }

    public static AgentToolResult failure(String toolName, String summary, String errorMessage) {
        return AgentToolResult.builder()
                .toolName(toolName)
                .success(false)
                .summary(summary)
                .errorMessage(errorMessage)
                .build();
    }
}
