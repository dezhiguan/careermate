package com.careermate.common.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record PageResult<T>(
        long total,
        int page,
        int size,
        boolean hasResume,
        String sortStrategy,
        List<T> items,
        @JsonProperty("_meta") CacheMeta meta
) {
    public PageResult(
            long total,
            int page,
            int size,
            boolean hasResume,
            String sortStrategy,
            List<T> items
    ) {
        this(total, page, size, hasResume, sortStrategy, items, CacheMeta.fresh());
    }

    public PageResult<T> withMeta(CacheMeta meta) {
        return new PageResult<>(total, page, size, hasResume, sortStrategy, items, meta);
    }

    public static <T> PageResult<T> empty(int page, int size, boolean hasResume, String sortStrategy) {
        return new PageResult<>(0, page, size, hasResume, sortStrategy, List.of());
    }

    public static <T> PageResult<T> degradedEmpty(int page, int size, boolean hasResume, String sortStrategy) {
        return new PageResult<>(0, page, size, hasResume, sortStrategy, List.of(), CacheMeta.loading());
    }
}
