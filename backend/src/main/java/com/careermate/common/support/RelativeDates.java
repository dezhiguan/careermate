package com.careermate.common.support;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 口语日期解析。
 *
 * <p>用户说的是「明天前整理面试复盘」「周五前补完 K8s」，不是 {@code 2026-08-15}。
 * 这段能力路由和工具都要用：路由负责把日期短语从标题里摘出来单独当参数，
 * 工具负责把它折算成具体日期——两边共用同一套规则，避免「路由认得、工具不认得」这类错位。
 */
public final class RelativeDates {

    /** 「今天/明天/后天/本周五/下周三/3天内/8月15日」等。用于从句首摘出日期短语。 */
    private static final Pattern PHRASE = Pattern.compile(
            "(今天|今日|明天|明日|后天|大后天|本月底|本月末|月底"
                    + "|\\d+\\s*天[内后]?"
                    + "|[下本这]?[周星期]期?[一二三四五六日天1-7]"
                    + "|\\d{4}-\\d{1,2}-\\d{1,2}"
                    + "|\\d{1,2}\\s*月\\s*\\d{1,2}\\s*[日号])"
                    + "\\s*(?:之?前|以前|前|之内|以内|内)?");

    private static final Pattern DAYS_WITHIN = Pattern.compile("(\\d+)\\s*天[内后]?");
    private static final Pattern WEEKDAY = Pattern.compile("[周星期]期?([一二三四五六日天1-7])");
    private static final Pattern MONTH_DAY = Pattern.compile("(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*[日号]");

    private static final Map<String, DayOfWeek> WEEKDAYS = Map.ofEntries(
            Map.entry("一", DayOfWeek.MONDAY), Map.entry("1", DayOfWeek.MONDAY),
            Map.entry("二", DayOfWeek.TUESDAY), Map.entry("2", DayOfWeek.TUESDAY),
            Map.entry("三", DayOfWeek.WEDNESDAY), Map.entry("3", DayOfWeek.WEDNESDAY),
            Map.entry("四", DayOfWeek.THURSDAY), Map.entry("4", DayOfWeek.THURSDAY),
            Map.entry("五", DayOfWeek.FRIDAY), Map.entry("5", DayOfWeek.FRIDAY),
            Map.entry("六", DayOfWeek.SATURDAY), Map.entry("6", DayOfWeek.SATURDAY),
            Map.entry("日", DayOfWeek.SUNDAY), Map.entry("天", DayOfWeek.SUNDAY),
            Map.entry("7", DayOfWeek.SUNDAY));

    private RelativeDates() {
    }

    /** 摘出来的日期短语与剩余文本。{@code phrase} 为 null 表示没找到日期。 */
    public record Split(String phrase, String remainder) {
    }

    /**
     * 从文本开头摘出日期短语。
     *
     * <p>只认句首：「明天前整理面试复盘」摘得出，「整理明天的面试复盘」不摘——后者的
     * 「明天」是内容的一部分，摘掉会把标题改错意思。
     */
    public static Split splitLeading(String text) {
        if (text == null || text.isBlank()) {
            return new Split(null, text);
        }
        Matcher m = PHRASE.matcher(text.trim());
        if (!m.lookingAt()) {
            return new Split(null, text.trim());
        }
        String phrase = m.group(1);
        String remainder = text.trim().substring(m.end()).trim();
        if (remainder.isEmpty()) {
            // 整句都是日期，摘掉就没标题了，宁可不摘
            return new Split(null, text.trim());
        }
        return new Split(phrase, remainder);
    }

    /**
     * 折算成具体日期。先按 ISO 解析，失败再走口语规则。
     *
     * @return 认不出返回 {@code null}，由调用方决定当作未填还是报错
     */
    public static LocalDate parse(String text, LocalDate today) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String raw = text.trim();
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException ignored) {
            // 继续走口语规则
        }
        // 只去空白：早先把「前/后/内」一并清掉，结果「后天」被削成「天」而认不出来
        String t = raw.replaceAll("\\s+", "");
        if (t.contains("今天") || t.contains("今日")) {
            return today;
        }
        if (t.contains("大后天")) {
            return today.plusDays(3);
        }
        if (t.contains("明天") || t.contains("明日")) {
            return today.plusDays(1);
        }
        if (t.contains("后天")) {
            return today.plusDays(2);
        }
        if (t.contains("本月") || t.contains("月底") || t.contains("月末")) {
            return today.withDayOfMonth(today.lengthOfMonth());
        }
        Matcher md = MONTH_DAY.matcher(raw);
        if (md.find()) {
            int month = Integer.parseInt(md.group(1));
            int day = Integer.parseInt(md.group(2));
            LocalDate candidate = LocalDate.of(today.getYear(), month, day);
            // 说「1月5日」而今天已是 12 月，指的是明年
            return candidate.isBefore(today) ? candidate.plusYears(1) : candidate;
        }
        Matcher days = DAYS_WITHIN.matcher(raw);
        if (days.find()) {
            return today.plusDays(Long.parseLong(days.group(1)));
        }
        Matcher wd = WEEKDAY.matcher(raw);
        if (!wd.find()) {
            return null;
        }
        DayOfWeek target = WEEKDAYS.get(wd.group(1));
        if (target == null) {
            return null;
        }
        // 「下周三」= 下一个自然周的周三：先跳到下周一再取该星期几。
        // 直接 plusWeeks(1) 再 nextOrSame 是错的——周四说「下周三」会算成再下周的周三。
        if (raw.contains("下周") || raw.contains("下星期")) {
            return today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                    .with(TemporalAdjusters.nextOrSame(target));
        }
        // 「周三/本周三」取从今天起最近的那一天
        return today.with(TemporalAdjusters.nextOrSame(target));
    }

    public static LocalDate parse(String text) {
        return parse(text, LocalDate.now());
    }
}
