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
public class JobMatchDetailResponse {

    private Long id;
    private Long resumeId;
    private String jobTitle;
    private String companyName;
    private String jdContent;
    private Integer matchScore;
    private String matchLevel;
    private List<String> matchedSkills;
    private List<String> missingSkills;
    private List<String> strengths;
    private List<String> risks;
    private List<String> suggestions;
    private String analysisSummary;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
