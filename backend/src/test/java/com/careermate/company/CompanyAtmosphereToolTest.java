package com.careermate.company;

import com.careermate.agent.tool.AgentToolContext;
import com.careermate.agent.tool.AgentToolDomain;
import com.careermate.agent.tool.AgentToolResult;
import com.careermate.agent.tool.CompanyAtmosphereTool;
import com.careermate.company.dto.CompanyAtmosphereVO;
import com.careermate.company.service.CompanyAtmosphereService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyAtmosphereToolTest {

    @Mock
    private CompanyAtmosphereService service;

    private CompanyAtmosphereTool tool;

    @BeforeEach
    void setUp() {
        tool = new CompanyAtmosphereTool(service);
    }

    @Test
    void metadataIsWellFormed() {
        assertEquals("get_company_atmosphere", tool.name());
        assertTrue(tool.description().contains("氛围"));
        assertEquals(AgentToolDomain.KNOWLEDGE, tool.definition().getDomain());
        assertEquals(1, tool.definition().getParameters().size());
        assertTrue(tool.supports(AgentToolContext.builder().userId(1L).build()));
    }

    @Test
    void executeReturnsSuccessWhenDataAvailable() {
        CompanyAtmosphereVO vo = new CompanyAtmosphereVO();
        vo.setCompanyName("字节跳动");
        vo.setDataAvailable(true);
        vo.setAiSummary("技术强");
        vo.setCultureTags(List.of(new CompanyAtmosphereVO.CultureTag("技术氛围强", "POSITIVE")));
        when(service.getCompanyAtmosphere(eq("字节跳动"))).thenReturn(vo);

        AgentToolResult result = tool.execute(AgentToolContext.builder()
                .userId(1L)
                .args(Map.of("company", "字节跳动"))
                .build());

        assertTrue(result.isSuccess());
        assertTrue(result.getSummary().contains("字节跳动"));
        assertEquals(true, result.getData().get("dataAvailable"));
        assertEquals("字节跳动", result.getData().get("companyName"));
    }

    @Test
    void executeReturnsSuccessButFlagsNoDataWhenUnavailable() {
        CompanyAtmosphereVO vo = new CompanyAtmosphereVO();
        vo.setCompanyName("某冷门公司");
        vo.setDataAvailable(false);
        when(service.getCompanyAtmosphere(eq("某冷门公司"))).thenReturn(vo);

        AgentToolResult result = tool.execute(AgentToolContext.builder()
                .args(Map.of("company", "某冷门公司"))
                .build());

        assertTrue(result.isSuccess());
        assertTrue(result.getSummary().contains("暂无"));
        assertEquals(false, result.getData().get("dataAvailable"));
    }

    @Test
    void executeFailsWhenCompanyMissing() {
        AgentToolResult blank = tool.execute(AgentToolContext.builder()
                .args(Map.of("company", "  "))
                .build());
        assertFalse(blank.isSuccess());

        AgentToolResult none = tool.execute(AgentToolContext.builder().build());
        assertFalse(none.isSuccess());
    }
}
