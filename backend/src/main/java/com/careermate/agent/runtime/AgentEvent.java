package com.careermate.agent.runtime;

import lombok.Builder;
import lombok.Value;

import java.util.Collections;
import java.util.Map;

@Value
@Builder
public class AgentEvent {

    String type;
    @Builder.Default
    Map<String, Object> payload = Collections.emptyMap();
    Long timestamp;

    public static AgentEvent of(String type, Map<String, Object> payload) {
        return AgentEvent.builder()
                .type(type)
                .payload(payload == null ? Collections.emptyMap() : payload)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
