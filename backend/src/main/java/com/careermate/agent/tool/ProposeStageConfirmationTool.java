package com.careermate.agent.tool;

import com.careermate.pipeline.ApplicationStage;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Agent 工具（投递看板 Layer-2 一键确认）：小职对<b>平台外发生、自己观测不到</b>的事件
 * （HR 在 BOSS 约面 / 二面通过等）主动发一张确认卡，让用户<b>单击确认</b>后再流转阶段。
 *
 * <p>与 {@code advance_application_stage}（Layer-1 对话推断，直接流转）分工：本工具<b>不改状态</b>，
 * 只产出一张 STAGE_CONFIRM 卡；真正的流转由用户点「确认」后经 /api/pipeline/confirm-stage 落库。
 */
@Component
public class ProposeStageConfirmationTool implements AgentTool {

    @Override
    public String name() {
        return "propose_stage_confirmation";
    }

    @Override
    public String description() {
        return "对平台外发生、你观测不到的事件（HR 约面/二面通过等）发一张一键确认卡，"
                + "让用户单击确认后再流转投递阶段；本工具不直接改状态";
    }

    @Override
    public AgentToolDefinition definition() {
        return AgentToolDefinition.base(
                        name(),
                        "投递阶段确认卡",
                        description(),
                        AgentToolDomain.DASHBOARD,
                        AgentToolPermission.READ_USER_DATA,
                        AgentToolRiskLevel.LOW
                )
                .parameter(AgentToolDefinitionSupport.stringParam(
                        "jdDocId", true, "目标 JD 的文档 ID"))
                .parameter(AgentToolDefinitionSupport.stringParam(
                        "targetStage", true, "拟推进到的阶段：PREPARING/INTERVIEW_SCHEDULED/INTERVIEWING/OFFER/CLOSED"))
                .parameter(AgentToolDefinitionSupport.stringParam(
                        "company", false, "公司名（用于卡片文案）"))
                .parameter(AgentToolDefinitionSupport.stringParam(
                        "question", false, "确认问法，如「字节二面过了吗？」；不填则自动生成"))
                .example("字节二面过了吗？过了我帮你挪到面试中")
                .build();
    }

    @Override
    public boolean supports(AgentToolContext context) {
        return true;
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        Map<String, Object> args = context.getArgs();
        Long jdDocId = parseLong(args, "jdDocId");
        String targetStage = stringArg(args, "targetStage");
        if (jdDocId == null) {
            return AgentToolResult.failure(name(), "发起阶段确认失败", "缺少 jdDocId");
        }
        ApplicationStage stage = ApplicationStage.fromCode(targetStage);
        if (stage == null) {
            return AgentToolResult.failure(name(), "发起阶段确认失败", "非法 targetStage：" + targetStage);
        }

        String company = stringArg(args, "company");
        String stageLabel = stage.label();
        String who = company != null ? company : "这条机会";
        String question = stringArg(args, "question");
        if (question == null) {
            question = "「" + who + "」要推进到「" + stageLabel + "」吗？";
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("jdDocId", jdDocId);
        data.put("targetStage", stage.name());
        data.put("stageLabel", stageLabel);
        data.put("company", company);
        data.put("question", question);

        return AgentToolResult.success(name(), question, data);
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
