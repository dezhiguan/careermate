package com.careermate.agent.tool;

import com.careermate.common.exception.BizException;
import com.careermate.interview.service.InterviewPracticeService;
import com.careermate.interview.dto.InterviewSessionCreateRequest;
import com.careermate.interview.dto.InterviewQuestionResponse;
import com.careermate.interview.dto.InterviewSessionDetailResponse;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class CreateInterviewSessionTool implements AgentTool {

    /** 创建后几秒内视为「本次新建」，更早的说明是复用了已存在的未完成训练。 */
    private static final long REUSE_THRESHOLD_SECONDS = 30;

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
    public AgentToolDefinition definition() {
        return AgentToolDefinition.base(
                name(),
                "创建面试训练",
                description(),
                AgentToolDomain.INTERVIEW,
                AgentToolPermission.WRITE_USER_DATA,
                AgentToolRiskLevel.MEDIUM
        )
                .parameter(AgentToolDefinitionSupport.stringParam(
                        "title", false, "面试训练标题"))
                .example("帮我准备面试")
                .build();
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

            // 把第 1 题直接带回对话：此前只回一句「可到面试特训页查看 N 个问题」，
            // 用户被要求自行跳转到另一个页面才能开始，线上 95% 的训练因此一题未答。
            InterviewQuestionResponse first = firstPendingQuestion(session);

            // 这个工具是幂等的：已有未完成的训练就直接返回它，不新建。此前结果里看不出这点，
            // 模型一律说成「已成功创建」，用户点进去却是昨天那场没答完的——得让它知道是复用。
            boolean reused = session.getCreatedAt() != null
                    && session.getCreatedAt().isBefore(OffsetDateTime.now().minusSeconds(REUSE_THRESHOLD_SECONDS));

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("reused", reused);
            data.put("sessionId", session.getId());
            data.put("title", session.getTitle());
            data.put("totalQuestions", session.getTotalQuestions());
            data.put("answeredQuestions", session.getAnsweredQuestions());
            data.put("route", "/interview?session=" + session.getId());
            if (first != null) {
                data.put("firstQuestionId", first.getId());
                data.put("firstQuestionNo", first.getQuestionNo());
                data.put("firstQuestionType", first.getQuestionType());
                data.put("firstQuestionText", first.getQuestionText());
            }

            String head = reused
                    ? "你已有一场未完成的面试训练（这次是继续，没有新建）："
                    : "已新建面试训练：";
            String message = first == null
                    ? head + session.getTitle()
                    : head + session.getTitle() + "。先来第 1 题（共 "
                            + session.getTotalQuestions() + " 题）：\n\n" + first.getQuestionText()
                            + "\n\n直接在这里作答即可，答完我再给下一题。";
            return AgentToolResult.success(name(), message, data);
        } catch (BizException e) {
            return AgentToolResult.failure(name(), "创建面试训练失败", e.getMessage());
        }
    }

    /** 取第一道未作答的题；复用已有训练时可能已答过前几题，此时应从断点继续。 */
    private InterviewQuestionResponse firstPendingQuestion(InterviewSessionDetailResponse session) {
        if (session.getQuestions() == null || session.getQuestions().isEmpty()) {
            return null;
        }
        return session.getQuestions().stream()
                .filter(q -> !"ANSWERED".equalsIgnoreCase(q.getStatus()))
                .findFirst()
                .orElse(null);
    }
}
