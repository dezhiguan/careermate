package com.careermate.agent.tool.rag;

import com.careermate.ragforge.RagForgeChunk;
import com.careermate.ragforge.RagForgeClient;
import com.careermate.ragforge.RagForgeProperties;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagRetrieverToolTest {

    @Mock
    private RagForgeClient ragForgeClient;

    private RagForgeProperties properties;
    private RagRetrieverTool ragRetrieverTool;

    @BeforeEach
    void setUp() {
        properties = new RagForgeProperties();
        properties.setEnabled(true);
        properties.setJdKbId("16");
        properties.setInterviewKbId("21");
        properties.setPersonalKbId("31");
        ragRetrieverTool = new RagRetrieverTool(ragForgeClient, properties);
    }

    @Test
    void mapsRagForgeChunksWithMetadata() {
        when(ragForgeClient.searchInterview(anyString(), anyInt())).thenReturn(List.of(
                new RagForgeChunk(101L, 201L, "redis_qa.md", "Redis 缓存一致性方案", "INTERVIEW_QA", 0.91)
        ));

        RagRetrieveResult result = ragRetrieverTool.retrieve(RagRetrieveRequest.builder()
                .query("Redis 缓存一致性")
                .scene(RagRetrieveScene.INTERVIEW)
                .topK(5)
                .build());

        assertTrue(result.isSuccess());
        assertEquals(1, result.getChunks().size());
        RagRetrievedChunk chunk = result.getChunks().get(0);
        assertEquals(101L, chunk.getChunkId());
        assertEquals(201L, chunk.getDocId());
        assertEquals("201", chunk.getSourceId());
        assertEquals("redis_qa.md", chunk.getFileName());
        assertEquals("redis_qa.md", chunk.getSourceTitle());
        assertEquals("Redis 缓存一致性方案", chunk.getContent());
        assertEquals(0.91, chunk.getScore());
        assertEquals(RagRetrieverChunkType.INTERVIEW_QA, chunk.getChunkType());
        assertFalse(result.isFallbackUsed());
        verify(ragForgeClient).searchInterview("Redis 缓存一致性", 5);
    }

    @Test
    void normalizesTopKWithDefaultAndMax() {
        assertEquals(5, ragRetrieverTool.normalizeTopK(0));
        assertEquals(5, ragRetrieverTool.normalizeTopK(-1));
        assertEquals(20, ragRetrieverTool.normalizeTopK(100));
        assertEquals(8, ragRetrieverTool.normalizeTopK(8));
    }

    @Test
    void interviewSceneUsesSearchInterview() {
        when(ragForgeClient.searchInterview("JVM 调优", 5)).thenReturn(List.of(
                new RagForgeChunk(1L, 2L, "jvm.md", "JVM 调优要点", null, 0.8)
        ));

        RagRetrieveResult result = ragRetrieverTool.retrieve(RagRetrieveRequest.builder()
                .query("JVM 调优")
                .scene(RagRetrieveScene.INTERVIEW)
                .topK(5)
                .build());

        assertTrue(result.isSuccess());
        assertEquals(RagRetrieverChunkType.INTERVIEW_QA, result.getChunks().get(0).getChunkType());
        verify(ragForgeClient).searchInterview("JVM 调优", 5);
        verify(ragForgeClient, never()).searchJd(anyString(), anyInt());
    }

    @Test
    void disabledRagForgeReturnsFallbackWithoutThrowing() {
        properties.setEnabled(false);

        RagRetrieveResult result = ragRetrieverTool.retrieve(RagRetrieveRequest.builder()
                .query("广州 Java 后端行情")
                .scene(RagRetrieveScene.MARKET)
                .topK(5)
                .build());

        assertFalse(result.isSuccess());
        assertTrue(result.isFallbackUsed());
        assertEquals(RagRetrieverTool.ERROR_RAGFORGE_DISABLED, result.getErrorCode());
        assertTrue(result.getChunks().isEmpty());
        verify(ragForgeClient, never()).searchJd(anyString(), anyInt());
    }

    @Test
    void missingKbIdReturnsFallback() {
        properties.setInterviewKbId("");

        RagRetrieveResult result = ragRetrieverTool.retrieve(RagRetrieveRequest.builder()
                .query("Redis 面试题")
                .scene(RagRetrieveScene.INTERVIEW)
                .topK(5)
                .build());

        assertFalse(result.isSuccess());
        assertTrue(result.isFallbackUsed());
        assertEquals(RagRetrieverTool.ERROR_KB_NOT_CONFIGURED, result.getErrorCode());
        verify(ragForgeClient, never()).searchInterview(anyString(), anyInt());
    }

    @Test
    void ragForgeExceptionReturnsStructuredFailure() {
        when(ragForgeClient.searchJd(anyString(), anyInt())).thenThrow(new RuntimeException("upstream down"));

        RagRetrieveResult result = ragRetrieverTool.retrieve(RagRetrieveRequest.builder()
                .query("Java 后端行情")
                .scene(RagRetrieveScene.MARKET)
                .topK(5)
                .build());

        assertFalse(result.isSuccess());
        assertTrue(result.isFallbackUsed());
        assertEquals(RagRetrieverTool.ERROR_EMPTY_RESULTS, result.getErrorCode());
        assertTrue(result.getLatencyMs() >= 0);
    }

    @Test
    void opportunitySceneUsesJdSearch() {
        when(ragForgeClient.searchJd("核心能力", 3)).thenReturn(List.of(
                new RagForgeChunk(11L, 22L, "jd_sample.md", "任职要求 Java Spring", "JD", 0.77)
        ));

        RagRetrieveResult result = ragRetrieverTool.retrieve(RagRetrieveRequest.builder()
                .query("核心能力")
                .scene(RagRetrieveScene.OPPORTUNITY)
                .topK(3)
                .build());

        assertTrue(result.isSuccess());
        assertEquals(RagRetrieverChunkType.JD, result.getChunks().get(0).getChunkType());
        verify(ragForgeClient).searchJd("核心能力", 3);
    }

    @Test
    void resumeSceneUsesPersonalKbSearch() {
        when(ragForgeClient.search(eq(31L), eq("项目经历"), eq(5), eq(List.of()))).thenReturn(List.of(
                new RagForgeChunk(5L, 6L, "resume.md", "订单系统项目经历", "RESUME", 0.66)
        ));

        RagRetrieveResult result = ragRetrieverTool.retrieve(RagRetrieveRequest.builder()
                .query("项目经历")
                .scene(RagRetrieveScene.RESUME)
                .topK(5)
                .build());

        assertTrue(result.isSuccess());
        assertEquals(RagRetrieverChunkType.RESUME, result.getChunks().get(0).getChunkType());
        verify(ragForgeClient).search(31L, "项目经历", 5, List.of());
    }

    @Test
    void toolDataContainsStructuredFields() {
        RagRetrieveResult result = RagRetrieveResult.builder()
                .success(true)
                .query("test")
                .scene(RagRetrieveScene.GENERAL)
                .chunks(List.of(RagRetrievedChunk.builder()
                        .content("content")
                        .chunkId(1L)
                        .docId(2L)
                        .chunkType(RagRetrieverChunkType.GENERAL)
                        .build()))
                .fallbackUsed(false)
                .latencyMs(12L)
                .build();

        Map<String, Object> data = ragRetrieverTool.toToolData(result);
        assertEquals(true, data.get("success"));
        assertEquals("test", data.get("query"));
        assertEquals("GENERAL", data.get("scene"));
        assertEquals(1, data.get("chunkCount"));
        assertEquals(false, data.get("fallbackUsed"));
        assertEquals(12L, data.get("latencyMs"));
    }
}
