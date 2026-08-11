package com.careermate.interview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 重命名训练记录。列表里同名记录堆积时用户需要自己整理的手段。 */
@Data
public class InterviewSessionRenameRequest {

    @NotBlank(message = "标题不能为空")
    @Size(max = 60, message = "标题最多 60 个字")
    private String title;
}
