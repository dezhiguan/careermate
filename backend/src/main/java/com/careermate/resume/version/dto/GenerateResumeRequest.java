package com.careermate.resume.version.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GenerateResumeRequest {

    @NotBlank
    private String sessionId;

    private String jdId;
}
