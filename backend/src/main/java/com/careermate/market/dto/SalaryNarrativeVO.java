package com.careermate.market.dto;

/**
 * 分位由样本算出时，LLM 只补这两段文案。
 *
 * @param trend     市场趋势
 * @param aiSummary 结论文案
 */
public record SalaryNarrativeVO(
        String trend,
        String aiSummary
) {
}
