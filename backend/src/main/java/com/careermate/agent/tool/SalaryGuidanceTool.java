package com.careermate.agent.tool;

import com.careermate.market.dto.SalaryGuidanceVO;
import com.careermate.market.service.SalaryGuidanceService;
import com.careermate.market.support.MarketDefaults;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Agent 工具：结合市场薪资分位与用户期望，给出谈判建议。
 *
 * <p>让小职在对话主线里被「这个岗位给多少合理」意图召唤。
 */
@Component
public class SalaryGuidanceTool implements AgentTool {

    private static final String DEFAULT_ROLE = MarketDefaults.ROLE;
    private static final String DEFAULT_CITY = MarketDefaults.CITY;
    // 未指明经验时按「不限」查，不臆造一个年限区间
    private static final String DEFAULT_YEARS = MarketDefaults.YEARS;

    private final SalaryGuidanceService salaryGuidanceService;

    public SalaryGuidanceTool(SalaryGuidanceService salaryGuidanceService) {
        this.salaryGuidanceService = salaryGuidanceService;
    }

    @Override
    public String name() {
        return "get_salary_guidance";
    }

    @Override
    public String description() {
        return "结合市场薪资分位(P25/P50/P75)与用户期望薪资，给出谈判锚点与建议";
    }

    @Override
    public AgentToolDefinition definition() {
        return AgentToolDefinition.base(
                        name(),
                        "薪酬谈判建议",
                        description(),
                        AgentToolDomain.KNOWLEDGE,
                        AgentToolPermission.READ_USER_DATA,
                        AgentToolRiskLevel.LOW
                )
                .parameter(AgentToolDefinitionSupport.stringParam("role", false, "岗位，如「Java后端」"))
                .parameter(AgentToolDefinitionSupport.stringParam("city", false, "城市，如「广州」"))
                .parameter(AgentToolDefinitionSupport.stringParam("years", false, "经验年限，如「3-5年」"))
                .example("这个岗位给到多少合理")
                .example("我期望的薪资在市场什么水平")
                .build();
    }

    @Override
    public boolean supports(AgentToolContext context) {
        return true;
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        Map<String, Object> args = context.getArgs();
        String role = stringArg(args, "role", DEFAULT_ROLE);
        String city = stringArg(args, "city", DEFAULT_CITY);
        String years = stringArg(args, "years", DEFAULT_YEARS);

        SalaryGuidanceVO vo = salaryGuidanceService.getSalaryGuidance(context.getUserId(), role, city, years);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("dataAvailable", vo.isDataAvailable());
        data.put("p25", vo.getP25());
        data.put("p50", vo.getP50());
        data.put("p75", vo.getP75());
        data.put("userExpectation", vo.getUserExpectation());
        data.put("quartile", vo.getQuartile());
        data.put("anchorPoint", vo.getAnchorPoint());
        data.put("negotiationAdvice", vo.getNegotiationAdvice());
        data.put("citations", vo.getCitations());

        String summary = vo.isDataAvailable()
                ? "薪酬建议：" + vo.getNegotiationAdvice()
                : "暂无市场薪资数据，无法给出谈判建议";
        return AgentToolResult.success(name(), summary, data);
    }

    private static String stringArg(Map<String, Object> args, String key, String defaultValue) {
        if (args == null || !args.containsKey(key) || args.get(key) == null) {
            return defaultValue;
        }
        String value = String.valueOf(args.get(key)).trim();
        return value.isEmpty() ? defaultValue : value;
    }
}
