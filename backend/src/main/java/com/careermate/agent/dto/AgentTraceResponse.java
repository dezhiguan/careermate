package com.careermate.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTraceResponse {

    private Long id;
    private String type;
    private String toolName;
    private String status;
    private String requestSummary;
    private String responseSummary;
    private Long latencyMs;
    private Boolean fallbackUsed;
    private String errorCode;
    private OffsetDateTime createdAt;
}
