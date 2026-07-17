package com.careermate.profile.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CareerProfileResponse {

    private String targetRole;
    private String targetCity;
    private String seniority;
    private String workMode;
    private String targetSalaryRange;
    private List<String> skillKeywords;
    private String preferenceSummary;
    private String source;
}
