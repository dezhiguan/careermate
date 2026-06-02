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
public class ChatRequest {

    private List<ChatMessage> messages;
    private String model;
    private Double temperature;
    private Integer maxTokens;
}
