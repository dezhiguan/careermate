package com.careermate.agent.tool;

import com.careermate.common.exception.BizException;
import com.careermate.interview.InterviewPracticeService;
import com.careermate.interview.dto.InterviewSessionCreateRequest;
import com.careermate.interview.dto.InterviewSessionDetailResponse;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class CreateInterviewSessionTool implements AgentTool {

    private final InterviewPracticeService interviewPracticeService;

    public CreateInterviewSessionTool(InterviewPracticeService interviewPracticeService) {
        this.interviewPracticeService = interviewPracticeService;
    }

    @Override
    public String name() {
        return "create_interview_session";
    }

    @Override
    public String description() {
        return "基于默认简历和最近岗位匹配创建面试训练";
    }

    @Override
    public boolean supports(AgentToolContext context) {
        return true;
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        InterviewSessionCreateRequest request = new InterviewSessionCreateRequest();
        Map<String, Object> args = context.getArgs();
        if (args != null && args.get("title") != null) {
            request.setTitle(String.valueOf(args.get("title")));
        }

        try {
            InterviewSessionDetailResponse session =
                    interviewPracticeService.createSessionForUser(context.getUserId(), request);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("sessionId", session.getId());
            data.put("title", session.getTitle());
            data.put("totalQuestions", session.getTotalQuestions());
            data.put("route", "/interview");
            return AgentToolResult.success(
                    name(),
                    "已创建面试训练：" + session.getTitle() + "，共 " + session.getTotalQuestions() + " 题",
                    data
            );
        } catch (BizException e) {
            return AgentToolResult.failure(name(), "创建面试训练失败", e.getMessage());
        }
    }
}
