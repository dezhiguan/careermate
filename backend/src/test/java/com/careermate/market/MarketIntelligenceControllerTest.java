package com.careermate.market;

import com.careermate.market.dto.CompanyInsightVO;
import com.careermate.market.dto.ResumeGapVO;
import com.careermate.market.dto.SalaryInsightVO;
import com.careermate.market.dto.SkillTrendsVO;
import com.careermate.security.CurrentUser;
import com.careermate.security.CurrentUserContext;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
class MarketIntelligenceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MarketIntelligenceService marketIntelligenceService;

    @BeforeEach
    void setUpUser() {
        CurrentUserContext.set(CurrentUser.builder()
                .userId(1L)
                .username("local-user")
                .role("user")
                .authenticated(true)
                .build());
    }

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    @Test
    void salaryInsightEndpointReturnsVo() throws Exception {
        SalaryInsightVO vo = new SalaryInsightVO();
        vo.setP25("20K");
        vo.setP50("28K");
        vo.setP75("32K");
        vo.setP90("40K");
        vo.setTrend("稳定");
        vo.setAiSummary("薪资平稳");
        when(marketIntelligenceService.getSalaryInsight("Java后端", "广州", "3-5年")).thenReturn(vo);

        mockMvc.perform(get("/api/market/salary-insight"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.p50").value("28K"))
                .andExpect(jsonPath("$.data.aiSummary").value("薪资平稳"));
    }

    @Test
    void skillTrendsEndpointReturnsVo() throws Exception {
        SkillTrendsVO vo = new SkillTrendsVO();
        SkillTrendsVO.SkillItem item = new SkillTrendsVO.SkillItem();
        item.setRank(1);
        item.setName("Java");
        item.setLevel("高频");
        item.setGrowth("稳定");
        vo.setSkills(List.of(item));
        vo.setAiSummary("Java 需求高");
        when(marketIntelligenceService.getSkillTrends("Java后端")).thenReturn(vo);

        mockMvc.perform(get("/api/market/skill-trends"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.skills[0].name").value("Java"));
    }

    @Test
    void resumeGapEndpointReturnsVo() throws Exception {
        ResumeGapVO vo = new ResumeGapVO();
        vo.setHasSkills(List.of("Java"));
        vo.setMissingSkills(List.of("K8s"));
        vo.setMatchScore(70);
        vo.setTopSuggestion("补 K8s");
        vo.setAiSummary("匹配一般");
        when(marketIntelligenceService.getResumeGap(1L)).thenReturn(vo);

        mockMvc.perform(get("/api/market/resume-gap"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.matchScore").value(70));
    }

    @Test
    void companyInsightEndpointReturnsVo() throws Exception {
        CompanyInsightVO vo = new CompanyInsightVO();
        vo.setCompanyName("腾讯");
        vo.setScale("大厂");
        vo.setStage("上市");
        vo.setTechStack(List.of("Java"));
        vo.setCurrentJds(List.of("Java后端"));
        vo.setAiSummary("互联网大厂");
        when(marketIntelligenceService.getCompanyInsight("腾讯")).thenReturn(vo);

        mockMvc.perform(get("/api/market/company-insight").param("company", "腾讯"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.companyName").value("腾讯"))
                .andExpect(jsonPath("$.data.currentJds[0]").value("Java后端"));
    }
}
