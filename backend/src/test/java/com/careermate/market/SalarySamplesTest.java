package com.careermate.market;

import com.careermate.market.support.SalarySamples;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SalarySamplesTest {

    @Test
    void rangeIsTakenAsMidpointAndCountedOnce() {
        // 20-40K 记 30K 一条，不能既记 20K 又记 40K
        assertArrayEquals(new int[]{30_000}, SalarySamples.extractMonthlyYuan("岗位薪资 20-40K·13薪"));
    }

    @Test
    void supportsRangeSingleWanAndPlainNumber() {
        int[] samples = SalarySamples.extractMonthlyYuan("A 20-30K，B 18K，C 2.5万，D 45000");

        assertArrayEquals(new int[]{18_000, 25_000, 25_000, 45_000}, samples);
    }

    @Test
    void dropsNoiseOutsideReasonableMonthlyRange() {
        // 「13薪」「2026 年」「1000-9999人」不是月薪
        int[] samples = SalarySamples.extractMonthlyYuan("2026年 招聘 13薪 规模1000-9999人 薪资 25K");

        assertArrayEquals(new int[]{25_000}, samples);
    }

    @Test
    void returnsEmptyWhenNoSalaryInText() {
        assertEquals(0, SalarySamples.extractMonthlyYuan("岗位职责与任职要求，无薪资信息").length);
        assertEquals(0, SalarySamples.extractMonthlyYuan(null).length);
    }

    @Test
    void percentileInterpolatesBetweenRanks() {
        int[] sorted = {10_000, 20_000, 30_000, 40_000, 50_000};

        assertEquals(10_000, SalarySamples.percentile(sorted, 0));
        assertEquals(20_000, SalarySamples.percentile(sorted, 0.25));
        assertEquals(30_000, SalarySamples.percentile(sorted, 0.50));
        assertEquals(40_000, SalarySamples.percentile(sorted, 0.75));
        assertEquals(46_000, SalarySamples.percentile(sorted, 0.90));
    }

    @Test
    void percentileHandlesEmptyAndSingleSample() {
        assertEquals(-1, SalarySamples.percentile(new int[0], 0.5));
        assertEquals(25_000, SalarySamples.percentile(new int[]{25_000}, 0.9));
    }
}
