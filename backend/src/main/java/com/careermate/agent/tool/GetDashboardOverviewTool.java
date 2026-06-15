package com.careermate.agent.tool;

import com.careermate.dashboard.service.DashboardService;
import com.careermate.dashboard.dto.DashboardOverviewResponse;
import com.careermate.dashboard.dto.DashboardSuggestionResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class GetDashboardOverviewTool implements AgentTool {

    private final DashboardService dashboardService;

    public GetDashboardOverviewTool(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @Override
    public String name() {
        return "get_dashboard_overview";
    }

    @Override
    public String description() {
        return "获取当前用户求职看板概览";
    }

    @Override
    public AgentToolDefinition definition() {
        return AgentToolDefinition.base(
                name(),
                "求职看板概览",
                description(),
                AgentToolDomain.DASHBOARD,
                AgentToolPermission.READ_USER_DATA,
                AgentToolRiskLevel.LOW
        )
                .example("看一下求职进展")
                .build();
    }

    @Override
    public boolean supports(AgentToolContext context) {
        return true;
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        DashboardOverviewResponse overview = dashboardService.getOverviewForUser(context.getUserId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("resumeCount", overview.getResumeStats().getTotalResumes());
        data.put("hasDefaultResume", overview.getResumeStats().isHasDefaultResume());
        data.put("jobMatchCount", overview.getJobMatchStats().getTotalMatches());
        data.put("interviewSessionCount", overview.getInterviewStats().getTotalSessions());
        data.put("suggestions", toSuggestionMaps(overview.getSuggestions()));

        String summary = String.format(
                "求职看板：简历 %d 份，岗位匹配 %d 条，面试训练 %d 次",
                overview.getResumeStats().getTotalResumes(),
                overview.getJobMatchStats().getTotalMatches(),
                overview.getInterviewStats().getTotalSessions()
        );
        return AgentToolResult.success(name(), summary, data);
    }

    private List<Map<String, String>> toSuggestionMaps(List<DashboardSuggestionResponse> suggestions) {
        List<Map<String, String>> list = new ArrayList<>();
        if (suggestions == null) {
            return list;
        }
        for (DashboardSuggestionResponse s : suggestions) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("type", s.getType());
            item.put("title", s.getTitle());
            item.put("description", s.getDescription());
            item.put("route", s.getRoute());
            list.add(item);
        }
        return list;
    }
}
