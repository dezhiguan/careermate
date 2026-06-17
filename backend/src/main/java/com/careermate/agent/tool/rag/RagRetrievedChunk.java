package com.careermate.agent.tool.rag;

import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.Map;

@Getter
@Builder
public class RagRetrievedChunk {

    private final String content;
    private final String contentPreview;
    private final String citation;
    private final Long chunkId;
    private final Long docId;
    private final String sourceId;
    private final String sourceTitle;
    private final String fileName;
    private final Double score;
    private final RagRetrieverChunkType chunkType;
    @Builder.Default
    private final Map<String, Object> metadata = Collections.emptyMap();
}
