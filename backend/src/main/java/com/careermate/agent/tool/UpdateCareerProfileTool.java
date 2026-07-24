package com.careermate.agent.tool;

import com.careermate.profile.dto.CareerProfileUpsertRequest;
import com.careermate.profile.service.CareerProfileService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * #P1-2 对话改画像：把「我期望涨到 35k / 想去广州 / 只接受远程」这类对话诉求，
 * 落成结构化求职画像字段（而非仅进长期记忆）。CareerProfileService.upsertProfile
 * 是 merge 语义（只改传入的非空字段），因此这里按需部分更新、不动其余字段。
 */
@Component
public class UpdateCareerProfileTool implements AgentTool {

    private final CareerProfileService careerProfileService;

    public UpdateCareerProfileTool(CareerProfileService careerProfileService) {
        this.careerProfileService = careerProfileService;
    }

    @Override
    public String name() {
        return "update_career_profile";
    }

    @Override
    public String description() {
        return "【用户在对话里表达求职偏好/期望变化时用】把期望薪资、目标城市、目标岗位、工作模式（远程/驻场）、"
                + "资历等更新进结构化求职画像。只更新用户明确提到的字段，其余保留。"
                + "如「期望涨到35k」→targetSalaryRange，「想去广州」→targetCity，「只接受远程」→workMode。";
    }

    @Override
    public AgentToolDefinition definition() {
        return AgentToolDefinition.base(
                name(),
                "更新求职画像",
                description(),
                AgentToolDomain.WORKSPACE,
                AgentToolPermission.WRITE_USER_DATA,
                AgentToolRiskLevel.MEDIUM
        )
                .parameter(AgentToolDefinitionSupport.stringParam("targetSalaryRange", false, "期望薪资，如「30-45k」「35k」"))
                .parameter(AgentToolDefinitionSupport.stringParam("targetCity", false, "目标城市，如「广州」"))
                .parameter(AgentToolDefinitionSupport.stringParam("targetRole", false, "目标岗位，如「后端开发工程师」"))
                .parameter(AgentToolDefinitionSupport.stringParam("workMode", false, "工作模式，如「远程」「驻场」「混合」"))
                .parameter(AgentToolDefinitionSupport.stringParam("seniority", false, "资历，如「高级」「资深」"))
                .example("我期望涨到 35k")
                .example("以后只看广州的岗位")
                .build();
    }

    @Override
    public boolean supports(AgentToolContext context) {
        return context.getUserId() != null;
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        Map<String, Object> args = context.getArgs();
        if (args == null || args.isEmpty()) {
            return AgentToolResult.failure(name(), "更新画像失败", "未提供任何可更新字段");
        }
        CareerProfileUpsertRequest req = new CareerProfileUpsertRequest();
        List<String> changed = new ArrayList<>();
        String salary = str(args, "targetSalaryRange");
        String city = str(args, "targetCity");
        String role = str(args, "targetRole");
        String mode = str(args, "workMode");
        String seniority = str(args, "seniority");
        if (salary != null) { req.setTargetSalaryRange(salary); changed.add("期望薪资=" + salary); }
        if (city != null) { req.setTargetCity(city); changed.add("目标城市=" + city); }
        if (role != null) { req.setTargetRole(role); changed.add("目标岗位=" + role); }
        if (mode != null) { req.setWorkMode(mode); changed.add("工作模式=" + mode); }
        if (seniority != null) { req.setSeniority(seniority); changed.add("资历=" + seniority); }
        if (changed.isEmpty()) {
            return AgentToolResult.failure(name(), "更新画像失败", "未识别到可更新的画像字段");
        }
        try {
            careerProfileService.upsertProfile(context.getUserId(), req, "agent");
        } catch (Exception e) {
            return AgentToolResult.failure(name(), "更新画像失败", e.getMessage());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("updatedFields", changed);
        return AgentToolResult.success(name(), "已更新求职画像：" + String.join("，", changed), data);
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
