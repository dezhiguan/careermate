package com.careermate.agent.tool;

import com.careermate.common.exception.BizException;
import com.careermate.jobmatch.JobMatchService;
import com.careermate.jobmatch.dto.JobMatchAnalyzeRequest;
import com.careermate.jobmatch.dto.JobMatchDetailResponse;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class CreateJobMatchTool implements AgentTool {

    private final JobMatchService jobMatchService;

    public CreateJobMatchTool(JobMatchService jobMatchService) {
        this.jobMatchService = jobMatchService;
    }

    @Override
    public String name() {
        return "create_job_match";
    }

    @Override
    public String description() {
        return "基于默认简历和用户输入中的岗位 JD 创建岗位匹配";
    }

    @Override
    public boolean supports(AgentToolContext context) {
        return true;
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        Map<String, Object> args = context.getArgs();
        String jobTitle = stringArg(args, "jobTitle", "用户输入岗位");
        String companyName = stringArg(args, "companyName", null);
        String jdContent = stringArg(args, "jdContent", context.getUserMessage());
        if (jdContent == null || jdContent.isBlank()) {
            return AgentToolResult.failure(name(), "创建岗位匹配失败", "缺少 JD 内容");
        }

        JobMatchAnalyzeRequest request = new JobMatchAnalyzeRequest();
        request.setJobTitle(jobTitle);
        request.setCompanyName(companyName);
        request.setJdContent(jdContent);

        try {
            JobMatchDetailResponse detail = jobMatchService.analyzeForUser(context.getUserId(), request);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("jobMatchId", detail.getId());
            data.put("jobTitle", detail.getJobTitle());
            data.put("matchScore", detail.getMatchScore());
            data.put("matchLevel", detail.getMatchLevel());
            data.put("matchedSkills", detail.getMatchedSkills());
            data.put("missingSkills", detail.getMissingSkills());
            data.put("analysisSummary", detail.getAnalysisSummary());
            return AgentToolResult.success(
                    name(),
                    "已创建岗位匹配：" + detail.getJobTitle() + "，匹配分 " + detail.getMatchScore(),
                    data
            );
        } catch (BizException e) {
            return AgentToolResult.failure(name(), "创建岗位匹配失败", e.getMessage());
        }
    }

    private String stringArg(Map<String, Object> args, String key, String defaultValue) {
        if (args == null || !args.containsKey(key)) {
            return defaultValue;
        }
        Object value = args.get(key);
        if (value == null) {
            return defaultValue;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? defaultValue : text;
    }
}
