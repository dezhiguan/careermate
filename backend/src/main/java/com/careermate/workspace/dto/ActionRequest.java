package com.careermate.workspace.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ActionRequest {

    @NotBlank
    private String action;

    private String payload;
}
