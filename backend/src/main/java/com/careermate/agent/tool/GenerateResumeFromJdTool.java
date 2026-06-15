package com.careermate.agent.tool;

import com.careermate.agent.sse.SseEmitterService;
import com.careermate.agent.sse.SseEventType;
import com.careermate.model.entity.AgentSessionEntity;
import com.careermate.resume.version.workflow.GenerateResumeFromJdWorkflow;
import com.careermate.workspace.support.WorkspaceSessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
public class GenerateResumeFromJdTool implements AgentTool {

    private final GenerateResumeFromJdWorkflow generateResumeFromJdWorkflow;
    private final WorkspaceSessionRepository workspaceSessionRepository;
    private final SseEmitterService sseEmitterService;

    public GenerateResumeFromJdTool(
            GenerateResumeFromJdWorkflow generateResumeFromJdWorkflow,
            WorkspaceSessionRepository workspaceSessionRepository,
            SseEmitterService sseEmitterService
    ) {
        this.generateResumeFromJdWorkflow = generateResumeFromJdWorkflow;
        this.workspaceSessionRepository = workspaceSessionRepository;
        this.sseEmitterService = sseEmitterService;
    }

    @Override
    public String name() {
        return "generate_resume_from_jd";
    }

    @Override
    public String description() {
        return "根据目标 JD 为用户生成定制简历 Markdown 版本；生成完成后可点击对话卡片「下载 PDF」导出 PDF";
    }

    @Override
    public AgentToolDefinition definition() {
        return AgentToolDefinition.base(
                name(),
                "按 JD 生成简历",
                description(),
                AgentToolDomain.RESUME,
                AgentToolPermission.LONG_RUNNING_TASK,
                AgentToolRiskLevel.HIGH
        )
                .parameter(AgentToolDefinitionSupport.stringParam(
                        "jdId", false, "目标 JD 文档 ID"))
                .example("按当前 JD 生成定制简历")
                .build();
    }

    @Override
    public boolean supports(AgentToolContext context) {
        if (context.getSessionId() == null || context.getSessionId().isBlank()) {
            return false;
        }
        try {
            AgentSessionEntity session = workspaceSessionRepository.requireSession(
                    context.getUserId(), context.getSessionId()
            );
            return WorkspaceSessionRepository.WORKSPACE_JD_PREP.equals(session.getWorkspaceType());
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        String sessionId = context.getSessionId();
        String jdId = context.getArgs() != null && context.getArgs().get("jdId") != null
                ? String.valueOf(context.getArgs().get("jdId"))
                : null;
        try {
            AgentSessionEntity session = workspaceSessionRepository.requireSession(
                    context.getUserId(), sessionId
            );
            String targetJdId = jdId != null && !jdId.isBlank() ? jdId : session.getJdId();
            Map<String, Object> card = generateResumeFromJdWorkflow.generateAndReturnCard(
                    context.getUserId(), sessionId, targetJdId);
            if (card != null) {
                try {
                    sseEmitterService.send(sessionId, SseEventType.UI_ACTION, Map.of("card", card));
                } catch (Exception ignored) {
                    // 推送失败不影响工具结果，前端重载历史消息时仍能看到卡片
                }
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("sessionId", sessionId);
            data.put("jdId", targetJdId);
            data.put("sseEndpoint", "/api/workspace/" + sessionId + "/generate-resume/stream");
            return AgentToolResult.success(name(), "已触发按 JD 生成简历", data);
        } catch (Exception e) {
            log.warn("generate_resume_from_jd tool failed: {}", e.getMessage());
            return AgentToolResult.failure(name(), "简历生成失败", e.getMessage());
        }
    }
}
