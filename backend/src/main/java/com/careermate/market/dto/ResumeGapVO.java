package com.careermate.market.dto;

import com.careermate.common.api.CacheMeta;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

@Data
public class ResumeGapVO {

    private List<String> hasSkills;
    private List<String> missingSkills;
    private int matchScore;
    private String topSuggestion;
    private String aiSummary;
    private List<MarketSourceCitationVO> citations;
    private List<String> sourceSummaries;
    @JsonProperty("_meta")
    private CacheMeta meta;
}
