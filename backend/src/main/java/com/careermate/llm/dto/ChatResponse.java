package com.careermate.llm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    private String content;
    private String model;
    private String provider;
    private Integer inputTokens;
    private Integer outputTokens;
    private Long latencyMs;
    private String finishReason;
}
