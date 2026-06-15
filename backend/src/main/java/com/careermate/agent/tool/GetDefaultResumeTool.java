package com.careermate.agent.tool;

import com.careermate.model.entity.ResumeEntity;
import com.careermate.resume.service.ResumeService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class GetDefaultResumeTool implements AgentTool {

    private static final int PREVIEW_MAX = 500;

    private final ResumeService resumeService;

    public GetDefaultResumeTool(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    @Override
    public String name() {
        return "get_default_resume";
    }

    @Override
    public String description() {
        return "获取当前用户默认简历摘要";
    }

    @Override
    public AgentToolDefinition definition() {
        return AgentToolDefinition.base(
                name(),
                "默认简历",
                description(),
                AgentToolDomain.RESUME,
                AgentToolPermission.READ_USER_DATA,
                AgentToolRiskLevel.LOW
        )
                .example("帮我分析默认简历")
                .build();
    }

    @Override
    public boolean supports(AgentToolContext context) {
        return true;
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        Optional<ResumeEntity> resumeOpt = resumeService.getDefaultActiveResume(context.getUserId());
        if (resumeOpt.isEmpty()) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("available", false);
            return AgentToolResult.success(name(), "当前用户暂无默认简历", data);
        }
        ResumeEntity resume = resumeOpt.get();
        String content = resume.getContent() == null ? "" : resume.getContent();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("available", true);
        data.put("resumeId", resume.getId());
        data.put("title", resume.getTitle());
        data.put("contentPreview", preview(content));
        data.put("contentLength", content.length());
        return AgentToolResult.success(name(), "已读取默认简历：" + resume.getTitle(), data);
    }

    private String preview(String content) {
        if (content.length() <= PREVIEW_MAX) {
            return content;
        }
        return content.substring(0, PREVIEW_MAX);
    }
}
