package com.careermate.profile.dto;

import lombok.Data;

import java.util.List;

@Data
public class CareerProfileUpsertRequest {

    private String targetRole;
    private String targetCity;
    private String seniority;
    private String workMode;
    private List<String> skillKeywords;
    private String preferenceSummary;
}
