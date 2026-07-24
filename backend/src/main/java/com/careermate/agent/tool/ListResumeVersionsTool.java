package com.careermate.agent.tool;

import com.careermate.model.entity.AgentSessionEntity;
import com.careermate.resume.version.dto.ResumeVersionListItemVO;
import com.careermate.resume.version.service.ResumeVersionService;
import com.careermate.workspace.support.WorkspaceSessionRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * #P1-4 对话调出简历版本：用户说「我给这家那份简历」「看看之前几版」时，列出当前 JD 线下
 * 已有的定制简历版本（seq/契合分/改动摘要/时间），供对话内联调出与回溯。
 */
@Component
public class ListResumeVersionsTool implements AgentTool {

    private final WorkspaceSessionRepository workspaceSessionRepository;
    private final ResumeVersionService resumeVersionService;

    public ListResumeVersionsTool(WorkspaceSessionRepository workspaceSessionRepository,
                                  ResumeVersionService resumeVersionService) {
        this.workspaceSessionRepository = workspaceSessionRepository;
        this.resumeVersionService = resumeVersionService;
    }

    @Override
    public String name() {
        return "list_resume_versions";
    }

    @Override
    public String description() {
        return "【用户想查看/调出这条 JD 线已定制的简历版本时用】列出当前 JD 线下的所有简历版本"
                + "（版本序号、契合分、改动摘要、时间），最新在前。用于「我给这家那份简历」「看看上一版」等。";
    }

    @Override
    public AgentToolDefinition definition() {
        return AgentToolDefinition.base(
                name(),
                "简历版本清单",
                description(),
                AgentToolDomain.RESUME,
                AgentToolPermission.READ_USER_DATA,
                AgentToolRiskLevel.LOW
        )
                .example("我给这家投的那份简历呢")
                .example("这条线有几个简历版本")
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
            return WorkspaceSessionRepository.WORKSPACE_JD_PREP.equals(session.getWorkspaceType());
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        try {
            List<ResumeVersionListItemVO> versions =
                    resumeVersionService.listBySession(context.getUserId(), context.getSessionId());
            List<Map<String, Object>> items = new ArrayList<>();
            for (ResumeVersionListItemVO v : versions) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("versionId", v.versionId());
                m.put("versionName", v.versionName());
                m.put("versionSeq", v.versionSeq());
                m.put("aiScore", v.aiScore());
                m.put("changeSummary", v.changeSummary());
                m.put("createdAt", v.createdAt() == null ? null : v.createdAt().toString());
                items.add(m);
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("count", items.size());
            data.put("versions", items);
            String msg = items.isEmpty()
                    ? "这条 JD 线还没有定制简历版本"
                    : "这条 JD 线共有 " + items.size() + " 个简历版本";
            return AgentToolResult.success(name(), msg, data);
        } catch (Exception e) {
            return AgentToolResult.failure(name(), "读取简历版本失败", e.getMessage());
        }
    }
}
