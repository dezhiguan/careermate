package com.careermate.resume.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResumeCreateRequest {

    @NotBlank(message = "标题不能为空")
    @Size(max = 128, message = "标题最长 128 字符")
    private String title;

    @NotBlank(message = "简历内容不能为空")
    @Size(max = 50000, message = "简历内容最长 50000 字符")
    private String content;
}
