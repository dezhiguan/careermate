package com.careermate.common.api;

public record CacheMeta(
        State state,
        Long cachedAt
) {
    public enum State {
        FRESH,
        LOADING,
        DEGRADED,
        EMPTY
    }

    public static CacheMeta fresh() {
        return new CacheMeta(State.FRESH, System.currentTimeMillis());
    }

    public static CacheMeta loading() {
        return new CacheMeta(State.LOADING, null);
    }

    public static CacheMeta degraded() {
        return new CacheMeta(State.DEGRADED, null);
    }

    public static CacheMeta empty() {
        return new CacheMeta(State.EMPTY, System.currentTimeMillis());
    }
}
