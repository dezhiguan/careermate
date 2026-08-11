package com.careermate.market.support;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 词项在检索原文中的出现统计——RAG 结论的「原文支撑」判定基础。
 *
 * <p>LLM 会凭常识补全出看起来合理、但并不来自检索语料的结论（典型案例：薪资行情库里
 * 根本没有技术栈，模型照样输出 Java/Spring Boot/MySQL）。这类编造无法从结果本身看出来，
 * 只能拿原文做确定性校验：结论里的词项若在检索到的文本中一次都没出现，即判定为无支撑。
 *
 * <p>纯 ASCII 词项加词边界，避免 {@code Java} 命中 {@code JavaScript}；中文词项按子串计数。
 */
public final class TermMentions {

    /** 纯 ASCII 词项（含空格与 . + # _ / - 等常见符号）。 */
    private static final Pattern ASCII_TERM = Pattern.compile("[a-z0-9 .+#_/-]+");

    private TermMentions() {
    }

    /** 把文本转成统计用的小写形态。传 null 得到空串。 */
    public static String haystack(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }

    /**
     * 统计 {@code term} 在 {@code haystack} 中的出现次数。
     *
     * @param haystack 已由 {@link #haystack(String)} 转小写的文本
     * @param term     待统计词项（大小写不敏感）
     */
    public static int count(String haystack, String term) {
        if (haystack == null || haystack.isEmpty() || term == null || term.isBlank()) {
            return 0;
        }
        String needle = term.trim().toLowerCase(Locale.ROOT);
        if (ASCII_TERM.matcher(needle).matches()) {
            Matcher matcher = Pattern
                    .compile("(?<![a-z0-9])" + Pattern.quote(needle) + "(?![a-z0-9])")
                    .matcher(haystack);
            int count = 0;
            while (matcher.find()) {
                count++;
            }
            return count;
        }
        int count = 0;
        int from = 0;
        while (true) {
            int idx = haystack.indexOf(needle, from);
            if (idx < 0) {
                return count;
            }
            count++;
            from = idx + needle.length();
        }
    }

    /** 词项是否在文本中出现过。 */
    public static boolean appearsIn(String haystack, String term) {
        return count(haystack, term) > 0;
    }
}
