package com.careermate.market.dto;

import java.util.List;
import lombok.Data;

@Data
public class SkillTrendsVO {

    private List<SkillItem> skills;
    private String aiSummary;
    private List<MarketSourceCitationVO> citations;
    private List<String> sourceSummaries;

    @Data
    public static class SkillItem {

        private int rank;
        private String name;
        private String level;
        private String growth;
    }
}
