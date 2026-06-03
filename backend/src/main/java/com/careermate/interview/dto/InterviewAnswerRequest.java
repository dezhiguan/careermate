package com.careermate.interview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class InterviewAnswerRequest {

    @NotBlank(message = "回答内容不能为空")
    @Size(max = 10000, message = "回答内容最长 10000 字符")
    private String answerText;
}
