package com.careermate.agent.tool;

import com.careermate.jobmatch.JobMatchJsonSupport;
import com.careermate.jobmatch.JobMatchService;
import com.careermate.model.entity.JobMatchEntity;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class GetLatestJobMatchTool implements AgentTool {

    private final JobMatchService jobMatchService;
    private final JobMatchJsonSupport jobMatchJsonSupport;

    public GetLatestJobMatchTool(JobMatchService jobMatchService, JobMatchJsonSupport jobMatchJsonSupport) {
        this.jobMatchService = jobMatchService;
        this.jobMatchJsonSupport = jobMatchJsonSupport;
    }

    @Override
    public String name() {
        return "get_latest_job_match";
    }

    @Override
    public String description() {
        return "获取当前用户最近一条岗位匹配结果";
    }

    @Override
    public boolean supports(AgentToolContext context) {
        return true;
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        Optional<JobMatchEntity> matchOpt = jobMatchService.getLatestActiveMatch(context.getUserId());
        if (matchOpt.isEmpty()) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("available", false);
            return AgentToolResult.success(name(), "当前用户暂无岗位匹配记录", data);
        }
        JobMatchEntity entity = matchOpt.get();
        List<String> matched = jobMatchJsonSupport.readStringList(entity.getMatchedSkills());
        List<String> missing = jobMatchJsonSupport.readStringList(entity.getMissingSkills());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("available", true);
        data.put("jobMatchId", entity.getId());
        data.put("jobTitle", entity.getJobTitle());
        data.put("companyName", entity.getCompanyName());
        data.put("matchScore", entity.getMatchScore());
        data.put("matchLevel", entity.getMatchLevel());
        data.put("matchedSkills", matched);
        data.put("missingSkills", missing);
        data.put("analysisSummary", entity.getAnalysisSummary());
        return AgentToolResult.success(
                name(),
                "已读取最近岗位匹配：" + entity.getJobTitle() + "，匹配分 " + entity.getMatchScore(),
                data
        );
    }
}
