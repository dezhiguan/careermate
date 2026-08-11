package com.careermate.market.dto;

import java.util.List;

/**
 * 行情查询维度字典——岗位 / 城市 / 经验的唯一来源，供资产页薪资行情、行情页筛选、画像编辑共用。
 *
 * @param roleGroups   分组岗位（含 AI / 大模型分组）
 * @param cities       可选城市，首项为「不限」
 * @param years        可选经验区间，首项为「不限」
 * @param defaultRole  默认岗位
 * @param defaultCity  默认城市
 * @param defaultYears 默认经验
 */
public record MarketDimensionsVO(
        List<MarketRoleGroupVO> roleGroups,
        List<String> cities,
        List<String> years,
        String defaultRole,
        String defaultCity,
        String defaultYears
) {
}
