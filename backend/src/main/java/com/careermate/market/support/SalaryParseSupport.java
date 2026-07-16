package com.careermate.market.support;

import java.util.Locale;

/**
 * 薪资字符串解析工具：将 "28K" / "3万" / "30000" 等归一为「元」，并做分位判断。
 *
 * <p>纯计算、无副作用，供 SalaryGuidanceService 生成谈判建议使用。
 */
public final class SalaryParseSupport {

    private SalaryParseSupport() {
    }

    /** 解析失败标记。 */
    public static final int UNPARSEABLE = -1;

    /**
     * 将薪资字符串解析为「元」。支持 K/k、万、W/w，以及纯数字。
     *
     * @param salary 如 "28K" / "3.5万" / "30000"
     * @return 元数值；无法解析返回 {@link #UNPARSEABLE}
     */
    public static int parseToYuan(String salary) {
        if (salary == null || salary.isBlank()) {
            return UNPARSEABLE;
        }
        String s = salary.trim().toUpperCase(Locale.ROOT);
        try {
            if (s.endsWith("K")) {
                return (int) (Double.parseDouble(s.substring(0, s.length() - 1)) * 1000);
            }
            if (s.endsWith("万") || s.endsWith("W")) {
                return (int) (Double.parseDouble(s.substring(0, s.length() - 1)) * 10000);
            }
            return (int) Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return UNPARSEABLE;
        }
    }

    /**
     * 从薪资范围取上限作为期望值：如 "30K-40K" → "40K"；单值原样返回。
     */
    public static String extractUpperBound(String range) {
        if (range == null || range.isBlank()) {
            return null;
        }
        String r = range.trim();
        // 兼容 - 与 ~ 两种分隔符
        String sep = r.contains("-") ? "-" : (r.contains("~") ? "~" : null);
        if (sep == null) {
            return r;
        }
        String[] parts = r.split(sep.equals("-") ? "-" : "~");
        return parts.length > 1 && !parts[1].isBlank() ? parts[1].trim() : r;
    }

    /**
     * 判断期望薪资落在哪个分位。任一值无法解析返回「未知」。
     *
     * @return "P25以下" / "P25-P50" / "P50-P75" / "P75以上" / "未知"
     */
    public static String quartileOf(String expected, String p25, String p50, String p75) {
        int exp = parseToYuan(expected);
        int q25 = parseToYuan(p25);
        int q50 = parseToYuan(p50);
        int q75 = parseToYuan(p75);
        if (exp < 0 || q25 < 0 || q50 < 0 || q75 < 0) {
            return "未知";
        }
        if (exp < q25) {
            return "P25以下";
        }
        if (exp < q50) {
            return "P25-P50";
        }
        if (exp < q75) {
            return "P50-P75";
        }
        return "P75以上";
    }

    /** 将「元」格式化为「K」表述：如 43200 → "43K"。 */
    public static String formatYuanToK(int yuan) {
        if (yuan <= 0) {
            return "暂无";
        }
        return Math.round(yuan / 1000.0) + "K";
    }
}
