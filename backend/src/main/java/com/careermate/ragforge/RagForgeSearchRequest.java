package com.careermate.ragforge;

import java.util.List;

public record RagForgeSearchRequest(
    String query,
    List<Long> kbIds,
    String strategy,
    Integer topK,
    Integer rerankTopN,
    Double vectorWeight,
    Filter filter
) {
    public record Filter(List<String> chunkType) {}
}
