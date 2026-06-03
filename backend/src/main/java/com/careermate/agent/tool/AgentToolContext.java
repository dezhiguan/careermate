package com.careermate.agent.tool;

import lombok.Builder;
import lombok.Value;

import java.util.Collections;
import java.util.Map;

@Value
@Builder
public class AgentToolContext {

    Long userId;
    String sessionId;
    String userMessage;
    @Builder.Default
    Map<String, Object> args = Collections.emptyMap();
}
