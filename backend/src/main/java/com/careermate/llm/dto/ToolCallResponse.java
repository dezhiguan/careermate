package com.careermate.llm.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCallResponse {

    private String content;
    private List<Object> toolCalls;
    private String model;
    private String provider;
    private Long latencyMs;
}
