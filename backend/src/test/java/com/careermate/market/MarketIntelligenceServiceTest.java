package com.careermate.market;

import com.careermate.agent.tool.rag.RagRetrieveRequest;
import com.careermate.agent.tool.rag.RagRetrieveResult;
import com.careermate.agent.tool.rag.RagRetrieveScene;
import com.careermate.agent.tool.rag.RagRetrievedChunk;
import com.careermate.agent.tool.rag.RagRetrieverChunkType;
import com.careermate.knowledge.KnowledgeRetrievalService;
import com.careermate.llm.LlmClient;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.dto.ChatResponse;
import com.careermate.market.dto.CompanyInsightVO;
import com.careermate.market.dto.ResumeGapVO;
import com.careermate.market.dto.SalaryInsightVO;
import com.careermate.market.dto.SkillTrendsVO;
import com.careermate.market.service.MarketIntelligenceService;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketIntelligenceServiceTest {

    private static final String FALLBACK_SUMMARY = "暂时无法获取市场数据，请稍后再试";
    private static final String NO_DATA = "暂无数据";

    @Mock
    private KnowledgeRetrievalService knowledgeRetrievalService;
    @Mock
    private LlmClient llmClient;
    @Mock
    private ResumeContextProvider resumeContextProvider;

    private MarketIntelligenceService service;

    @BeforeEach
    void setUp() {
        service = new MarketIntelligenceService(
                knowledgeRetrievalService,
                llmClient,
                resumeContextProvider,
                new ObjectMapper()
        );
        lenient().when(knowledgeRetrievalService.toMarketCitations(any())).thenAnswer(invocation -> {
            RagRetrieveResult result = invocation.getArgument(0);
            if (result == null || !result.isSuccess() || result.getChunks() == null) {
                return List.of();
            }
            return result.getChunks().stream().map(chunk -> {
                com.careermate.market.dto.MarketSourceCitationVO citation =
                        new com.careermate.market.dto.MarketSourceCitationVO();
                citation.setCitation(chunk.getCitation());
                citation.setContentPreview(chunk.getContentPreview());
                citation.setSourceTitle(chunk.getFileName());
                citation.setScore(chunk.getScore());
                citation.setChunkType(chunk.getChunkType() == null ? null : chunk.getChunkType().name());
                return citation;
            }).toList();
        });
        lenient().when(knowledgeRetrievalService.toSourceSummaries(any())).thenAnswer(invocation -> {
            RagRetrieveResult result = invocation.getArgument(0);
            if (result == null || !result.isSuccess() || result.getChunks() == null) {
                return List.of();
            }
            return result.getChunks().stream()
                    .map(chunk -> "[" + chunk.getCitation() + "] " + chunk.getContentPreview())
                    .toList();
        });
    }

    @Test
    void getSalaryInsightReturnsFallbackWhenRagEmpty() {
        when(knowledgeRetrievalService.retrieve(any())).thenReturn(emptyResult(RagRetrieveScene.MARKET));

        SalaryInsightVO result = service.getSalaryInsight("Java后端", "广州", "3-5年");

        assertEquals(NO_DATA, result.getP25());
        assertEquals(NO_DATA, result.getP50());
        assertEquals(FALLBACK_SUMMARY, result.getAiSummary());
        assertTrue(result.getCitations().isEmpty());
        verify(llmClient, never()).chat(any());
    }

    @Test
    void getSalaryInsightParsesValidLlmJsonAndAttachesSafeCitations() {
        when(knowledgeRetrievalService.retrieve(any())).thenReturn(sampleMarketResult());
        when(llmClient.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder().content("""
                {"p25":"22K","p50":"28K","p75":"32K","p90":"38K","trend":"上涨","aiSummary":"薪资稳步上涨"}
                """).build());

        SalaryInsightVO result = service.getSalaryInsight("Java后端", "广州", "3-5年");

        assertEquals("28K", result.getP50());
        assertEquals("上涨", result.getTrend());
        assertEquals("薪资稳步上涨", result.getAiSummary());
        assertEquals(1, result.getCitations().size());
        assertEquals("MARKET_REPORT@jd.md", result.getCitations().get(0).getCitation());
        assertEquals("月薪 25K-35K", result.getCitations().get(0).getContentPreview());
        assertFalse(result.getSourceSummaries().get(0).contains("完整敏感"));
    }

    @Test
    void getSalaryInsightReturnsFallbackWhenLlmReturnsNonJson() {
        when(knowledgeRetrievalService.retrieve(any())).thenReturn(sampleMarketResult());
        when(llmClient.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().content("无法分析").build());

        SalaryInsightVO result = service.getSalaryInsight("Java后端", "广州", "3-5年");

        assertEquals(FALLBACK_SUMMARY, result.getAiSummary());
        assertEquals(NO_DATA, result.getP75());
    }

    @Test
    void getSkillTrendsParsesValidLlmJson() {
        when(knowledgeRetrievalService.retrieve(any())).thenReturn(sampleMarketResult());
        when(llmClient.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder().content("""
                {"skills":[{"rank":1,"name":"Java","level":"高频","growth":"稳定"}],"aiSummary":"Java 仍是核心"}
                """).build());

        SkillTrendsVO result = service.getSkillTrends("Java后端");

        assertNotNull(result.getSkills());
        assertEquals(1, result.getSkills().size());
        assertEquals("Java", result.getSkills().get(0).getName());
        assertEquals("Java 仍是核心", result.getAiSummary());
        assertEquals(1, result.getCitations().size());
    }

    @Test
    void getSkillTrendsReturnsFallbackWhenLlmThrows() {
        when(knowledgeRetrievalService.retrieve(any())).thenReturn(sampleMarketResult());
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
        verify(knowledgeRetrievalService, never()).retrieve(any());
    }

    @Test
    void getResumeGapComputesSynchronouslyReturnsTerminalStateNotLoadingWhenCacheEnabled() {
        // 修复验证：缓存开启且未命中时，getResumeGap 应同步计算并返回终态（非 LOADING），
        // 不再走「异步刷新+返回 LOADING」导致永远转圈。
        org.springframework.cache.CacheManager cacheManager =
                org.mockito.Mockito.mock(org.springframework.cache.CacheManager.class);
        org.springframework.cache.Cache cache = org.mockito.Mockito.mock(org.springframework.cache.Cache.class);
        org.mockito.Mockito.when(cacheManager.getCache("market:resume-gap")).thenReturn(cache);
        org.mockito.Mockito.when(cache.get(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(ResumeGapVO.class))).thenReturn(null); // 未命中
        @SuppressWarnings("unchecked")
        org.springframework.beans.factory.ObjectProvider<org.springframework.cache.CacheManager> provider =
                org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
        org.mockito.Mockito.when(provider.getIfAvailable()).thenReturn(cacheManager);
        org.springframework.context.ApplicationContext ctx =
                org.mockito.Mockito.mock(org.springframework.context.ApplicationContext.class);

        MarketIntelligenceService cachedService = new MarketIntelligenceService(
                knowledgeRetrievalService, llmClient, resumeContextProvider, new ObjectMapper(), provider, ctx);
        when(resumeContextProvider.getResumeContext(1L)).thenReturn(ResumeContext.builder().available(false).build());

        ResumeGapVO result = cachedService.getResumeGap(1L);

        assertNotNull(result.getMeta());
        assertNotEquals("LOADING", result.getMeta().state().name(), "应同步返回终态，不再永远 LOADING");
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
        when(knowledgeRetrievalService.retrieve(any())).thenReturn(emptyResult(RagRetrieveScene.OPPORTUNITY));

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
        when(knowledgeRetrievalService.retrieve(any())).thenReturn(sampleOpportunityResult());
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
        verify(knowledgeRetrievalService, never()).retrieveMerged(any());
    }

    @Test
    void getCompanyInsightReturnsFallbackWhenRagEmpty() {
        when(knowledgeRetrievalService.retrieveMerged(any())).thenReturn(emptyResult(RagRetrieveScene.COMPANY));

        CompanyInsightVO result = service.getCompanyInsight("腾讯");

        assertEquals("腾讯", result.getCompanyName());
        assertEquals(FALLBACK_SUMMARY, result.getAiSummary());
        assertTrue(result.getTechStack().isEmpty());
    }

    @Test
    void getCompanyInsightParsesValidLlmJsonAndAttachesCompanyCitations() {
        when(knowledgeRetrievalService.retrieveMerged(any())).thenReturn(sampleCompanyResult());
        when(llmClient.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder().content("""
                {"companyName":"","scale":"大厂","stage":"上市","techStack":["Java"],"currentJds":["Java后端"],"aiSummary":"互联网大厂"}
                """).build());

        CompanyInsightVO result = service.getCompanyInsight("腾讯");

        assertEquals("腾讯", result.getCompanyName());
        assertEquals("大厂", result.getScale());
        assertEquals("Java", result.getTechStack().get(0));
        assertEquals("Java后端", result.getCurrentJds().get(0));
        assertEquals(RagRetrieverChunkType.COMPANY.name(), result.getCitations().get(0).getChunkType());
        verify(knowledgeRetrievalService).retrieveMerged(any());
    }

    @Test
    void getCompanyInsightReturnsFallbackWhenLlmReturnsInvalidJson() {
        when(knowledgeRetrievalService.retrieveMerged(any())).thenReturn(sampleCompanyResult());
        when(llmClient.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().content("not json").build());

        CompanyInsightVO result = service.getCompanyInsight("腾讯");

        assertEquals("腾讯", result.getCompanyName());
        assertEquals(FALLBACK_SUMMARY, result.getAiSummary());
    }

    private static RagRetrieveResult emptyResult(RagRetrieveScene scene) {
        return RagRetrieveResult.fallback("q", scene, KnowledgeRetrievalService.ERROR_EMPTY_RESULTS, 1);
    }

    private static RagRetrieveResult sampleMarketResult() {
        return RagRetrieveResult.builder()
                .success(true)
                .query("薪资")
                .scene(RagRetrieveScene.MARKET)
                .chunks(List.of(RagRetrievedChunk.builder()
                        .content("月薪 25K-35K 完整敏感段落不应出现在 citations")
                        .contentPreview("月薪 25K-35K")
                        .citation("MARKET_REPORT@jd.md")
                        .chunkType(RagRetrieverChunkType.MARKET_REPORT)
                        .fileName("jd.md")
                        .score(0.9)
                        .build()))
                .latencyMs(5L)
                .build();
    }

    private static RagRetrieveResult sampleOpportunityResult() {
        return RagRetrieveResult.builder()
                .success(true)
                .query("岗位要求")
                .scene(RagRetrieveScene.OPPORTUNITY)
                .chunks(List.of(RagRetrievedChunk.builder()
                        .content("要求 Redis K8s")
                        .contentPreview("要求 Redis K8s")
                        .citation("JD@jd.md")
                        .chunkType(RagRetrieverChunkType.JD)
                        .fileName("jd.md")
                        .score(0.8)
                        .build()))
                .latencyMs(5L)
                .build();
    }

    private static RagRetrieveResult sampleCompanyResult() {
        return RagRetrieveResult.builder()
                .success(true)
                .query("腾讯")
                .scene(RagRetrieveScene.COMPANY)
                .chunks(List.of(RagRetrievedChunk.builder()
                        .content("腾讯 大厂 Java")
                        .contentPreview("腾讯 大厂 Java")
                        .citation("COMPANY@company.md")
                        .chunkType(RagRetrieverChunkType.COMPANY)
                        .fileName("company.md")
                        .score(0.88)
                        .build()))
                .latencyMs(5L)
                .build();
    }
}
