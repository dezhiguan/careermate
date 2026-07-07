package com.careermate.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.careermate.agent.tool.rag.RagRetrieveRequest;
import com.careermate.agent.tool.rag.RagRetrieveResult;
import com.careermate.agent.tool.rag.RagRetrieveScene;
import com.careermate.agent.tool.rag.RagRetrievedChunk;
import com.careermate.agent.tool.rag.RagRetrieverChunkType;
import com.careermate.knowledge.KnowledgeRetrievalService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SearchKnowledgeBaseToolTest {

    @Mock
    private KnowledgeRetrievalService knowledgeRetrievalService;

    private SearchKnowledgeBaseTool tool;

    @BeforeEach
    void setUp() {
        tool = new SearchKnowledgeBaseTool(knowledgeRetrievalService);
    }

    @Test
    void executeUsesQueryArgumentAndReturnsPreviewData() {
        RagRetrievedChunk first = RagRetrievedChunk.builder()
                .contentPreview("负责 Java 后端服务设计")
                .citation("jd-patterns.md#1")
                .score(0.92)
                .chunkType(RagRetrieverChunkType.JD)
                .build();
        RagRetrievedChunk second = RagRetrievedChunk.builder()
                .contentPreview("需要熟悉 Spring Boot")
                .citation(null)
                .score(0.81)
                .chunkType(null)
                .build();
        when(knowledgeRetrievalService.retrieve(any())).thenReturn(RagRetrieveResult.builder()
                .success(true)
                .chunks(List.of(first, second))
                .build());

        AgentToolResult result = tool.execute(AgentToolContext.builder()
                .args(Map.of("query", " Java 后端 "))
                .userMessage("忽略用户消息")
                .build());

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getSummary()).contains("2");
        assertThat(result.getData())
                .containsEntry("query", "Java 后端")
                .containsEntry("count", 2);
        assertThat((List<?>) result.getData().get("previews"))
                .hasSize(2)
                .first()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("contentPreview", "负责 Java 后端服务设计")
                .containsEntry("citation", "jd-patterns.md#1")
                .containsEntry("score", 0.92)
                .containsEntry("chunkType", "JD");

        ArgumentCaptor<RagRetrieveRequest> captor = ArgumentCaptor.forClass(RagRetrieveRequest.class);
        verify(knowledgeRetrievalService).retrieve(captor.capture());
        RagRetrieveRequest request = captor.getValue();
        assertThat(request.getQuery()).isEqualTo("Java 后端");
        assertThat(request.getScene()).isEqualTo(RagRetrieveScene.OPPORTUNITY);
        assertThat(request.getTopK()).isEqualTo(5);
    }

    @Test
    void executeFailsWithoutQueryAndSkipsRetrieval() {
        AgentToolResult result = tool.execute(AgentToolContext.builder()
                .args(Map.of("query", " "))
                .userMessage(" ")
                .build());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo("query 为空");
        verify(knowledgeRetrievalService, never()).retrieve(any());
    }

    @Test
    void executeFallsBackToTruncatedUserMessage() {
        String longMessage = "a".repeat(120);
        when(knowledgeRetrievalService.retrieve(any())).thenReturn(RagRetrieveResult.builder()
                .success(false)
                .chunks(List.of())
                .build());

        AgentToolResult result = tool.execute(AgentToolContext.builder()
                .args(Map.of())
                .userMessage(longMessage)
                .build());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getSummary()).isEqualTo("知识库暂无相关内容");
        ArgumentCaptor<RagRetrieveRequest> captor = ArgumentCaptor.forClass(RagRetrieveRequest.class);
        verify(knowledgeRetrievalService).retrieve(captor.capture());
        assertThat(captor.getValue().getQuery())
                .hasSize(100)
                .isEqualTo(longMessage.substring(0, 100));
    }
}
