package com.careermate.ragforge;

public record RagForgeChunk(
    Long chunkId,
    Long docId,
    String filename,
    String content,
    String chunkType,
    Double finalScore
) {}
