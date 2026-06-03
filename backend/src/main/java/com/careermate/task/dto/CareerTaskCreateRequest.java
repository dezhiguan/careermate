package com.careermate.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CareerTaskCreateRequest {

    @NotBlank(message = "任务标题不能为空")
    @Size(max = 200, message = "任务标题不能超过 200 字")
    private String title;

    @Size(max = 1000, message = "任务描述不能超过 1000 字")
    private String description;

    @NotBlank(message = "任务分类不能为空")
    private String category;

    private String priority;

    private LocalDate dueDate;
}
