package com.careermate.pipeline.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 单条投递机会（看板卡片）。
 */
@Data
public class ApplicationVO {
    private Long id;
    private Long jdDocId;
    private String company;
    private String roleTitle;
    private String stage;
    private String stageLabel;
    private String resumeVersionId;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastActiveAt;
}
