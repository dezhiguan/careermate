package com.careermate.agent.tool;

import com.careermate.agent.sse.SseEmitterService;
import com.careermate.agent.sse.SseEventType;
import com.careermate.model.entity.AgentSessionEntity;
import com.careermate.resume.version.dto.ResumeVersionListItemVO;
import com.careermate.resume.version.dto.ResumeVersionVO;
import com.careermate.resume.version.service.ResumeModifyService;
import com.careermate.resume.version.service.ResumeVersionService;
import com.careermate.workspace.support.WorkspaceSessionRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * #6「说一句即改」对话工具：在当前 JD 线最新简历版本上做最小改动，落新版本并推卡片刷新 Canvas。
 * 与 generate_resume_from_jd（从 JD 全量重生成）互补——已有简历、只想微调时用它。
 */
@Slf4j
@Component
public class ModifyResumeVersionTool implements AgentTool {

    private final WorkspaceSessionRepository workspaceSessionRepository;
    private final ResumeVersionService resumeVersionService;
    private final ResumeModifyService resumeModifyService;
    private final SseEmitterService sseEmitterService;

    public ModifyResumeVersionTool(WorkspaceSessionRepository workspaceSessionRepository,
                                   ResumeVersionService resumeVersionService,
                                   ResumeModifyService resumeModifyService,
                                   SseEmitterService sseEmitterService) {
        this.workspaceSessionRepository = workspaceSessionRepository;
        this.resumeVersionService = resumeVersionService;
        this.resumeModifyService = resumeModifyService;
        this.sseEmitterService = sseEmitterService;
    }

    @Override
    public String name() {
        return "modify_resume";
    }

    @Override
    public String description() {
        return "在已生成的简历版本上做最小改动（加一段/删掉某条/改数字/调措辞），只改需要改的、保留其余；"
                + "当用户已经生成过简历、只想微调某处时用它，而不是重新整份生成";
    }

    @Override
    public AgentToolDefinition definition() {
        return AgentToolDefinition.base(
                name(),
                "说一句即改简历",
                description(),
                AgentToolDomain.RESUME,
                AgentToolPermission.LONG_RUNNING_TASK,
                AgentToolRiskLevel.MEDIUM
        )
                .parameter(AgentToolDefinitionSupport.stringParam(
                        "instruction", true, "用户想做的具体改动，如「把期望薪资改成 200k」「加一段开源项目经历」「删掉前端那条」"))
                .example("把期望薪资改成 30-45k")
                .example("加一段开源贡献经历")
                .build();
    }

    @Override
    public boolean supports(AgentToolContext context) {
        if (context.getSessionId() == null || context.getSessionId().isBlank()) {
            return false;
        }
        try {
            AgentSessionEntity session = workspaceSessionRepository.requireSession(
                    context.getUserId(), context.getSessionId());
            if (!WorkspaceSessionRepository.WORKSPACE_JD_PREP.equals(session.getWorkspaceType())) {
                return false;
            }
            // 仅当已有至少一个简历版本时可改
            return !resumeVersionService.listBySession(context.getUserId(), context.getSessionId()).isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        String sessionId = context.getSessionId();
        String instruction = context.getArgs() != null && context.getArgs().get("instruction") != null
                ? String.valueOf(context.getArgs().get("instruction"))
                : null;
        try {
            List<ResumeVersionListItemVO> versions =
                    resumeVersionService.listBySession(context.getUserId(), sessionId);
            if (versions.isEmpty()) {
                return AgentToolResult.failure(name(), "还没有可修改的简历版本，请先生成一份", null);
            }
            String latestVersionId = versions.get(0).versionId();
            ResumeVersionVO modified = resumeModifyService.modify(
                    context.getUserId(), latestVersionId, instruction);

            String preview = modified.contentMarkdown() == null ? ""
                    : modified.contentMarkdown().substring(0, Math.min(400, modified.contentMarkdown().length()));
            Map<String, Object> card = buildResumeCard(modified.versionId(), modified.versionName(), preview);
            try {
                sseEmitterService.send(sessionId, SseEventType.UI_ACTION, Map.of("card", card));
            } catch (Exception ignored) {
                // 推送失败不影响结果，前端重载历史仍能看到卡片
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("sessionId", sessionId);
            data.put("versionId", modified.versionId());
            data.put("changeSummary", modified.changeSummary());
            return AgentToolResult.success(name(), "已按你的要求改好并生成新版本：" + modified.versionName(), data);
        } catch (Exception e) {
            log.warn("modify_resume tool failed: {}", e.getMessage());
            return AgentToolResult.failure(name(), "简历修改失败", e.getMessage());
        }
    }

    /** 与生成完成卡片同构（RESUME_GENERATED），前端点「查看完整简历」即刷新 Canvas 到新版本。 */
    private static Map<String, Object> buildResumeCard(String versionId, String versionName, String preview) {
        Map<String, Object> pdfPayload = new LinkedHashMap<>();
        pdfPayload.put("versionId", versionId);
        pdfPayload.put("versionName", versionName);
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("type", "RESUME_GENERATED");
        card.put("title", "简历已按你的要求更新");
        card.put("versionId", versionId);
        card.put("versionName", versionName);
        card.put("previewMarkdown", preview);
        card.put("actions", List.of(
                Map.of("label", "查看完整简历", "action", "VIEW_RESUME", "payload", versionId),
                Map.of("label", "复制 Markdown", "action", "COPY_MARKDOWN", "payload", versionId),
                Map.of("label", "下载 PDF", "action", "DOWNLOAD_PDF", "payload", pdfPayload),
                Map.of("label", "下载 Word", "action", "DOWNLOAD_WORD", "payload", pdfPayload)
        ));
        return card;
    }
}
