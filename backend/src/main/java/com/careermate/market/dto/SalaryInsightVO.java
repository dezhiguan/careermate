package com.careermate.market.dto;

import com.careermate.common.api.CacheMeta;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

@Data
public class SalaryInsightVO {

    private String p25;
    private String p50;
    private String p75;
    private String p90;
    private String trend;
    private String aiSummary;
    private List<MarketSourceCitationVO> citations;
    private List<String> sourceSummaries;
    @JsonProperty("_meta")
    private CacheMeta meta;
}
