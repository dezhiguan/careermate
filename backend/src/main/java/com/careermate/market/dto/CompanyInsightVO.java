package com.careermate.market.dto;

import java.util.List;
import lombok.Data;

@Data
public class CompanyInsightVO {

    private String companyName;
    private String scale;
    private String stage;
    private List<String> techStack;
    private List<String> currentJds;
    private String aiSummary;
}
