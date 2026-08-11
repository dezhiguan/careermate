package com.careermate.market.dto;

import java.util.List;

/**
 * 岗位分组（前端按 optgroup 渲染）。
 *
 * @param group 分组名，如「AI / 大模型」
 * @param roles 该分组下的岗位
 */
public record MarketRoleGroupVO(
        String group,
        List<String> roles
) {
}
