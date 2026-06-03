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
public class AgentSessionListItemResponse {

    private String sessionId;
    private String title;
    /** CREATED | RUNNING | COMPLETED | ERROR */
    private String status;
    private Integer messageCount;
    private String lastMessagePreview;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
