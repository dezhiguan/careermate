package com.careermate.agent.runtime;

import com.careermate.agent.react.ReActTrace;
import com.careermate.agent.tool.AgentToolResult;
import com.careermate.llm.dto.ChatRequest;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Value
@Builder
public class AgentRunResult {

    String systemPrompt;
    ChatRequest chatRequest;
    @Singular("event")
    List<AgentEvent> events;
    @Singular("toolResult")
    List<AgentToolResult> toolResults;
    ReActTrace reactTrace;
    @Builder.Default
    Map<String, Object> debugMetadata = Collections.emptyMap();
}
