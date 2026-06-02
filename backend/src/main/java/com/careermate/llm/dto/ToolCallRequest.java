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
public class ToolCallRequest {

    private List<ChatMessage> messages;
    private List<Object> tools;
    private String model;
}
