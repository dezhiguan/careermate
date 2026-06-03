package com.careermate.jobmatch;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobMatchContext {

    private boolean available;
    private Long jobMatchId;
    private Long resumeId;
    private String jobTitle;
    private String companyName;
    private Integer matchScore;
    private String matchLevel;
    private List<String> matchedSkills;
    private List<String> missingSkills;
    private List<String> strengths;
    private List<String> risks;
    private List<String> suggestions;
    private String analysisSummary;
    private String contextText;
}
