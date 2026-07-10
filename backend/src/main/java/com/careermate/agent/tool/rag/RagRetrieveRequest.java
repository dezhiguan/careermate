package com.careermate.agent.tool.rag;

import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Getter
@Builder
public class RagRetrieveRequest {

    private final String query;
    private final RagRetrieveScene scene;
    private final int topK;
    @Builder.Default
    private final Map<String, Object> filters = Collections.emptyMap();
    /** 可选：按文档 id 精确检索（岗位详情/分析走 /search + docIds 过滤，绕开对 API-Key 不开放的 chunks 端点）。 */
    private final List<Long> docIds;

    public List<Long> docIdFilters() {
        return docIds == null ? List.of() : docIds;
    }

    public List<String> chunkTypeFilters() {
        if (filters == null || filters.isEmpty()) {
            return List.of();
        }
        Object raw = filters.get("chunkTypes");
        if (raw instanceof List<?> list) {
            return list.stream()
                    .map(String::valueOf)
                    .filter(s -> !s.isBlank())
                    .toList();
        }
        return List.of();
    }
}
