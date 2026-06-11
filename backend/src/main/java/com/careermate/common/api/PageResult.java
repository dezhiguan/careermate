package com.careermate.common.api;

import java.util.List;

public record PageResult<T>(
        long total,
        int page,
        int size,
        boolean hasResume,
        String sortStrategy,
        List<T> items
) {
    public static <T> PageResult<T> empty(int page, int size, boolean hasResume, String sortStrategy) {
        return new PageResult<>(0, page, size, hasResume, sortStrategy, List.of());
    }
}
