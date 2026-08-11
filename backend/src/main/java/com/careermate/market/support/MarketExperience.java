package com.careermate.market.support;

import java.util.List;

/**
 * 行情查询的「经验」维度口径。
 *
 * <p>此前「不限」只是前端下拉里的一个假选项：前端传 {@code undefined}，接口层默认值又把它补成
 * {@code 3-5年}，于是用户选「不限」实际查到的是 3-5 年区间，AI 摘要还会写成「3-5年经验」。
 * 这里把「不限」升级为一等口径：归一后全链路（检索 query、Prompt、缓存 key）都按「不限」处理。
 */
public final class MarketExperience {

    /** 「不限」——不带经验维度检索。 */
    public static final String ANY = "不限";

    /** 可选经验区间，首项为「不限」。 */
    private static final List<String> OPTIONS = List.of(
            ANY, "应届", "1-3年", "3-5年", "5-10年", "10年以上"
    );

    private MarketExperience() {
    }

    public static List<String> options() {
        return OPTIONS;
    }

    /** 归一：null / 空串 / 「不限」一律归为 {@link #ANY}，其余去空白后原样返回。 */
    public static String normalize(String years) {
        if (years == null || years.isBlank()) {
            return ANY;
        }
        String trimmed = years.trim();
        return ANY.equals(trimmed) ? ANY : trimmed;
    }

    public static boolean isAny(String years) {
        return ANY.equals(normalize(years));
    }

    /** Prompt 中的经验口径描述——「不限」时显式写清，避免 LLM 杜撰出具体年限区间。 */
    public static String describe(String years) {
        return isAny(years) ? "全经验段（不限工作年限）" : normalize(years) + "经验";
    }
}
