package com.careermate.jobmatch.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobMatchListItemResponse {

    private Long id;
    private Long resumeId;
    private String jobTitle;
    private String companyName;
    private Integer matchScore;
    private String matchLevel;
    private List<String> matchedSkills;
    private List<String> missingSkills;
    private String analysisSummary;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
