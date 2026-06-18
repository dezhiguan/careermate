package com.careermate.common.api;

public record CacheMeta(
        boolean degraded,
        Long cachedAt
) {
    public static CacheMeta freshNow() {
        return new CacheMeta(false, System.currentTimeMillis());
    }

    public static CacheMeta degradedNow() {
        return new CacheMeta(true, null);
    }
}
