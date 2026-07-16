package com.careermate.market;

import com.careermate.agent.tool.AgentToolContext;
import com.careermate.agent.tool.AgentToolDomain;
import com.careermate.agent.tool.AgentToolResult;
import com.careermate.agent.tool.SalaryGuidanceTool;
import com.careermate.market.dto.SalaryGuidanceVO;
import com.careermate.market.service.SalaryGuidanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalaryGuidanceToolTest {

    @Mock
    private SalaryGuidanceService service;

    private SalaryGuidanceTool tool;

    @BeforeEach
    void setUp() {
        tool = new SalaryGuidanceTool(service);
    }

    @Test
    void metadataIsWellFormed() {
        assertEquals("get_salary_guidance", tool.name());
        assertEquals(AgentToolDomain.KNOWLEDGE, tool.definition().getDomain());
        assertEquals(3, tool.definition().getParameters().size());
        assertTrue(tool.supports(AgentToolContext.builder().build()));
    }

    @Test
    void executeUsesDefaultsAndReturnsAdvice() {
        SalaryGuidanceVO vo = new SalaryGuidanceVO();
        vo.setDataAvailable(true);
        vo.setNegotiationAdvice("建议锚定 43K");
        vo.setQuartile("P50-P75");
        // 默认参数 Java后端/广州/3-5年
        when(service.getSalaryGuidance(any(), eq("Java后端"), eq("广州"), eq("3-5年"))).thenReturn(vo);

        AgentToolResult result = tool.execute(AgentToolContext.builder().userId(1L).build());

        assertTrue(result.isSuccess());
        assertTrue(result.getSummary().contains("43K"));
        assertEquals("P50-P75", result.getData().get("quartile"));
    }

    @Test
    void executePassesExplicitArgs() {
        SalaryGuidanceVO vo = new SalaryGuidanceVO();
        vo.setDataAvailable(false);
        when(service.getSalaryGuidance(any(), eq("前端"), eq("深圳"), eq("1-3年"))).thenReturn(vo);

        AgentToolResult result = tool.execute(AgentToolContext.builder()
                .args(Map.of("role", "前端", "city", "深圳", "years", "1-3年"))
                .build());

        assertTrue(result.isSuccess());
        assertTrue(result.getSummary().contains("暂无"));
        assertEquals(false, result.getData().get("dataAvailable"));
    }
}
