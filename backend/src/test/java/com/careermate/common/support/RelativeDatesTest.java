package com.careermate.common.support;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 口语日期解析与摘取。
 *
 * <p>用户说的是「明天前整理面试复盘」，不是 2026-08-15。此前整句都进了任务标题、
 * dueDate 一直是空的；更早还因为 LocalDate.parse("周五") 抛异常，让整个建任务动作失败。
 */
class RelativeDatesTest {

    /** 2026-08-13 是星期四，用固定日期避免用例随运行日漂移。 */
    private static final LocalDate THU = LocalDate.of(2026, 8, 13);

    @Test
    void 解析常见口语日期() {
        assertEquals(THU, RelativeDates.parse("今天", THU));
        assertEquals(THU.plusDays(1), RelativeDates.parse("明天", THU));
        assertEquals(THU.plusDays(2), RelativeDates.parse("后天", THU));
        assertEquals(THU.plusDays(3), RelativeDates.parse("大后天", THU));
        assertEquals(THU.plusDays(3), RelativeDates.parse("3天内", THU));
        assertEquals(LocalDate.of(2026, 8, 31), RelativeDates.parse("本月底", THU));
        assertEquals(LocalDate.of(2026, 8, 14), RelativeDates.parse("周五", THU), "本周五");
        assertEquals(LocalDate.of(2026, 8, 19), RelativeDates.parse("下周三", THU));
        assertEquals(LocalDate.of(2026, 8, 20), RelativeDates.parse("8月20日", THU));
    }

    @Test
    void ISO日期照常解析() {
        assertEquals(LocalDate.of(2026, 8, 15), RelativeDates.parse("2026-08-15", THU));
    }

    @Test
    void 认不出返回null而不是抛异常() {
        // 抛异常会让整个建任务动作失败；截止日期本是选填项，认不出就当没填
        assertNull(RelativeDates.parse("等我想好了", THU));
        assertNull(RelativeDates.parse("", THU));
        assertNull(RelativeDates.parse(null, THU));
    }

    @Test
    void 从句首摘出日期短语并留下标题() {
        var s = RelativeDates.splitLeading("明天前整理面试复盘");
        assertEquals("明天", s.phrase());
        assertEquals("整理面试复盘", s.remainder());

        var s2 = RelativeDates.splitLeading("下周三前复习 JVM 调优");
        assertEquals("下周三", s2.phrase());
        assertEquals("复习 JVM 调优", s2.remainder());
    }

    @Test
    void 只认句首以免改错标题() {
        // 「整理明天的面试复盘」里的「明天」是内容的一部分，摘掉会让标题变味
        var s = RelativeDates.splitLeading("整理明天的面试复盘");
        assertNull(s.phrase());
        assertEquals("整理明天的面试复盘", s.remainder());
    }

    @Test
    void 整句都是日期时不摘() {
        var s = RelativeDates.splitLeading("明天");
        assertNull(s.phrase(), "摘掉就没标题了");
    }
}
