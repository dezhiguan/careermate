package com.careermate.company;

import com.careermate.agent.tool.rag.RagRetrieveResult;
import com.careermate.agent.tool.rag.RagRetrieveScene;
import com.careermate.agent.tool.rag.RagRetrievedChunk;
import com.careermate.company.dto.CompanyAtmosphereVO;
import com.careermate.company.service.CompanyAtmosphereService;
import com.careermate.knowledge.KnowledgeRetrievalService;
import com.careermate.llm.LlmClient;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.dto.ChatResponse;
import com.careermate.market.dto.MarketSourceCitationVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyAtmosphereServiceTest {

    @Mock
    private KnowledgeRetrievalService knowledgeRetrievalService;
    @Mock
    private LlmClient llmClient;

    private CompanyAtmosphereService service;

    @BeforeEach
    void setUp() {
        service = new CompanyAtmosphereService(knowledgeRetrievalService, llmClient, new ObjectMapper());
        lenient().when(knowledgeRetrievalService.toMarketCitations(any()))
                .thenReturn(List.of(new MarketSourceCitationVO()));
        lenient().when(knowledgeRetrievalService.toSourceSummaries(any()))
                .thenReturn(List.of("[COMPANY@a.md] 预览"));
    }

    @Test
    void blankCompanyReturnsFallback() {
        CompanyAtmosphereVO nullResult = service.getCompanyAtmosphere(null);
        assertFalse(nullResult.isDataAvailable());
        assertEquals("未知公司", nullResult.getCompanyName());
        assertTrue(nullResult.getCultureTags().isEmpty());

        CompanyAtmosphereVO blankResult = service.getCompanyAtmosphere("   ");
        assertFalse(blankResult.isDataAvailable());
    }

    @Test
    void emptyRagContextReturnsFallback() {
        when(knowledgeRetrievalService.retrieveMerged(any()))
                .thenReturn(RagRetrieveResult.fallback("字节", RagRetrieveScene.COMPANY, "EMPTY", 1));

        CompanyAtmosphereVO result = service.getCompanyAtmosphere("字节");

        assertFalse(result.isDataAvailable());
        assertEquals("字节", result.getCompanyName());
        assertTrue(result.getSourceSummaries().isEmpty());
    }

    @Test
    void validJsonParsedAndSourcesAttached() {
        when(knowledgeRetrievalService.retrieveMerged(any())).thenReturn(sampleResult());
        when(llmClient.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder().content("""
                {"companyName":"字节跳动","workIntensity":"节奏快","teamReputation":"技术氛围强",
                 "interviewStyle":"重系统设计","overtimeSignal":"历史大小周已取消",
                 "cultureTags":[{"label":"技术氛围强","sentiment":"POSITIVE"},
                                {"label":"节奏快","sentiment":"NEGATIVE"}],
                 "aiSummary":"技术强、节奏快","dataAvailable":true}
                """).build());

        CompanyAtmosphereVO result = service.getCompanyAtmosphere("字节跳动");

        assertTrue(result.isDataAvailable());
        assertEquals("字节跳动", result.getCompanyName());
        assertEquals("节奏快", result.getWorkIntensity());
        assertEquals(2, result.getCultureTags().size());
        assertEquals("POSITIVE", result.getCultureTags().get(0).getSentiment());
        assertEquals(1, result.getCitations().size());
        assertEquals(1, result.getSourceSummaries().size());
    }

    @Test
    void companyNameBackfilledAndBadSentimentNormalized() {
        when(knowledgeRetrievalService.retrieveMerged(any())).thenReturn(sampleResult());
        when(llmClient.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder().content("""
                {"companyName":"","cultureTags":[{"label":"扁平","sentiment":"weird"},
                 {"label":"  ","sentiment":"POSITIVE"},{"label":"稳定"}],
                 "aiSummary":"x","dataAvailable":true}
                """).build());

        CompanyAtmosphereVO result = service.getCompanyAtmosphere("小红书");

        // 名字回填自入参
        assertEquals("小红书", result.getCompanyName());
        // 非法 sentiment → NEUTRAL；空标签被剔除；缺省 sentiment → NEUTRAL
        assertEquals(2, result.getCultureTags().size());
        assertEquals("扁平", result.getCultureTags().get(0).getLabel());
        assertEquals("NEUTRAL", result.getCultureTags().get(0).getSentiment());
        assertEquals("NEUTRAL", result.getCultureTags().get(1).getSentiment());
    }

    @Test
    void allEmptyAtmosphereMarksUnavailable() {
        // 知识库无氛围情报：LLM 返回全空字段+无标签，即便 dataAvailable=true 也应兜底为 false（真机发现的 bug）
        when(knowledgeRetrievalService.retrieveMerged(any())).thenReturn(sampleResult());
        when(llmClient.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder().content("""
                {"companyName":"字节跳动","workIntensity":"","teamReputation":"","interviewStyle":"",
                 "overtimeSignal":"","cultureTags":[],"aiSummary":"暂无足够情报","dataAvailable":true}
                """).build());

        CompanyAtmosphereVO result = service.getCompanyAtmosphere("字节跳动");

        assertFalse(result.isDataAvailable());
        assertTrue(result.getCultureTags().isEmpty());
    }

    @Test
    void invalidJsonReturnsFallback() {
        when(knowledgeRetrievalService.retrieveMerged(any())).thenReturn(sampleResult());
        when(llmClient.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().content("这不是 JSON").build());

        CompanyAtmosphereVO result = service.getCompanyAtmosphere("字节");

        assertFalse(result.isDataAvailable());
    }

    @Test
    void emptyLlmResponseReturnsFallback() {
        when(knowledgeRetrievalService.retrieveMerged(any())).thenReturn(sampleResult());
        when(llmClient.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().content("").build());

        CompanyAtmosphereVO result = service.getCompanyAtmosphere("字节");

        assertFalse(result.isDataAvailable());
    }

    @Test
    void unparseableJsonBodyReturnsFallback() {
        when(knowledgeRetrievalService.retrieveMerged(any())).thenReturn(sampleResult());
        // 含 { } 但内容非法，触发 objectMapper 解析异常分支
        when(llmClient.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder().content("{ not : valid json }").build());

        CompanyAtmosphereVO result = service.getCompanyAtmosphere("字节");

        assertFalse(result.isDataAvailable());
    }

    @Test
    void retrievalExceptionReturnsFallback() {
        when(knowledgeRetrievalService.retrieveMerged(any()))
                .thenThrow(new RuntimeException("rag down"));

        CompanyAtmosphereVO result = service.getCompanyAtmosphere("字节");

        assertFalse(result.isDataAvailable());
        assertEquals("字节", result.getCompanyName());
    }

    @Test
    void byJdResolvesCompanyThenReturnsAtmosphere() {
        // JD 正文含「公司:字节跳动」→ 解析出公司名 → 复用 getCompanyAtmosphere 主链路
        when(knowledgeRetrievalService.retrieve(any())).thenReturn(jdResult("公司：字节跳动\n岗位：Java 高级工程师"));
        when(knowledgeRetrievalService.retrieveMerged(any())).thenReturn(sampleResult());
        when(llmClient.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder().content("""
                {"companyName":"字节跳动","workIntensity":"节奏快","teamReputation":"技术强",
                 "interviewStyle":"重系统设计","overtimeSignal":"大小周已取消",
                 "cultureTags":[{"label":"技术氛围强","sentiment":"POSITIVE"}],
                 "aiSummary":"技术强","dataAvailable":true}
                """).build());

        CompanyAtmosphereVO result = service.getCompanyAtmosphereByJd(1234L);

        assertTrue(result.isDataAvailable());
        assertEquals("字节跳动", result.getCompanyName());
    }

    @Test
    void byJdWithMarkdownBoldCompanyField() {
        when(knowledgeRetrievalService.retrieve(any())).thenReturn(jdResult("**公司**: 小红书 | 地点：上海"));
        when(knowledgeRetrievalService.retrieveMerged(any())).thenReturn(sampleResult());
        when(llmClient.chat(any(ChatRequest.class))).thenReturn(ChatResponse.builder().content("""
                {"companyName":"小红书","workIntensity":"稳定",
                 "cultureTags":[{"label":"扁平","sentiment":"POSITIVE"}],"dataAvailable":true}
                """).build());

        CompanyAtmosphereVO result = service.getCompanyAtmosphereByJd(5L);

        assertTrue(result.isDataAvailable());
        assertEquals("小红书", result.getCompanyName());
    }

    @Test
    void byJdNullIdReturnsFallback() {
        CompanyAtmosphereVO result = service.getCompanyAtmosphereByJd(null);
        assertFalse(result.isDataAvailable());
        assertEquals("未知公司", result.getCompanyName());
    }

    @Test
    void byJdUnresolvableCompanyReturnsFallback() {
        // JD 正文没有「公司:」字段 → 无法解析 → 兜底，不误查
        when(knowledgeRetrievalService.retrieve(any())).thenReturn(jdResult("岗位职责：负责后端开发，熟悉分布式"));

        CompanyAtmosphereVO result = service.getCompanyAtmosphereByJd(9L);

        assertFalse(result.isDataAvailable());
        assertEquals("未知公司", result.getCompanyName());
    }

    @Test
    void byJdRetrievalFailureReturnsFallback() {
        when(knowledgeRetrievalService.retrieve(any())).thenReturn(
                RagRetrieveResult.fallback("公司", RagRetrieveScene.OPPORTUNITY, "ERR", 1));

        CompanyAtmosphereVO result = service.getCompanyAtmosphereByJd(7L);

        assertFalse(result.isDataAvailable());
    }

    @Test
    void byJdExceptionReturnsFallback() {
        when(knowledgeRetrievalService.retrieve(any())).thenThrow(new RuntimeException("rag down"));

        CompanyAtmosphereVO result = service.getCompanyAtmosphereByJd(3L);

        assertFalse(result.isDataAvailable());
    }

    private static RagRetrieveResult jdResult(String content) {
        return RagRetrieveResult.builder()
                .success(true)
                .query("公司 岗位")
                .scene(RagRetrieveScene.OPPORTUNITY)
                .chunks(List.of(RagRetrievedChunk.builder()
                        .content(content)
                        .sourceTitle("【JD】高级 Java 工程师")
                        .score(0.9)
                        .build()))
                .latencyMs(3L)
                .build();
    }

    private static RagRetrieveResult sampleResult() {
        return RagRetrieveResult.builder()
                .success(true)
                .query("字节")
                .scene(RagRetrieveScene.COMPANY)
                .chunks(List.of(RagRetrievedChunk.builder()
                        .content("字节跳动 技术氛围强 节奏快 历史大小周")
                        .contentPreview("字节跳动 技术氛围强")
                        .citation("COMPANY@bytedance.md")
                        .fileName("bytedance.md")
                        .score(0.9)
                        .build()))
                .latencyMs(5L)
                .build();
    }
}
