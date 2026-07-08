package com.careermate.agent.rag;

import com.careermate.agent.tool.rag.RagRetrieveRequest;
import com.careermate.agent.tool.rag.RagRetrieveResult;
import com.careermate.agent.tool.rag.RagRetrieveScene;
import com.careermate.agent.tool.rag.RagRetrievedChunk;
import com.careermate.agent.tool.rag.RagRetrieverChunkType;
import com.careermate.knowledge.KnowledgeRetrievalService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeepPathKnowledgeAugmentorTest {

    private final KnowledgeRetrievalService retrieval = mock(KnowledgeRetrievalService.class);
    private final DeepPathKnowledgeAugmentor augmentor = new DeepPathKnowledgeAugmentor(retrieval);

    private RagRetrievedChunk chunk(String citation, String preview) {
        return RagRetrievedChunk.builder()
                .citation(citation)
                .contentPreview(preview)
                .chunkType(RagRetrieverChunkType.JD)
                .build();
    }

    @Test
    void blankMessage_returnsEmpty() {
        assertThat(augmentor.buildCitationBlock(" ")).isEmpty();
    }

    @Test
    void noHits_returnsEmpty() {
        when(retrieval.retrieve(any(RagRetrieveRequest.class)))
                .thenReturn(RagRetrieveResult.fallback("q", RagRetrieveScene.GENERAL, "EMPTY_RESULTS", 1));
        assertThat(augmentor.buildCitationBlock("Java 薪资")).isEmpty();
    }

    @Test
    void retrievalThrows_returnsEmpty_doesNotPropagate() {
        when(retrieval.retrieve(any(RagRetrieveRequest.class))).thenThrow(new RuntimeException("down"));
        assertThat(augmentor.buildCitationBlock("Java 薪资")).isEmpty();
    }

    @Test
    void hits_rendersNumberedCitationBlock() {
        RagRetrieveResult result = RagRetrieveResult.builder()
                .success(true)
                .query("字节 算法")
                .scene(RagRetrieveScene.GENERAL)
                .chunks(List.of(
                        chunk("JD@bytedance.md", "算法岗要求熟悉推荐系统"),
                        chunk("JD@meituan.md", "要求 3 年经验")
                ))
                .build();
        when(retrieval.retrieve(any(RagRetrieveRequest.class))).thenReturn(result);

        String block = augmentor.buildCitationBlock("字节 算法");

        assertThat(block).contains("参考资料");
        assertThat(block).contains("[1] JD@bytedance.md 算法岗要求熟悉推荐系统");
        assertThat(block).contains("[2] JD@meituan.md 要求 3 年经验");
        assertThat(block).contains("标注来源");
    }
}
