package com.careermate.agent.runtime;

import lombok.Builder;
import lombok.Value;

import java.util.Collections;
import java.util.Map;

@Value
@Builder
public class AgentRunRequest {

    Long userId;
    String sessionId;
    String userMessage;
    @Builder.Default
    boolean streamEnabled = true;
    @Builder.Default
    Map<String, Object> attributes = Collections.emptyMap();
}
