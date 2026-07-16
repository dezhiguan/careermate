package com.careermate.agent.tool;

import com.careermate.company.dto.CompanyAtmosphereVO;
import com.careermate.company.service.CompanyAtmosphereService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Agent 工具：查询某公司的「氛围」（工作强度/团队口碑/面试风格/加班信号）。
 *
 * <p>让小职在对话主线里被「这家公司加班严重吗」等意图召唤，依据目标公司知识库作答，无据明说。
 */
@Component
public class CompanyAtmosphereTool implements AgentTool {

    private final CompanyAtmosphereService companyAtmosphereService;

    public CompanyAtmosphereTool(CompanyAtmosphereService companyAtmosphereService) {
        this.companyAtmosphereService = companyAtmosphereService;
    }

    @Override
    public String name() {
        return "get_company_atmosphere";
    }

    @Override
    public String description() {
        return "查询目标公司的氛围（工作强度、团队口碑、面试风格、加班信号），依据知识库作答，无据则明说";
    }

    @Override
    public AgentToolDefinition definition() {
        return AgentToolDefinition.base(
                        name(),
                        "公司氛围",
                        description(),
                        AgentToolDomain.KNOWLEDGE,
                        AgentToolPermission.CALL_EXTERNAL_SERVICE,
                        AgentToolRiskLevel.LOW
                )
                .parameter(AgentToolDefinitionSupport.stringParam(
                        "company", false, "公司名，如「字节跳动」（与 jdDocId 二选一）"))
                .parameter(AgentToolDefinitionSupport.stringParam(
                        "jdDocId", false, "目标 JD 的文档 ID，自动解析公司名（与 company 二选一）"))
                .example("字节这家公司加班严重吗")
                .example("看看这家公司的氛围")
                .build();
    }

    @Override
    public boolean supports(AgentToolContext context) {
        return true;
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        Map<String, Object> args = context.getArgs();
        String company = stringArg(args, "company");
        Long jdDocId = parseLong(args, "jdDocId");
        if (company == null && jdDocId == null) {
            return AgentToolResult.failure(name(), "查询公司氛围失败", "需提供 company 或 jdDocId");
        }

        CompanyAtmosphereVO vo = company != null
                ? companyAtmosphereService.getCompanyAtmosphere(company)
                : companyAtmosphereService.getCompanyAtmosphereByJd(jdDocId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("companyName", vo.getCompanyName());
        data.put("dataAvailable", vo.isDataAvailable());
        data.put("workIntensity", vo.getWorkIntensity());
        data.put("teamReputation", vo.getTeamReputation());
        data.put("interviewStyle", vo.getInterviewStyle());
        data.put("overtimeSignal", vo.getOvertimeSignal());
        data.put("cultureTags", vo.getCultureTags());
        data.put("aiSummary", vo.getAiSummary());
        data.put("citations", vo.getCitations());

        String summary = vo.isDataAvailable()
                ? "已获取「" + vo.getCompanyName() + "」的公司氛围"
                : "暂无「" + vo.getCompanyName() + "」的氛围情报";
        return AgentToolResult.success(name(), summary, data);
    }

    private static String stringArg(Map<String, Object> args, String key) {
        if (args == null || !args.containsKey(key) || args.get(key) == null) {
            return null;
        }
        String value = String.valueOf(args.get(key)).trim();
        return value.isEmpty() ? null : value;
    }

    /** 容错解析 JD 文档 id：支持「doc-89840」「89840」或数字，取其中的数字部分。 */
    private static Long parseLong(Map<String, Object> args, String key) {
        Object raw = args == null ? null : args.get(key);
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.longValue();
        }
        String digits = String.valueOf(raw).trim().replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
