package com.careermate.knowledge;

import com.careermate.agent.tool.rag.RagRetrieveRequest;
import com.careermate.agent.tool.rag.RagRetrieveResult;
import com.careermate.agent.tool.rag.RagRetrieveScene;
import com.careermate.agent.tool.rag.RagRetrievedChunk;
import com.careermate.agent.tool.rag.RagRetrieverChunkType;
import com.careermate.ragforge.RagForgeChunk;
import com.careermate.ragforge.RagForgeClient;
import com.careermate.ragforge.RagForgeProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeRetrievalServiceTest {

    @Mock
    private RagForgeClient ragForgeClient;

    private RagForgeProperties properties;
    private KnowledgeRetrievalService service;

    @BeforeEach
    void setUp() {
        properties = new RagForgeProperties();
        properties.setEnabled(true);
        properties.setJdKbId("16");
        properties.setInterviewKbId("21");
        properties.setPersonalKbId("31");
        service = new KnowledgeRetrievalService(ragForgeClient, properties);
    }

    @Test
    void generalValidationAllowsJdOrInterviewOnly() {
        properties.setJdKbId("");
        properties.setInterviewKbId("21");
        properties.setPersonalKbId("31");
        when(ragForgeClient.searchJd("Redis", 5)).thenReturn(List.of());

        RagRetrieveResult result = service.retrieve(RagRetrieveRequest.builder()
                .query("Redis")
                .scene(RagRetrieveScene.GENERAL)
                .topK(5)
                .build());

        assertFalse(result.isSuccess());
        assertEquals(KnowledgeRetrievalService.ERROR_EMPTY_RESULTS, result.getErrorCode());
        verify(ragForgeClient).searchInterview("Redis", 5);
        verify(ragForgeClient, never()).search(anyLong(), anyString(), anyInt(), eq(List.of()));
    }

    @Test
    void generalSearchDoesNotFallbackToPersonalKb() {
        when(ragForgeClient.searchJd(anyString(), anyInt())).thenReturn(List.of());
        when(ragForgeClient.searchInterview(anyString(), anyInt())).thenReturn(List.of());

        RagRetrieveResult result = service.retrieve(RagRetrieveRequest.builder()
                .query("Redis")
                .scene(RagRetrieveScene.GENERAL)
                .topK(5)
                .build());

        assertFalse(result.isSuccess());
        verify(ragForgeClient, never()).search(anyLong(), anyString(), anyInt(), eq(List.of()));
    }

    @Test
    void companySceneMapsJdPatternToCompanyChunkType() {
        when(ragForgeClient.searchJd("阿里 公司", 5)).thenReturn(List.of(
                new RagForgeChunk(2L, 3L, "company.md", "阿里 技术栈", "JD_PATTERN", 0.85)
        ));

        RagRetrieveResult result = service.retrieve(RagRetrieveRequest.builder()
                .query("阿里 公司")
                .scene(RagRetrieveScene.COMPANY)
                .topK(5)
                .build());

        assertTrue(result.isSuccess());
        assertEquals(RagRetrieverChunkType.COMPANY, result.getChunks().get(0).getChunkType());
        assertEquals("COMPANY@company.md", result.getChunks().get(0).getCitation());
    }

    @Test
    void companySceneUsesJdSearchAndMapsChunkType() {
        when(ragForgeClient.searchJd("腾讯 公司", 5)).thenReturn(List.of(
                new RagForgeChunk(1L, 2L, "company.md", "腾讯 大厂 Java 技术栈", "JD", 0.88)
        ));

        RagRetrieveResult result = service.retrieve(RagRetrieveRequest.builder()
                .query("腾讯 公司")
                .scene(RagRetrieveScene.COMPANY)
                .topK(5)
                .build());

        assertTrue(result.isSuccess());
        RagRetrievedChunk chunk = result.getChunks().get(0);
        assertEquals(RagRetrieverChunkType.COMPANY, chunk.getChunkType());
        assertNotNull(chunk.getCitation());
        assertNotNull(chunk.getContentPreview());
        assertTrue(chunk.getContentPreview().length() <= KnowledgeRetrievalSupport.DEFAULT_PREVIEW_CHARS + 3);
        verify(ragForgeClient).searchJd("腾讯 公司", 5);
    }

    @Test
    void retrieveContextTextJoinsInternalContent() {
        when(ragForgeClient.searchInterview("JVM", 3)).thenReturn(List.of(
                new RagForgeChunk(1L, 2L, "jvm.md", "JVM 调优完整内容段落", "INTERVIEW_QA", 0.9)
        ));

        String context = service.retrieveContextText(RagRetrieveScene.INTERVIEW, "JVM", 3);

        assertTrue(context.contains("JVM 调优完整内容段落"));
    }

    @Test
    void interviewOnlyConfigPassesValidationForGeneral() {
        properties.setJdKbId("");
        properties.setInterviewKbId("21");
        when(ragForgeClient.searchInterview("Redis", 5)).thenReturn(List.of(
                new RagForgeChunk(3L, 4L, "redis.md", "Redis 面试题", "INTERVIEW_QA", 0.7)
        ));

        RagRetrieveResult result = service.retrieve(RagRetrieveRequest.builder()
                .query("Redis")
                .scene(RagRetrieveScene.GENERAL)
                .topK(5)
                .build());

        assertTrue(result.isSuccess());
        assertEquals(RagRetrieverChunkType.INTERVIEW_QA, result.getChunks().get(0).getChunkType());
    }

    @Test
    void disabledAndMissingQueryReturnStructuredFallbackWithoutCallingClient() {
        RagRetrieveResult missingQuery = service.retrieve(RagRetrieveRequest.builder()
                .query("  ")
                .scene(RagRetrieveScene.OPPORTUNITY)
                .topK(5)
                .build());
        assertFalse(missingQuery.isSuccess());
        assertEquals(KnowledgeRetrievalService.ERROR_QUERY_MISSING, missingQuery.getErrorCode());

        properties.setEnabled(false);
        RagRetrieveResult disabled = service.retrieve(RagRetrieveRequest.builder()
                .query("Java")
                .scene(RagRetrieveScene.OPPORTUNITY)
                .topK(5)
                .build());
        assertFalse(disabled.isSuccess());
        assertEquals(KnowledgeRetrievalService.ERROR_RAGFORGE_DISABLED, disabled.getErrorCode());
        verify(ragForgeClient, never()).searchJd(anyString(), anyInt());
    }

    @Test
    void resumeSceneUsesPersonalKbWithChunkTypeFilterAndNormalizesTopK() {
        // RAGForge /search 服务端 topK 上限为 150，故检索层必须把超限 topK 收敛到 ≤150。
        when(ragForgeClient.search(31L, "我的简历", 150, List.of("RESUME"))).thenReturn(List.of(
                new RagForgeChunk(10L, 20L, "resume.md", "Java 后端简历", "PERSONAL_RESUME", 0.91)
        ));

        RagRetrieveResult result = service.retrieve(RagRetrieveRequest.builder()
                .query(" 我的简历 ")
                .scene(RagRetrieveScene.RESUME)
                .topK(999)
                .filters(Map.of("chunkTypes", List.of("RESUME")))
                .build());

        assertTrue(result.isSuccess());
        assertEquals("我的简历", result.getQuery());
        assertEquals(RagRetrieverChunkType.RESUME, result.getChunks().get(0).getChunkType());
        assertEquals("resume.md", result.getChunks().get(0).getSourceTitle());
        assertEquals("PERSONAL_RESUME", result.getChunks().get(0).getMetadata().get("rawChunkType"));
        verify(ragForgeClient).search(31L, "我的简历", 150, List.of("RESUME"));
    }

    @Test
    void invalidPersonalKbConfigReturnsKbNotConfigured() {
        properties.setPersonalKbId("not-number");

        RagRetrieveResult result = service.retrieve(RagRetrieveRequest.builder()
                .query("简历")
                .scene(RagRetrieveScene.RESUME)
                .topK(5)
                .build());

        assertFalse(result.isSuccess());
        assertEquals(KnowledgeRetrievalService.ERROR_KB_NOT_CONFIGURED, result.getErrorCode());
        verify(ragForgeClient, never()).search(anyLong(), anyString(), anyInt(), eq(List.of()));
    }

    @Test
    void retrieveMergedDeduplicatesByChunkIdThenContent() {
        when(ragForgeClient.searchJd("Java", 5)).thenReturn(List.of(
                new RagForgeChunk(1L, 100L, "a.md", "同一个 chunk", "JD", 0.9),
                new RagForgeChunk(null, 101L, "b.md", "同内容", "JD", 0.8)
        ));
        when(ragForgeClient.searchJd("Redis", 5)).thenReturn(List.of(
                new RagForgeChunk(1L, 100L, "a.md", "同一个 chunk", "JD", 0.7),
                new RagForgeChunk(null, 102L, "c.md", "同内容", "JD", 0.6),
                new RagForgeChunk(3L, 103L, "d.md", "新内容", "MARKET_REPORT", 0.5)
        ));

        RagRetrieveResult merged = service.retrieveMerged(List.of(
                RagRetrieveRequest.builder().query("Java").scene(RagRetrieveScene.OPPORTUNITY).topK(0).build(),
                RagRetrieveRequest.builder().query("Redis").scene(RagRetrieveScene.OPPORTUNITY).topK(5).build()
        ));

        assertTrue(merged.isSuccess());
        assertEquals("Java", merged.getQuery());
        assertEquals(3, merged.getChunks().size());
        assertEquals(RagRetrieverChunkType.MARKET_REPORT, merged.getChunks().get(2).getChunkType());
    }

    @Test
    void retrieveMergedContextAndSourceHelpersHandleFallbackAndSuccess() {
        assertEquals("", service.retrieveMergedContextText(List.of(), 100));
        assertEquals(List.of(), service.toMarketCitations(null));
        assertEquals(List.of(), service.toSourceSummaries(RagRetrieveResult.fallback("q", RagRetrieveScene.MARKET, "E", 1)));

        when(ragForgeClient.searchJd("薪资", 5)).thenReturn(List.of(
                new RagForgeChunk(11L, 12L, "market.md", "市场薪资报告内容", "MARKET", 0.72)
        ));
        RagRetrieveResult result = service.retrieve(RagRetrieveRequest.builder()
                .query("薪资")
                .scene(RagRetrieveScene.MARKET)
                .topK(5)
                .build());

        assertEquals(1, service.toMarketCitations(result).size());
        assertEquals("MARKET_REPORT", service.toMarketCitations(result).get(0).getChunkType());
        assertTrue(service.toSourceSummaries(result).get(0).contains("市场薪资报告内容"));
        assertTrue(service.retrieveMergedContextText(List.of(
                RagRetrieveRequest.builder().query("薪资").scene(RagRetrieveScene.MARKET).topK(5).build()
        ), 20).contains("市场薪资报告内容"));
    }

    @Test
    void nullChunksAreSkippedAndClientExceptionBecomesFallback() {
        when(ragForgeClient.searchInterview("异常", 5)).thenThrow(new IllegalStateException("down"));
        RagRetrieveResult failed = service.retrieve(RagRetrieveRequest.builder()
                .query("异常")
                .scene(RagRetrieveScene.INTERVIEW)
                .topK(-1)
                .build());
        assertFalse(failed.isSuccess());
        assertEquals(KnowledgeRetrievalService.ERROR_RAGFORGE_FAILED, failed.getErrorCode());

        when(ragForgeClient.searchInterview("空块", 5)).thenReturn(Arrays.asList(
                null,
                new RagForgeChunk(1L, 1L, null, null, null, null)
        ));
        RagRetrieveResult result = service.retrieve(RagRetrieveRequest.builder()
                .query("空块")
                .scene(RagRetrieveScene.INTERVIEW)
                .topK(-1)
                .build());
        assertTrue(result.isSuccess());
        assertEquals("", result.getChunks().get(0).getContent());
        assertEquals(RagRetrieverChunkType.INTERVIEW_QA, result.getChunks().get(0).getChunkType());
    }

    // ---- A1-4：精确 scene→kbId 路由 ----

    @Test
    void marketSceneUsesMarketKbWhenConfigured() {
        properties.setMarketKbId("597");
        when(ragForgeClient.search(eq(597L), anyString(), anyInt(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(new RagForgeChunk(1L, 2L, "salary.md", "P75 45k", "JD", 0.9)));

        RagRetrieveResult result = service.retrieve(RagRetrieveRequest.builder()
                .query("Java 薪资")
                .scene(RagRetrieveScene.MARKET)
                .topK(5)
                .build());

        assertTrue(result.isSuccess());
        verify(ragForgeClient).search(eq(597L), eq("Java 薪资"), eq(5), org.mockito.ArgumentMatchers.any());
        verify(ragForgeClient, never()).searchJd(anyString(), anyInt());
    }

    @Test
    void marketSceneFallsBackToJdWhenKbUnset() {
        when(ragForgeClient.searchJd("Java 薪资", 5))
                .thenReturn(List.of(new RagForgeChunk(1L, 2L, "jd.md", "薪资范围 30-45k", "JD", 0.8)));

        RagRetrieveResult result = service.retrieve(RagRetrieveRequest.builder()
                .query("Java 薪资")
                .scene(RagRetrieveScene.MARKET)
                .topK(5)
                .build());

        assertTrue(result.isSuccess());
        verify(ragForgeClient).searchJd("Java 薪资", 5);
    }

    @Test
    void companySceneUsesCompanyKbWhenConfigured() {
        properties.setCompanyKbId("595");
        when(ragForgeClient.search(eq(595L), anyString(), anyInt(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(new RagForgeChunk(1L, 2L, "company.md", "字节 技术栈", "JD_PATTERN", 0.9)));

        RagRetrieveResult result = service.retrieve(RagRetrieveRequest.builder()
                .query("字节 公司")
                .scene(RagRetrieveScene.COMPANY)
                .topK(5)
                .build());

        assertTrue(result.isSuccess());
        verify(ragForgeClient).search(eq(595L), eq("字节 公司"), eq(5), org.mockito.ArgumentMatchers.any());
        verify(ragForgeClient, never()).searchJd(anyString(), anyInt());
    }

    // ---- 评审第五章 #2：静默回退改显式（shouldFallbackToJd 判定） ----

    @Test
    void shouldFallbackToJdTrueWhenMarketOrCompanyKbUnsetButJdSet() {
        // jdKbId 已配（setUp 里为 "16"），marketKbId/companyKbId 未配 → 会降级到 JD 库
        assertTrue(service.shouldFallbackToJd(RagRetrieveScene.MARKET));
        assertTrue(service.shouldFallbackToJd(RagRetrieveScene.COMPANY));
    }

    @Test
    void shouldNotFallbackWhenDedicatedKbConfigured() {
        properties.setMarketKbId("597");
        properties.setCompanyKbId("595");
        assertFalse(service.shouldFallbackToJd(RagRetrieveScene.MARKET));
        assertFalse(service.shouldFallbackToJd(RagRetrieveScene.COMPANY));
    }

    @Test
    void shouldNotFallbackWhenJdKbAlsoUnset() {
        // 专库与 JD 库都未配 → 不存在「降级到 JD」这回事（会走 KB_NOT_CONFIGURED）
        properties.setJdKbId("");
        assertFalse(service.shouldFallbackToJd(RagRetrieveScene.MARKET));
        assertFalse(service.shouldFallbackToJd(RagRetrieveScene.COMPANY));
    }

    @Test
    void shouldNotFallbackForNonMarketCompanyScenes() {
        assertFalse(service.shouldFallbackToJd(RagRetrieveScene.OPPORTUNITY));
        assertFalse(service.shouldFallbackToJd(RagRetrieveScene.INTERVIEW));
        assertFalse(service.shouldFallbackToJd(RagRetrieveScene.RESUME));
        assertFalse(service.shouldFallbackToJd(RagRetrieveScene.SKILL));
        assertFalse(service.shouldFallbackToJd(RagRetrieveScene.GENERAL));
    }

    // ---- 评审第五章 #3：接入岗位技能画像库（SKILL 场景） ----

    @Test
    void skillSceneUsesSkillKbWhenConfiguredAndMapsChunkType() {
        properties.setSkillKbId("598");
        when(ragForgeClient.search(eq(598L), anyString(), anyInt(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(new RagForgeChunk(1L, 2L, "skill.md", "Java 高频技能：JVM/并发/Spring", "SKILL", 0.9)));

        RagRetrieveResult result = service.retrieve(RagRetrieveRequest.builder()
                .query("Java 后端 技能")
                .scene(RagRetrieveScene.SKILL)
                .topK(5)
                .build());

        assertTrue(result.isSuccess());
        assertEquals(RagRetrieverChunkType.SKILL, result.getChunks().get(0).getChunkType());
        verify(ragForgeClient).search(eq(598L), eq("Java 后端 技能"), eq(5), org.mockito.ArgumentMatchers.any());
        verify(ragForgeClient, never()).searchJd(anyString(), anyInt());
    }

    @Test
    void skillSceneReturnsKbNotConfiguredWhenUnset() {
        // skillKbId 未配 → 明确 KB_NOT_CONFIGURED，绝不回退到 JD 库（不污染技能语义）
        RagRetrieveResult result = service.retrieve(RagRetrieveRequest.builder()
                .query("技能")
                .scene(RagRetrieveScene.SKILL)
                .topK(5)
                .build());

        assertFalse(result.isSuccess());
        assertEquals(KnowledgeRetrievalService.ERROR_KB_NOT_CONFIGURED, result.getErrorCode());
        verify(ragForgeClient, never()).searchJd(anyString(), anyInt());
    }

    @Test
    void skillSceneDefaultsChunkTypeWhenRawTypeBlank() {
        properties.setSkillKbId("598");
        when(ragForgeClient.search(eq(598L), anyString(), anyInt(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(new RagForgeChunk(3L, 4L, "s.md", "技能内容", null, 0.6)));

        RagRetrieveResult result = service.retrieve(RagRetrieveRequest.builder()
                .query("技能")
                .scene(RagRetrieveScene.SKILL)
                .topK(5)
                .build());

        assertTrue(result.isSuccess());
        assertEquals(RagRetrieverChunkType.SKILL, result.getChunks().get(0).getChunkType());
    }
}
