package com.careermate.opportunity.cache;

public final class OpportunityCacheKeys {

    private static final String LIST_PREFIX = "opp:list:";
    private static final String DETAIL_PREFIX = "opp:detail:";

    private OpportunityCacheKeys() {
    }

    public static String listKey(Long userId, String queryHash) {
        return LIST_PREFIX + userId + ":" + queryHash;
    }

    public static String detailKey(Long userId, Long docId) {
        return DETAIL_PREFIX + userId + ":" + docId;
    }
}
