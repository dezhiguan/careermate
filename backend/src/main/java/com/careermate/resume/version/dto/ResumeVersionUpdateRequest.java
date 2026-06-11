package com.careermate.resume.version.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResumeVersionUpdateRequest {

    @NotBlank(message = "versionName不能为空")
    @Size(max = 128, message = "versionName最长128字符")
    private String versionName;

    @NotBlank(message = "contentMarkdown不能为空")
    @Size(max = 50000, message = "contentMarkdown最长50000字符")
    private String contentMarkdown;
}
