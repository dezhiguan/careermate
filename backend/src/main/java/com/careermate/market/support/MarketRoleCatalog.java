package com.careermate.market.support;

import com.careermate.market.dto.MarketRoleGroupVO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * 全站可选岗位目录（薪资行情 / 行情页筛选 / 画像目标岗位 共用同一份）。
 *
 * <p>此前岗位清单在三处各写一份（资产页 7 项、行情页 7 项含重复、我的页 9 项），
 * 口径互相漂移，且都缺 AI 相关岗位。这里收敛为唯一来源，由 {@code GET /api/market/dimensions} 下发。
 *
 * <p>注意：接口层的 {@code role} 仍是自由文本（检索 query 直接用它），本目录只决定「下拉里能选什么」，
 * 不做白名单校验——用户画像里的自定义岗位仍可直接查询。
 */
@Component
public class MarketRoleCatalog {

    private static final List<MarketRoleGroupVO> GROUPS = List.of(
            new MarketRoleGroupVO("AI / 大模型", List.of(
                    "AI应用工程师",
                    "大模型算法工程师",
                    "AI Agent工程师",
                    "RAG工程师",
                    "Prompt工程师",
                    "AIGC应用开发",
                    "多模态算法工程师",
                    "大模型推理优化工程师",
                    "MLOps工程师",
                    "AI平台工程师",
                    "AI数据工程师",
                    "AI产品经理"
            )),
            new MarketRoleGroupVO("算法 / 机器学习", List.of(
                    "算法工程师",
                    "机器学习工程师",
                    "深度学习工程师",
                    "NLP算法工程师",
                    "计算机视觉工程师",
                    "推荐算法工程师",
                    "搜索算法工程师",
                    "语音算法工程师",
                    "风控算法工程师"
            )),
            new MarketRoleGroupVO("后端", List.of(
                    "Java后端",
                    "Go后端",
                    "Python后端",
                    "C++后端",
                    "Node.js后端",
                    "PHP后端",
                    ".NET后端",
                    "Rust后端",
                    "服务端架构师"
            )),
            new MarketRoleGroupVO("前端 / 客户端", List.of(
                    "前端开发",
                    "全栈工程师",
                    "Android开发",
                    "iOS开发",
                    "鸿蒙开发",
                    "小程序开发",
                    "Flutter开发"
            )),
            new MarketRoleGroupVO("数据", List.of(
                    "大数据工程师",
                    "数据开发工程师",
                    "数据仓库工程师",
                    "数据分析师",
                    "数据挖掘工程师",
                    "BI工程师"
            )),
            new MarketRoleGroupVO("测试 / 运维 / 安全", List.of(
                    "测试工程师",
                    "测试开发工程师",
                    "自动化测试工程师",
                    "运维工程师",
                    "SRE",
                    "DevOps工程师",
                    "安全工程师"
            )),
            new MarketRoleGroupVO("产品 / 项目", List.of(
                    "产品经理",
                    "技术产品经理",
                    "项目经理",
                    "解决方案架构师"
            ))
    );

    private static final List<String> FLAT = GROUPS.stream()
            .flatMap(g -> g.roles().stream())
            .toList();

    /** 分组岗位（前端 optgroup）。 */
    public List<MarketRoleGroupVO> groups() {
        return GROUPS;
    }

    /** 拉平的岗位列表（datalist / 校验用）。 */
    public List<String> roles() {
        return FLAT;
    }

    /** 岗位是否在目录内（大小写不敏感）。 */
    public boolean contains(String role) {
        if (role == null || role.isBlank()) {
            return false;
        }
        String target = role.trim().toLowerCase(Locale.ROOT);
        return FLAT.stream().anyMatch(r -> r.toLowerCase(Locale.ROOT).equals(target));
    }
}
