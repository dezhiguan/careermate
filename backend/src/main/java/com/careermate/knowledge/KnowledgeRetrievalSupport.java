package com.careermate.knowledge;

import com.careermate.agent.tool.rag.RagRetrieveScene;
import com.careermate.agent.tool.rag.RagRetrievedChunk;
import com.careermate.agent.tool.rag.RagRetrieverChunkType;
import com.careermate.ragforge.RagForgeChunk;

import java.util.Collection;

public final class KnowledgeRetrievalSupport {

    static final int DEFAULT_PREVIEW_CHARS = 120;
    static final int RESUME_PREVIEW_CHARS = 80;
    static final int DEFAULT_CONTEXT_CHARS = 4000;

    private KnowledgeRetrievalSupport() {
    }

    public static String buildContentPreview(String content, RagRetrieveScene scene) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String normalized = content.replace('\n', ' ').trim();
        int limit = scene == RagRetrieveScene.RESUME ? RESUME_PREVIEW_CHARS : DEFAULT_PREVIEW_CHARS;
        if (normalized.length() <= limit) {
            return normalized;
        }
        return normalized.substring(0, limit) + "...";
    }

    public static String buildCitation(String fileName, Long docId, RagRetrieverChunkType chunkType) {
        String source = fileName != null && !fileName.isBlank()
                ? fileName
                : (docId != null ? "doc-" + docId : "source");
        String type = chunkType == null ? "GENERAL" : chunkType.name();
        return type + "@" + source;
    }

    public static String joinChunkContents(Collection<RagRetrievedChunk> chunks, int maxChars) {
        if (chunks == null || chunks.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (RagRetrievedChunk chunk : chunks) {
            if (chunk == null || chunk.getContent() == null || chunk.getContent().isBlank()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append(chunk.getContent().trim());
            if (sb.length() >= maxChars) {
                return sb.substring(0, maxChars);
            }
        }
        return sb.toString();
    }

    public static RagForgeChunk toRagForgeChunk(RagRetrievedChunk chunk) {
        if (chunk == null) {
            return null;
        }
        return new RagForgeChunk(
                chunk.getChunkId(),
                chunk.getDocId(),
                chunk.getFileName(),
                chunk.getContent(),
                chunk.getChunkType() == null ? null : chunk.getChunkType().name(),
                chunk.getScore()
        );
    }
}
