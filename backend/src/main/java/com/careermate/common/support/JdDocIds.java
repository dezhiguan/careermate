package com.careermate.common.support;

/**
 * JD 文档 id 的两种形态互转。
 *
 * <p>机会相关接口对外一律返回 {@code "doc-89840"} 这种带前缀的字符串（{@code jdId}），
 * 而收藏、投递、面试出题这几个接口的入参是裸数字 {@code Long}。调用方把上一个接口的返回值
 * 原样传给下一个接口是最自然的用法，此前会直接 400。这里统一归一，两种形态都接受。
 */
public final class JdDocIds {

    private static final String PREFIX = "doc-";

    private JdDocIds() {
    }

    /**
     * 归一成数字 id。接受 {@code "doc-89840"}、{@code "89840"}、{@code 89840}。
     *
     * @return 解析结果；入参为空或不是合法 id 时返回 {@code null}，由调用方决定报什么错
     */
    public static Long parse(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        if (text.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
            text = text.substring(PREFIX.length()).trim();
        }
        try {
            return Long.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 数字 id → 对外展示形态。 */
    public static String format(Long docId) {
        return docId == null ? null : PREFIX + docId;
    }
}
