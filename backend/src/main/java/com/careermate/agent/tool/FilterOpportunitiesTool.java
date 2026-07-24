package com.careermate.agent.tool;

import com.careermate.opportunity.dto.OpportunityListItemVO;
import com.careermate.opportunity.dto.OpportunityListRequest;
import com.careermate.common.api.PageResult;
import com.careermate.opportunity.service.OpportunityService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * #P1-3 对话式筛选：把「只看广州的」「搜后端相关的机会」这类对话诉求，转成机会池的
 * city/keyword 过滤调用（对齐设计稿「极简筛选」——只支持城市与关键词，不做六维面板）。
 * 内联返回前若干条给对话卡渲染。
 */
@Component
public class FilterOpportunitiesTool implements AgentTool {

    private static final int MAX_ITEMS = 8;

    private final OpportunityService opportunityService;

    public FilterOpportunitiesTool(OpportunityService opportunityService) {
        this.opportunityService = opportunityService;
    }

    @Override
    public String name() {
        return "filter_opportunities";
    }

    @Override
    public String description() {
        return "【用户在对话里说「只看某城市的/搜某类岗位的机会」时用】按城市或关键词筛选机会池，返回匹配的机会清单。"
                + "对齐极简筛选：仅支持城市(city)与关键词(keyword)，不支持薪资/规模等多维过滤。";
    }

    @Override
    public AgentToolDefinition definition() {
        return AgentToolDefinition.base(
                name(),
                "筛选机会",
                description(),
                AgentToolDomain.JOB_MATCH,
                AgentToolPermission.READ_USER_DATA,
                AgentToolRiskLevel.LOW
        )
                .parameter(AgentToolDefinitionSupport.stringParam("city", false, "城市，如「广州」"))
                .parameter(AgentToolDefinitionSupport.stringParam("keyword", false, "公司或岗位关键词，如「后端」「字节」"))
                .example("只看广州的机会")
                .example("有没有后端相关的机会")
                .build();
    }

    @Override
    public boolean supports(AgentToolContext context) {
        return context.getUserId() != null;
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        Map<String, Object> args = context.getArgs() == null ? Map.of() : context.getArgs();
        String city = str(args, "city");
        String keyword = str(args, "keyword");
        OpportunityListRequest req = new OpportunityListRequest(keyword, city, null, null, 1, MAX_ITEMS);
        try {
            PageResult<OpportunityListItemVO> page = opportunityService.list(context.getUserId(), req);
            List<Map<String, Object>> items = new ArrayList<>();
            if (page != null && page.items() != null) {
                for (OpportunityListItemVO vo : page.items()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("jdId", vo.jdId());
                    m.put("company", vo.company());
                    m.put("title", vo.title());
                    m.put("city", vo.city());
                    m.put("salaryRange", vo.salaryRange());
                    m.put("matchScore", vo.matchScore());
                    items.add(m);
                }
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("total", page == null ? 0 : page.total());
            data.put("items", items);
            data.put("city", city);
            data.put("keyword", keyword);
            String scope = (city == null ? "" : city + " ") + (keyword == null ? "" : keyword);
            return AgentToolResult.success(name(),
                    "筛选到 " + items.size() + " 条机会" + (scope.isBlank() ? "" : "（" + scope.trim() + "）"), data);
        } catch (Exception e) {
            return AgentToolResult.failure(name(), "筛选机会失败", e.getMessage());
        }
    }

    private static String str(Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }
}
