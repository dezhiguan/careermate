package com.careermate.market;

import com.careermate.market.support.MarketExperience;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketExperienceTest {

    @Test
    void blankAndNullNormalizeToAny() {
        assertEquals("不限", MarketExperience.normalize(null));
        assertEquals("不限", MarketExperience.normalize(""));
        assertEquals("不限", MarketExperience.normalize("   "));
        assertEquals("不限", MarketExperience.normalize("不限"));
    }

    @Test
    void concreteRangeIsKept() {
        assertEquals("3-5年", MarketExperience.normalize(" 3-5年 "));
        assertFalse(MarketExperience.isAny("3-5年"));
        assertTrue(MarketExperience.isAny(null));
        assertTrue(MarketExperience.isAny("不限"));
    }

    @Test
    void describeMakesAnyExplicitForPrompt() {
        assertEquals("全经验段（不限工作年限）", MarketExperience.describe(null));
        assertEquals("全经验段（不限工作年限）", MarketExperience.describe("不限"));
        assertEquals("3-5年经验", MarketExperience.describe("3-5年"));
    }

    @Test
    void optionsLeadWithAny() {
        assertEquals("不限", MarketExperience.options().get(0));
        assertTrue(MarketExperience.options().contains("应届"));
    }
}
