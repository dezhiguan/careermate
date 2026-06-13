package com.careermate.market;

import com.careermate.llm.LlmClient;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.dto.ChatResponse;
import com.careermate.market.dto.CompanyInsightVO;
import com.careermate.market.dto.ResumeGapVO;
import com.careermate.market.dto.SalaryInsightVO;
import com.careermate.market.dto.SkillTrendsVO;
import com.careermate.market.service.MarketIntelligenceService;
import com.careermate.ragforge.RagForgeChunk;
import com.careermate.ragforge.RagForgeClient;
import com.careermate.resume.ResumeContext;
import com.careermate.resume.ResumeContextProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketIntelligenceServiceTest {

    private static final String FALLBACK_SUMMARY = "暂时无法获取市场数据，请稍后再试";
    private static final String NO_DATA = "暂无数据";

    @Mock
    private RagForgeClient ragForgeClient;
    @Mock
    private LlmClient llmClient;
    @Mock
    private ResumeContextProvider resumeContextProvider;

    private MarketIntelligenceService service;

    @BeforeEach
    void setUp() {
        service = new MarketIntelligenceService(
                ragForgeClient,
                llmClient,
                resumeContextProvider,
                new ObjectMapper()
        );
    }

    @Test
    void getSalaryInsightReturnsFallbackWhenRagEmpty() {
        when(ragForgeClient.searchJd(any(), eq(30))).thenReturn(List.of());

        SalaryInsightVO result = service.getSalaryInsight("Java后端", "广州", "3-5年");

        assertEquals(NO_DATA, result.getP25());
        assertEquals(NO_DATA, result.getP50());
        assertEquals(FALLBACK_SUMMARY, result.getAiSummary());
        verify(llmClient, never()).chat(any());
    }

    @Test
    void getSalaryInsightParsesValidLlmJson() {
        when(ragForgeClient.searchJd(contains("薪资"), eq(30))).thenReturn(sampleChunks("月薪 25K-35K"));
        when(llmClient.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder().content("""
                {"p25":"22K","p50":"28K","p75":"32K","p90":"38K","trend":"上涨","aiSummary":"薪资稳步上涨"}
                """).build());

        SalaryInsightVO result = service.getSalaryInsight("Java后端", "广州", "3-5年");

        assertEquals("28K", result.getP50());
        assertEquals("上涨", result.getTrend());
        assertEquals("薪资稳步上涨", result.getAiSummary());
    }

    @Test
    void getSalaryInsightReturnsFallbackWhenLlmReturnsNonJson() {
        when(ragForgeClient.searchJd(any(), eq(30))).thenReturn(sampleChunks("JD"));
        when(llmClient.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().content("无法分析").build());

        SalaryInsightVO result = service.getSalaryInsight("Java后端", "广州", "3-5年");

        assertEquals(FALLBACK_SUMMARY, result.getAiSummary());
        assertEquals(NO_DATA, result.getP75());
    }

    @Test
    void getSkillTrendsParsesValidLlmJson() {
        when(ragForgeClient.searchJd(contains("技能要求"), eq(40))).thenReturn(sampleChunks("Spring Boot Redis"));
        when(llmClient.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder().content("""
                {"skills":[{"rank":1,"name":"Java","level":"高频","growth":"稳定"}],"aiSummary":"Java 仍是核心"}
                """).build());

        SkillTrendsVO result = service.getSkillTrends("Java后端");

        assertNotNull(result.getSkills());
        assertEquals(1, result.getSkills().size());
        assertEquals("Java", result.getSkills().get(0).getName());
        assertEquals("Java 仍是核心", result.getAiSummary());
    }

    @Test
    void getSkillTrendsReturnsFallbackWhenLlmThrows() {
        when(ragForgeClient.searchJd(any(), eq(40))).thenReturn(sampleChunks("JD"));
        when(llmClient.chat(any(ChatRequest.class))).thenThrow(new RuntimeException("timeout"));

        SkillTrendsVO result = service.getSkillTrends("Java后端");

        assertTrue(result.getSkills().isEmpty());
        assertEquals(FALLBACK_SUMMARY, result.getAiSummary());
    }

    @Test
    void getResumeGapReturnsEmptyWhenNoResume() {
        when(resumeContextProvider.getResumeContext(1L)).thenReturn(
                ResumeContext.builder().available(false).build()
        );

        ResumeGapVO result = service.getResumeGap(1L);

        assertEquals(0, result.getMatchScore());
        assertTrue(result.getHasSkills().isEmpty());
        assertTrue(result.getMissingSkills().isEmpty());
        assertEquals("", result.getAiSummary());
        verify(ragForgeClient, never()).searchJd(any(), anyInt());
    }

    @Test
    void getResumeGapReturnsFallbackWhenRagEmpty() {
        when(resumeContextProvider.getResumeContext(1L)).thenReturn(
                ResumeContext.builder()
                        .available(true)
                        .title("Java后端工程师")
                        .content("熟悉 Java Spring")
                        .build()
        );
        when(ragForgeClient.searchJd(any(), eq(30))).thenReturn(List.of());

        ResumeGapVO result = service.getResumeGap(1L);

        assertEquals(0, result.getMatchScore());
        assertEquals(FALLBACK_SUMMARY, result.getAiSummary());
    }

    @Test
    void getResumeGapParsesValidLlmJson() {
        when(resumeContextProvider.getResumeContext(1L)).thenReturn(
                ResumeContext.builder()
                        .available(true)
                        .title("Java后端工程师")
                        .content("熟悉 Java Spring Redis")
                        .build()
        );
        when(ragForgeClient.searchJd(contains("岗位要求"), eq(30))).thenReturn(sampleChunks("要求 Redis K8s"));
        when(llmClient.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder().content("""
                {"hasSkills":["Java","Spring"],"missingSkills":["K8s"],"matchScore":72,"topSuggestion":"补 K8s","aiSummary":"整体匹配良好"}
                """).build());

        ResumeGapVO result = service.getResumeGap(1L);

        assertEquals(72, result.getMatchScore());
        assertEquals("K8s", result.getMissingSkills().get(0));
        assertEquals("补 K8s", result.getTopSuggestion());
    }

    @Test
    void getCompanyInsightReturnsFallbackWhenCompanyBlank() {
        CompanyInsightVO result = service.getCompanyInsight("  ");

        assertEquals(NO_DATA, result.getCompanyName());
        assertEquals(FALLBACK_SUMMARY, result.getAiSummary());
        verify(ragForgeClient, never()).searchJd(any(), anyInt());
    }

    @Test
    void getCompanyInsightReturnsFallbackWhenRagEmpty() {
        when(ragForgeClient.searchJd(any(), anyInt())).thenReturn(List.of());

        CompanyInsightVO result = service.getCompanyInsight("腾讯");

        assertEquals("腾讯", result.getCompanyName());
        assertEquals(FALLBACK_SUMMARY, result.getAiSummary());
        assertTrue(result.getTechStack().isEmpty());
    }

    @Test
    void getCompanyInsightParsesValidLlmJsonAndMergesSearches() {
        when(ragForgeClient.searchJd(contains("公司 技术栈"), eq(20)))
                .thenReturn(List.of(new RagForgeChunk(1L, 10L, "jd.md", "腾讯 大厂 Java", "JD", 0.9)));
        when(ragForgeClient.searchJd(contains("岗位 招聘"), eq(10)))
                .thenReturn(List.of(new RagForgeChunk(2L, 11L, "jd2.md", "Java 后端工程师", "JD", 0.8)));
        when(llmClient.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder().content("""
                {"companyName":"","scale":"大厂","stage":"上市","techStack":["Java"],"currentJds":["Java后端"],"aiSummary":"互联网大厂"}
                """).build());

        CompanyInsightVO result = service.getCompanyInsight("腾讯");

        assertEquals("腾讯", result.getCompanyName());
        assertEquals("大厂", result.getScale());
        assertEquals("Java", result.getTechStack().get(0));
        assertEquals("Java后端", result.getCurrentJds().get(0));
        verify(ragForgeClient).searchJd(contains("公司 技术栈"), eq(20));
        verify(ragForgeClient).searchJd(contains("岗位 招聘"), eq(10));
    }

    @Test
    void getCompanyInsightReturnsFallbackWhenLlmReturnsInvalidJson() {
        when(ragForgeClient.searchJd(any(), anyInt())).thenReturn(sampleChunks("腾讯 JD"));
        when(llmClient.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().content("not json").build());

        CompanyInsightVO result = service.getCompanyInsight("腾讯");

        assertEquals("腾讯", result.getCompanyName());
        assertEquals(FALLBACK_SUMMARY, result.getAiSummary());
    }

    private static List<RagForgeChunk> sampleChunks(String content) {
        return List.of(new RagForgeChunk(1L, 100L, "jd.md", content, "JD", 0.9));
    }
}
