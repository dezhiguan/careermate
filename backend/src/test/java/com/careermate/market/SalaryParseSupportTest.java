package com.careermate.market;

import com.careermate.market.support.SalaryParseSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SalaryParseSupportTest {

    @Test
    void parseToYuanHandlesUnits() {
        assertEquals(28000, SalaryParseSupport.parseToYuan("28K"));
        assertEquals(30500, SalaryParseSupport.parseToYuan("30.5k"));
        assertEquals(300000, SalaryParseSupport.parseToYuan("30万"));
        assertEquals(35000, SalaryParseSupport.parseToYuan("3.5W"));
        assertEquals(30000, SalaryParseSupport.parseToYuan("30000"));
    }

    @Test
    void parseToYuanReturnsUnparseableOnBadInput() {
        assertEquals(SalaryParseSupport.UNPARSEABLE, SalaryParseSupport.parseToYuan(null));
        assertEquals(SalaryParseSupport.UNPARSEABLE, SalaryParseSupport.parseToYuan("  "));
        assertEquals(SalaryParseSupport.UNPARSEABLE, SalaryParseSupport.parseToYuan("暂无数据"));
        assertEquals(SalaryParseSupport.UNPARSEABLE, SalaryParseSupport.parseToYuan("abcK"));
    }

    @Test
    void extractUpperBoundTakesUpperOrSelf() {
        assertEquals("40K", SalaryParseSupport.extractUpperBound("30K-40K"));
        assertEquals("40K", SalaryParseSupport.extractUpperBound("30K~40K"));
        assertEquals("35K", SalaryParseSupport.extractUpperBound("35K"));
        assertEquals(null, SalaryParseSupport.extractUpperBound(null));
        assertEquals(null, SalaryParseSupport.extractUpperBound("  "));
    }

    @Test
    void quartileOfBuckets() {
        assertEquals("P25以下", SalaryParseSupport.quartileOf("18K", "22K", "30K", "42K"));
        assertEquals("P25-P50", SalaryParseSupport.quartileOf("26K", "22K", "30K", "42K"));
        assertEquals("P50-P75", SalaryParseSupport.quartileOf("40K", "22K", "30K", "42K"));
        assertEquals("P75以上", SalaryParseSupport.quartileOf("60K", "22K", "30K", "42K"));
        assertEquals("未知", SalaryParseSupport.quartileOf("暂无", "22K", "30K", "42K"));
    }

    @Test
    void formatYuanToK() {
        assertEquals("43K", SalaryParseSupport.formatYuanToK(43200));
        assertEquals("暂无", SalaryParseSupport.formatYuanToK(0));
        assertEquals("暂无", SalaryParseSupport.formatYuanToK(-1));
    }
}
