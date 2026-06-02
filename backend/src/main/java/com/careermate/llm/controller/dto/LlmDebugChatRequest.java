package com.careermate.llm.controller.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LlmDebugChatRequest {

    @NotBlank(message = "message 不能为空")
    private String message;
}
