package com.careermate.market.dto;

import lombok.Data;

import java.util.List;

/**
 * 薪酬谈判建议 VO —— 围绕一条 JD，结合市场薪资分位与用户期望薪资给出谈判锚点。
 *
 * <p>纯规则计算，不经 LLM，无编造风险。市场数据缺失时 {@link #dataAvailable}=false 且明说。
 */
@Data
public class SalaryGuidanceVO {

    /** 市场分位（来自薪资行情库）。 */
    private String p25;
    private String p50;
    private String p75;
    private String p90;
    private String trend;

    /** 用户期望薪资（取自画像 targetSalaryRange 的上限；无则为空）。 */
    private String userExpectation;

    /** 期望落在的分位：P25以下 / P25-P50 / P50-P75 / P75以上 / 未知。 */
    private String quartile;

    /** 建议锚定的谈判价位。 */
    private String anchorPoint;

    /** 谈判建议文案。 */
    private String negotiationAdvice;

    /** 市场数据是否可用。 */
    private boolean dataAvailable;

    /** 来源引用。 */
    private List<MarketSourceCitationVO> citations;

    /** 来源摘要。 */
    private List<String> sourceSummaries;
}
