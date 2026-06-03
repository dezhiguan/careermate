package com.careermate.task.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class DashboardTaskItemResponse {

    private Long id;
    private String title;
    private String category;
    private String priority;
    private String status;
    private LocalDate dueDate;
}
