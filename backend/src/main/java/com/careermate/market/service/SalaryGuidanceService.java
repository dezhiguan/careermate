package com.careermate.market.service;

import com.careermate.market.dto.SalaryGuidanceVO;
import com.careermate.market.dto.SalaryInsightVO;
import com.careermate.market.support.SalaryParseSupport;
import com.careermate.model.entity.CareerProfileEntity;
import com.careermate.profile.service.CareerProfileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * 薪酬谈判建议服务 —— 复用市场薪资分位（{@link MarketIntelligenceService}）+ 用户期望薪资（画像），
 * 用纯规则计算谈判锚点与建议文案。不经 LLM，无编造风险，全程降级不抛异常。
 */
@Slf4j
@Service
public class SalaryGuidanceService {

    private static final String NO_DATA = "暂无数据";
    private static final String FALLBACK_ADVICE = "暂时无法获取市场薪资数据，请稍后再试。";

    private final MarketIntelligenceService marketIntelligenceService;
    private final CareerProfileService careerProfileService;

    public SalaryGuidanceService(
            MarketIntelligenceService marketIntelligenceService,
            CareerProfileService careerProfileService
    ) {
        this.marketIntelligenceService = marketIntelligenceService;
        this.careerProfileService = careerProfileService;
    }

    /**
     * 生成薪酬谈判建议。
     *
     * @param userId 用户 ID（取期望薪资；可为 null）
     * @param role   岗位
     * @param city   城市
     * @param years  经验年限
     */
    public SalaryGuidanceVO getSalaryGuidance(Long userId, String role, String city, String years) {
        try {
            SalaryInsightVO salary = marketIntelligenceService.getSalaryInsight(role, city, years);
            if (salary == null || SalaryParseSupport.parseToYuan(salary.getP50()) < 0) {
                return fallback();
            }
            String expectation = resolveExpectation(userId);
            return compute(salary, expectation);
        } catch (Exception e) {
            log.warn("getSalaryGuidance failed: userId={}, err={}", userId, e.getMessage());
            return fallback();
        }
    }

    private String resolveExpectation(Long userId) {
        try {
            if (userId == null) {
                return null;
            }
            CareerProfileEntity profile = careerProfileService.findEntityByUserId(userId);
            if (profile == null || profile.getTargetSalaryRange() == null || profile.getTargetSalaryRange().isBlank()) {
                return null;
            }
            return SalaryParseSupport.extractUpperBound(profile.getTargetSalaryRange());
        } catch (Exception e) {
            log.warn("resolveExpectation failed: userId={}, err={}", userId, e.getMessage());
            return null;
        }
    }

    private SalaryGuidanceVO compute(SalaryInsightVO salary, String expectation) {
        SalaryGuidanceVO vo = new SalaryGuidanceVO();
        vo.setP25(salary.getP25());
        vo.setP50(salary.getP50());
        vo.setP75(salary.getP75());
        vo.setP90(salary.getP90());
        vo.setTrend(salary.getTrend());
        vo.setCitations(salary.getCitations());
        vo.setSourceSummaries(salary.getSourceSummaries());
        vo.setDataAvailable(true);

        int exp = SalaryParseSupport.parseToYuan(expectation);
        if (exp < 0) {
            // 无期望：以市场中位为锚
            vo.setUserExpectation("暂无");
            vo.setQuartile("未知");
            vo.setAnchorPoint(salary.getP50());
            vo.setNegotiationAdvice("你尚未设置期望薪资，建议先参考市场中位 " + salary.getP50()
                    + "（P50），并结合自身竞争力上下浮动。");
            return vo;
        }

        vo.setUserExpectation(expectation);
        String quartile = SalaryParseSupport.quartileOf(
                expectation, salary.getP25(), salary.getP50(), salary.getP75());
        vo.setQuartile(quartile);
        String anchor = anchorFor(exp, quartile, salary);
        vo.setAnchorPoint(anchor);
        vo.setNegotiationAdvice(adviceFor(expectation, quartile, anchor));
        return vo;
    }

    private static String anchorFor(int exp, String quartile, SalaryInsightVO salary) {
        int p50 = SalaryParseSupport.parseToYuan(salary.getP50());
        int p75 = SalaryParseSupport.parseToYuan(salary.getP75());
        if (p50 < 0 || p75 < 0) {
            return salary.getP50();
        }
        return switch (quartile) {
            case "P75以上" -> salary.getP75();                 // 期望偏高，锚 P75 更现实
            case "P25以下", "P25-P50" -> salary.getP50();      // 期望偏低，抬到市场中位
            default -> SalaryParseSupport.formatYuanToK((int) (exp * 1.08)); // 中位偏上，小幅上探
        };
    }

    private static String adviceFor(String expectation, String quartile, String anchor) {
        return switch (quartile) {
            case "P25以下" -> "你的期望 " + expectation + " 低于市场 P25，明显偏保守，建议提升到 " + anchor + " 附近。";
            case "P25-P50" -> "你的期望 " + expectation + " 处于 P25-P50，建议锚定 " + anchor + "，向市场中位靠拢。";
            case "P50-P75" -> "你的期望 " + expectation + " 处于市场中位偏上，可锚定 " + anchor + "（P50-P75 区间）从容谈判。";
            case "P75以上" -> "你的期望 " + expectation + " 高于 P75，谈判时需重点体现核心价值，建议以 " + anchor + " 为现实锚点。";
            default -> "暂无法判断你的期望所处分位，建议参考市场行情后再定锚点。";
        };
    }

    private static SalaryGuidanceVO fallback() {
        SalaryGuidanceVO vo = new SalaryGuidanceVO();
        vo.setP25(NO_DATA);
        vo.setP50(NO_DATA);
        vo.setP75(NO_DATA);
        vo.setP90(NO_DATA);
        vo.setTrend(NO_DATA);
        vo.setUserExpectation("暂无");
        vo.setQuartile("未知");
        vo.setAnchorPoint(NO_DATA);
        vo.setNegotiationAdvice(FALLBACK_ADVICE);
        vo.setDataAvailable(false);
        vo.setCitations(Collections.emptyList());
        vo.setSourceSummaries(Collections.emptyList());
        return vo;
    }
}
