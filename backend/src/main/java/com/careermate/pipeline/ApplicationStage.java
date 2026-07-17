package com.careermate.pipeline;

import java.util.Arrays;
import java.util.Locale;

/**
 * 投递阶段（看板列），贴合中国求职流程。顺序即看板列顺序。
 */
public enum ApplicationStage {

    PREPARING("准备投递", 1),
    INTERVIEW_SCHEDULED("约面", 2),
    INTERVIEWING("面试中", 3),
    OFFER("Offer谈薪", 4),
    CLOSED("已结束", 5);

    private final String label;
    private final int order;

    ApplicationStage(String label, int order) {
        this.label = label;
        this.order = order;
    }

    public String label() {
        return label;
    }

    public int order() {
        return order;
    }

    /** 宽松解析：忽略大小写；未知/空返回 null（由调用方决定默认或拒绝）。 */
    public static ApplicationStage fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(s -> s.name().equals(normalized))
                .findFirst()
                .orElse(null);
    }
}
