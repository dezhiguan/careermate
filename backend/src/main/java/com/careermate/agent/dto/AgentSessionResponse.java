package com.careermate.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentSessionResponse {

    private String sessionId;
    private String status;
    private String title;
    private String intent;
    private String taskType;
    private Long totalLatencyMs;
    private List<AgentMessageResponse> messages;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
