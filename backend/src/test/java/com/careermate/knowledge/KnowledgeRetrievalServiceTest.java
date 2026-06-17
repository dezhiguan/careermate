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

import java.util.List;

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
}
