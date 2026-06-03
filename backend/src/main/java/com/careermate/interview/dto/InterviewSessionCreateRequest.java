package com.careermate.interview.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class InterviewSessionCreateRequest {

    @Size(max = 128, message = "标题最长 128 字符")
    private String title;
}
