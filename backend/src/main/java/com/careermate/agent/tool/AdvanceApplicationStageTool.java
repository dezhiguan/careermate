package com.careermate.agent.tool;

import com.careermate.pipeline.dto.ApplicationVO;
import com.careermate.pipeline.service.PipelineService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Agent 工具：按对话推断把某条 JD 的投递阶段流转到目标列（对话驱动看板）。
 *
 * <p>让小职在「我约到面试了 / 拿到 offer 了」等意图下，把对应 JD 移动到正确阶段；
 * 若该 JD 尚无投递记录则按目标阶段自动建一条，无需用户先手动建卡。
 */
@Component
public class AdvanceApplicationStageTool implements AgentTool {

    private final PipelineService pipelineService;

    public AdvanceApplicationStageTool(PipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    @Override
    public String name() {
        return "advance_application_stage";
    }

    @Override
    public String description() {
        return "把某条 JD 的投递阶段流转到目标列（PREPARING准备投递/INTERVIEW_SCHEDULED约面/"
                + "INTERVIEWING面试中/OFFER谈薪/CLOSED已结束）；该 JD 无记录时自动建卡";
    }

    @Override
    public AgentToolDefinition definition() {
        return AgentToolDefinition.base(
                        name(),
                        "流转投递阶段",
                        description(),
                        AgentToolDomain.DASHBOARD,
                        AgentToolPermission.WRITE_USER_DATA,
                        AgentToolRiskLevel.LOW
                )
                .parameter(AgentToolDefinitionSupport.stringParam(
                        "jdDocId", true, "目标 JD 的文档 ID"))
                .parameter(AgentToolDefinitionSupport.stringParam(
                        "stage", true, "目标阶段：PREPARING/INTERVIEW_SCHEDULED/INTERVIEWING/OFFER/CLOSED"))
                .parameter(AgentToolDefinitionSupport.stringParam(
                        "company", false, "公司名（新建卡时用）"))
                .parameter(AgentToolDefinitionSupport.stringParam(
                        "roleTitle", false, "岗位名（新建卡时用）"))
                .example("我约到这家的面试了")
                .example("这个岗位我拿到 offer 了")
                .build();
    }

    @Override
    public boolean supports(AgentToolContext context) {
        return true;
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        if (context.getUserId() == null) {
            return AgentToolResult.failure(name(), "流转阶段失败", "未认证");
        }
        Map<String, Object> args = context.getArgs();
        Long jdDocId = parseLong(args, "jdDocId");
        String stage = stringArg(args, "stage");
        if (jdDocId == null) {
            return AgentToolResult.failure(name(), "流转阶段失败", "缺少 jdDocId");
        }
        if (stage == null) {
            return AgentToolResult.failure(name(), "流转阶段失败", "缺少 stage");
        }

        try {
            ApplicationVO vo = pipelineService.updateStageByJd(
                    context.getUserId(), jdDocId, stage,
                    stringArg(args, "company"), stringArg(args, "roleTitle"));

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("applicationId", vo.getId());
            data.put("jdDocId", vo.getJdDocId());
            data.put("company", vo.getCompany());
            data.put("roleTitle", vo.getRoleTitle());
            data.put("stage", vo.getStage());
            data.put("stageLabel", vo.getStageLabel());

            String who = vo.getCompany() != null ? vo.getCompany() : "该机会";
            return AgentToolResult.success(name(),
                    "已把「" + who + "」移动到「" + vo.getStageLabel() + "」", data);
        } catch (Exception e) {
            return AgentToolResult.failure(name(), "流转阶段失败", e.getMessage());
        }
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
