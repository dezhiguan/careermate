package com.careermate.market.support;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从检索原文中抽取真实薪资样本，并做分位计算。
 *
 * <p>分位数是纯算术，本来就不该问 LLM——问了既慢又可能编。语料里的薪资写法是
 * {@code 40-50K·14薪} / {@code 20-23K} / {@code 30000} / {@code 25万}，
 * 抽出来直接算 P25/P50/P75/P90 比模型估的可靠得多。
 *
 * <p>区间取中位代表该条招聘的典型月薪（{@code 20-40K} → 30K）：用两个端点会让同一条
 * 招聘在样本里计两次，把分布拉宽。{@code ·14薪} 是年终倍数，不参与月薪分位。
 */
public final class SalarySamples {

    /** 低于此值视为解析噪声（如把「13薪」读成薪资）。 */
    private static final int MIN_REASONABLE_YUAN = 3_000;
    /** 高于此值视为解析噪声（如把年薪或员工数读成月薪）。 */
    private static final int MAX_REASONABLE_YUAN = 200_000;

    /** {@code 20-40K} / {@code 20~40k} / {@code 20 - 40 K}，也匹配「万」。 */
    private static final Pattern RANGE = Pattern.compile(
            "(\\d{1,3}(?:\\.\\d+)?)\\s*[-~－—到]\\s*(\\d{1,3}(?:\\.\\d+)?)\\s*([KkWw万])");
    /** 单值 {@code 25K} / {@code 3.5万}。 */
    private static final Pattern SINGLE = Pattern.compile(
            "(?<![\\d.])(\\d{1,3}(?:\\.\\d+)?)\\s*([KkWw万])");
    /** 裸数字月薪 {@code 30000}（5-6 位，避免命中年份与人数）。 */
    private static final Pattern PLAIN = Pattern.compile("(?<![\\d.])(\\d{5,6})(?![\\d.])");

    private SalarySamples() {
    }

    /**
     * 抽取月薪样本（单位：元），已排序。
     *
     * <p>抽取顺序有意为之：先吃掉区间写法并从原文里抹掉，剩下的文本再找单值，
     * 否则 {@code 20-40K} 会被单值规则重复计成 20K 与 40K 两条。
     */
    public static int[] extractMonthlyYuan(String context) {
        if (context == null || context.isBlank()) {
            return new int[0];
        }
        List<Integer> samples = new ArrayList<>();
        StringBuilder rest = new StringBuilder();
        Matcher range = RANGE.matcher(context);
        int last = 0;
        while (range.find()) {
            rest.append(context, last, range.start()).append(' ');
            last = range.end();
            int low = toYuan(range.group(1), range.group(3));
            int high = toYuan(range.group(2), range.group(3));
            if (low > 0 && high > 0 && high >= low) {
                add(samples, (low + high) / 2);
            }
        }
        rest.append(context.substring(last));

        String remainder = rest.toString();
        Matcher single = SINGLE.matcher(remainder);
        while (single.find()) {
            add(samples, toYuan(single.group(1), single.group(2)));
        }
        Matcher plain = PLAIN.matcher(remainder);
        while (plain.find()) {
            add(samples, Integer.parseInt(plain.group(1)));
        }

        int[] sorted = samples.stream().mapToInt(Integer::intValue).toArray();
        Arrays.sort(sorted);
        return sorted;
    }

    /**
     * 线性插值分位数。
     *
     * @param sorted 已排序样本（元）
     * @param p      分位，0-1
     * @return 分位值（元）；样本为空返回 {@link SalaryParseSupport#UNPARSEABLE}
     */
    public static int percentile(int[] sorted, double p) {
        if (sorted == null || sorted.length == 0) {
            return SalaryParseSupport.UNPARSEABLE;
        }
        if (sorted.length == 1) {
            return sorted[0];
        }
        double pos = p * (sorted.length - 1);
        int lower = (int) Math.floor(pos);
        int upper = (int) Math.ceil(pos);
        if (lower == upper) {
            return sorted[lower];
        }
        double weight = pos - lower;
        return (int) Math.round(sorted[lower] * (1 - weight) + sorted[upper] * weight);
    }

    private static void add(List<Integer> samples, int yuan) {
        if (yuan >= MIN_REASONABLE_YUAN && yuan <= MAX_REASONABLE_YUAN) {
            samples.add(yuan);
        }
    }

    private static int toYuan(String number, String unit) {
        try {
            double value = Double.parseDouble(number);
            return switch (unit) {
                case "K", "k" -> (int) (value * 1_000);
                case "W", "w", "万" -> (int) (value * 10_000);
                default -> SalaryParseSupport.UNPARSEABLE;
            };
        } catch (NumberFormatException e) {
            return SalaryParseSupport.UNPARSEABLE;
        }
    }
}
