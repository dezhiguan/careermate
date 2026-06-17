package com.careermate.agent.tool.rag;

import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

@Getter
@Builder
public class RagRetrieveResult {

    private final boolean success;
    private final String query;
    private final RagRetrieveScene scene;
    @Builder.Default
    private final List<RagRetrievedChunk> chunks = Collections.emptyList();
    private final boolean fallbackUsed;
    private final String errorCode;
    private final long latencyMs;

    public static RagRetrieveResult fallback(
            String query,
            RagRetrieveScene scene,
            String errorCode,
            long latencyMs
    ) {
        return RagRetrieveResult.builder()
                .success(false)
                .query(query)
                .scene(scene)
                .chunks(List.of())
                .fallbackUsed(true)
                .errorCode(errorCode)
                .latencyMs(latencyMs)
                .build();
    }
}
