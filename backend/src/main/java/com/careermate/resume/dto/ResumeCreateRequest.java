package com.careermate.resume.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResumeCreateRequest {

    @NotBlank(message = "title不能为空")
    @Size(max = 128, message = "title最长128字符")
    private String title;

    @NotBlank(message = "content不能为空")
    @Size(max = 50000, message = "content最长50000字符")
    private String content;
}
