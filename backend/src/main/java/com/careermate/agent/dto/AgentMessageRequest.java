package com.careermate.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AgentMessageRequest {

    @NotBlank(message = "message 不能为空")
    @Size(min = 1, max = 2000, message = "message 长度必须在 1-2000")
    private String message;
}

