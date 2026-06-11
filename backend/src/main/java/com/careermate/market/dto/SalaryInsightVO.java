package com.careermate.market.dto;

import lombok.Data;

@Data
public class SalaryInsightVO {

    private String p25;
    private String p50;
    private String p75;
    private String p90;
    private String trend;
    private String aiSummary;
}
