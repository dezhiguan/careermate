package com.careermate.company.dto;

import com.careermate.market.dto.MarketSourceCitationVO;
import lombok.Data;

import java.util.List;

/**
 * 公司氛围 VO —— 围绕一条 JD 展开的「这家公司的氛围」能力产出。
 *
 * <p>区别于 {@code CompanyInsightVO}（公司基本信息：规模/阶段/技术栈），本 VO 聚焦
 * 工作强度、团队口碑、面试风格、加班信号等「氛围」维度，并按正/负/中性给出文化标签。
 *
 * <p>「有据才答、无据明说」：{@link #dataAvailable} 为 false 时表示知识库暂无该公司情报，
 * 不做臆测；来源均在 {@link #citations} / {@link #sourceSummaries} 标注。
 */
@Data
public class CompanyAtmosphereVO {

    /** 公司名。 */
    private String companyName;

    /** 工作强度概述（如「节奏快，历史有大小周传闻」）。 */
    private String workIntensity;

    /** 团队口碑概述。 */
    private String teamReputation;

    /** 面试风格概述（如「重系统设计与项目深挖」）。 */
    private String interviewStyle;

    /** 加班信号（回应用户「不接受长期 996」等底线偏好）。 */
    private String overtimeSignal;

    /** 文化标签（带情绪极性），供前端氛围卡片渲染。 */
    private List<CultureTag> cultureTags;

    /** 一句话氛围小结。 */
    private String aiSummary;

    /** 是否有据可查；false = 知识库暂无情报，前端应提示「暂无氛围情报」。 */
    private boolean dataAvailable;

    /** 来源引用（复用 market 的来源标注结构）。 */
    private List<MarketSourceCitationVO> citations;

    /** 来源摘要（"[COMPANY@source.md] 预览文本"）。 */
    private List<String> sourceSummaries;

    /** 文化标签：标签文案 + 情绪极性（POSITIVE / NEGATIVE / NEUTRAL）。 */
    @Data
    public static class CultureTag {
        private String label;
        private String sentiment;

        public CultureTag() {
        }

        public CultureTag(String label, String sentiment) {
            this.label = label;
            this.sentiment = sentiment;
        }
    }
}
