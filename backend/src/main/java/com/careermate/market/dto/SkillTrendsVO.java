package com.careermate.market.dto;

import com.careermate.common.api.CacheMeta;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

@Data
public class SkillTrendsVO {

    private List<SkillItem> skills;
    private String aiSummary;
    private List<MarketSourceCitationVO> citations;
    private List<String> sourceSummaries;
    @JsonProperty("_meta")
    private CacheMeta meta;

    @Data
    public static class SkillItem {

        private int rank;
        private String name;
        private String level;
        private String growth;

        /**
         * 该技能在本次检索到的 JD 原文中的出现次数——由后端确定性统计，不经 LLM。
         * 为 null 表示无法统计（检索上下文为空）。
         */
        private Integer mentions;

        /**
         * 相对热度 0-100（{@code mentions / maxMentions * 100}），前端热度条宽度只认这个字段。
         * 为 null 表示热度不可信，前端不得渲染热度条。
         */
        private Integer heat;
    }
}
