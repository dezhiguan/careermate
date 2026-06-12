package com.careermate.interview.dto;

import java.util.List;
import lombok.Data;

@Data
public class CompanyPrepVO {

    private String companyName;
    private String interviewStyle;
    private List<String> techFocus;
    private List<String> commonQuestions;
    private String behaviorTips;
    private String aiSummary;
}
