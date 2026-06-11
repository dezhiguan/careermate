package com.careermate.market.dto;

import java.util.List;
import lombok.Data;

@Data
public class ResumeGapVO {

    private List<String> hasSkills;
    private List<String> missingSkills;
    private int matchScore;
    private String topSuggestion;
    private String aiSummary;
}
