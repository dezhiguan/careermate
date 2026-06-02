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
public class AgentMessageResponse {

    private Long id;
    private String role;
    private String content;
    private String messageType;
    private Integer sequenceNo;
    private OffsetDateTime createdAt;
}
