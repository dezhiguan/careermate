package com.careermate.ragforge;

public record RagForgeChunk(
    Long chunkId,
    Long docId,
    String filename,
    String content,
    String chunkType,
    Double finalScore,
    Double vectorScore
) {
    /** 兼容旧调用（不带 vectorScore）：vectorScore 置空。 */
    public RagForgeChunk(Long chunkId, Long docId, String filename, String content,
                         String chunkType, Double finalScore) {
        this(chunkId, docId, filename, content, chunkType, finalScore, null);
    }
}
