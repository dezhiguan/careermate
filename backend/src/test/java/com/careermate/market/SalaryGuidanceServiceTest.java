package com.careermate.market;

import com.careermate.market.dto.SalaryGuidanceVO;
import com.careermate.market.dto.SalaryInsightVO;
import com.careermate.market.service.MarketIntelligenceService;
import com.careermate.market.service.SalaryGuidanceService;
import com.careermate.model.entity.CareerProfileEntity;
import com.careermate.profile.service.CareerProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalaryGuidanceServiceTest {

    @Mock
    private MarketIntelligenceService marketIntelligenceService;
    @Mock
    private CareerProfileService careerProfileService;

    private SalaryGuidanceService service;

    @BeforeEach
    void setUp() {
        service = new SalaryGuidanceService(marketIntelligenceService, careerProfileService);
    }

    @Test
    void salaryUnavailableReturnsFallback() {
        SalaryInsightVO salary = salaryVo("暂无数据", "暂无数据", "暂无数据");
        when(marketIntelligenceService.getSalaryInsight(any(), any(), any())).thenReturn(salary);

        SalaryGuidanceVO g = service.getSalaryGuidance(1L, "Java后端", "广州", "3-5年");

        assertFalse(g.isDataAvailable());
        assertTrue(g.getNegotiationAdvice().contains("暂时无法"));
    }

    @Test
    void nullSalaryReturnsFallback() {
        when(marketIntelligenceService.getSalaryInsight(any(), any(), any())).thenReturn(null);
        SalaryGuidanceVO g = service.getSalaryGuidance(1L, "r", "c", "y");
        assertFalse(g.isDataAvailable());
    }

    @Test
    void expectationInMidUpperGivesUpwardAnchor() {
        when(marketIntelligenceService.getSalaryInsight(any(), any(), any()))
                .thenReturn(salaryVo("22K", "30K", "42K"));
        when(careerProfileService.findEntityByUserId(anyLong())).thenReturn(profile("30K-40K"));

        SalaryGuidanceVO g = service.getSalaryGuidance(1L, "Java后端", "广州", "3-5年");

        assertTrue(g.isDataAvailable());
        assertEquals("40K", g.getUserExpectation());
        assertEquals("P50-P75", g.getQuartile());
        assertEquals("43K", g.getAnchorPoint());
        assertTrue(g.getNegotiationAdvice().contains("P50-P75"));
    }

    @Test
    void expectationBelowP25AnchorsToMedian() {
        when(marketIntelligenceService.getSalaryInsight(any(), any(), any()))
                .thenReturn(salaryVo("22K", "30K", "42K"));
        when(careerProfileService.findEntityByUserId(anyLong())).thenReturn(profile("18K"));

        SalaryGuidanceVO g = service.getSalaryGuidance(1L, "r", "c", "y");

        assertEquals("P25以下", g.getQuartile());
        assertEquals("30K", g.getAnchorPoint());
        assertTrue(g.getNegotiationAdvice().contains("低于市场 P25"));
    }

    @Test
    void expectationAboveP75AnchorsToP75() {
        when(marketIntelligenceService.getSalaryInsight(any(), any(), any()))
                .thenReturn(salaryVo("22K", "30K", "42K"));
        when(careerProfileService.findEntityByUserId(anyLong())).thenReturn(profile("60K"));

        SalaryGuidanceVO g = service.getSalaryGuidance(1L, "r", "c", "y");

        assertEquals("P75以上", g.getQuartile());
        assertEquals("42K", g.getAnchorPoint());
        assertTrue(g.getNegotiationAdvice().contains("高于 P75"));
    }

    @Test
    void noProfileUsesMedianAnchor() {
        when(marketIntelligenceService.getSalaryInsight(any(), any(), any()))
                .thenReturn(salaryVo("22K", "30K", "42K"));
        when(careerProfileService.findEntityByUserId(anyLong())).thenReturn(null);

        SalaryGuidanceVO g = service.getSalaryGuidance(1L, "r", "c", "y");

        assertTrue(g.isDataAvailable());
        assertEquals("暂无", g.getUserExpectation());
        assertEquals("未知", g.getQuartile());
        assertEquals("30K", g.getAnchorPoint());
        assertTrue(g.getNegotiationAdvice().contains("尚未设置"));
    }

    @Test
    void nullUserIdSkipsProfileLookup() {
        when(marketIntelligenceService.getSalaryInsight(any(), any(), any()))
                .thenReturn(salaryVo("22K", "30K", "42K"));

        SalaryGuidanceVO g = service.getSalaryGuidance(null, "r", "c", "y");

        assertTrue(g.isDataAvailable());
        assertEquals("暂无", g.getUserExpectation());
    }

    @Test
    void profileLookupExceptionIsSwallowed() {
        when(marketIntelligenceService.getSalaryInsight(any(), any(), any()))
                .thenReturn(salaryVo("22K", "30K", "42K"));
        when(careerProfileService.findEntityByUserId(anyLong()))
                .thenThrow(new RuntimeException("db down"));

        SalaryGuidanceVO g = service.getSalaryGuidance(1L, "r", "c", "y");

        assertTrue(g.isDataAvailable());
        assertEquals("暂无", g.getUserExpectation());
    }

    @Test
    void salaryServiceExceptionReturnsFallback() {
        when(marketIntelligenceService.getSalaryInsight(any(), any(), any()))
                .thenThrow(new RuntimeException("boom"));

        SalaryGuidanceVO g = service.getSalaryGuidance(1L, "r", "c", "y");
        assertFalse(g.isDataAvailable());
    }

    private static SalaryInsightVO salaryVo(String p25, String p50, String p75) {
        SalaryInsightVO vo = new SalaryInsightVO();
        vo.setP25(p25);
        vo.setP50(p50);
        vo.setP75(p75);
        vo.setP90("55K");
        vo.setTrend("稳定");
        return vo;
    }

    private static CareerProfileEntity profile(String range) {
        CareerProfileEntity e = new CareerProfileEntity();
        e.setTargetSalaryRange(range);
        return e;
    }
}
