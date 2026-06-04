package com.careermate.observability.ragforge;

import lombok.Builder;
import lombok.Data;

import java.util.Collections;
import java.util.List;

@Data
@Builder
public class RagForgeSearchResult {

    private boolean success;
    private int resultCount;
    private long latencyMs;
    private List<String> documentIds;

    public static RagForgeSearchResult disabled() {
        return RagForgeSearchResult.builder()
                .success(true)
                .resultCount(0)
                .latencyMs(0L)
                .documentIds(Collections.emptyList())
                .build();
    }
}
