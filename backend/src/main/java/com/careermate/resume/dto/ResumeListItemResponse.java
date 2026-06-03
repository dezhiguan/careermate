package com.careermate.resume.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class ResumeListItemResponse {

    private Long id;
    private String title;
    private String sourceType;
    private Boolean isDefault;
    private String status;
    private String contentPreview;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
