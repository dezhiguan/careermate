package com.careermate.task.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class CareerTaskResponse {

    private Long id;
    private String title;
    private String description;
    private String category;
    private String priority;
    private String status;
    private LocalDate dueDate;
    private String source;
    private String relatedType;
    private Long relatedId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
}
