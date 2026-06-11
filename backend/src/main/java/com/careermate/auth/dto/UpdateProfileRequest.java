package com.careermate.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    @NotBlank(message = "displayName不能为空")
    @Size(min = 1, max = 64, message = "displayName长度需在1-64字符")
    private String displayName;

    private String avatarUrl;
}
