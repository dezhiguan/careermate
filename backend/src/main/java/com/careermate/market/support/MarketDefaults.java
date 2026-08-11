package com.careermate.market.support;

/**
 * 行情查询的默认口径——进页即以此查询一次，接口层缺参时也回落到此。
 */
public final class MarketDefaults {

    public static final String ROLE = "Java后端";
    public static final String CITY = "广州";
    public static final String YEARS = MarketExperience.ANY;

    private MarketDefaults() {
    }
}
