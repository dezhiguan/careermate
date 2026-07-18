package com.careermate.opportunity.support;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 机会页可选城市目录。
 *
 * <p>当前实现（Phase 1）：后端维护的全国主要城市表，稳定可控、无运行时外部依赖。
 * 后续（Phase 2）可替换为对 JD 库的离线/定时聚合结果（「城市→岗位数」，只保留有岗位的城市），
 * 届时仅需替换 {@link #cities()} 的数据来源，调用方与接口契约不变。
 */
@Component
public class OpportunityCityCatalog {

    /** 「不限」——不过滤城市。 */
    public static final String ANY = "不限";

    /** 全国主要城市（按体量/招聘活跃度大致排序），首项为「不限」。 */
    private static final List<String> CITIES = List.of(
            ANY,
            "北京", "上海", "广州", "深圳", "杭州", "成都", "武汉", "南京", "西安",
            "苏州", "重庆", "天津", "长沙", "郑州", "青岛", "宁波", "东莞", "佛山",
            "合肥", "济南", "无锡", "厦门", "福州", "大连", "沈阳", "昆明", "南昌",
            "长春", "哈尔滨", "石家庄", "南宁", "贵阳", "太原", "珠海", "常州", "温州"
    );

    /** 可选城市列表（首项为「不限」）。 */
    public List<String> cities() {
        return CITIES;
    }

    /** 城市是否在目录内（用于校验默认城市是否可作为选中项）。 */
    public boolean contains(String city) {
        return city != null && CITIES.contains(city.trim());
    }
}
