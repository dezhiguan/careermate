package com.careermate.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeStatsResponse {

    private int totalResumes;
    private boolean hasDefaultResume;
    private String defaultResumeTitle;
    private OffsetDateTime latestUpdatedAt;
}
