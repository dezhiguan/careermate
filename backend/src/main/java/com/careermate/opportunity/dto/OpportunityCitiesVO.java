package com.careermate.opportunity.dto;

import java.util.List;

/**
 * 机会页城市筛选选项。
 *
 * @param cities      可选城市列表（首项为「不限」）
 * @param defaultCity 进页默认选中的城市（取自用户画像目标城市；无则为「不限」）
 */
public record OpportunityCitiesVO(
        List<String> cities,
        String defaultCity
) {}
