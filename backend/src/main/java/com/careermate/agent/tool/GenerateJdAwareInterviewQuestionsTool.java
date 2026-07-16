package com.careermate.agent.tool;

import com.careermate.interview.dto.JdAwareQuestionsVO;
import com.careermate.interview.service.InterviewQuestionService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Agent 工具：根据指定 JD + 用户简历，生成针对性面试题。
 *
 * <p>让小职在对话主线里被「这个岗位面试会问什么」意图召唤，围绕当前 JD 出题。
 */
@Component
public class GenerateJdAwareInterviewQuestionsTool implements AgentTool {

    private final InterviewQuestionService interviewQuestionService;

    public GenerateJdAwareInterviewQuestionsTool(InterviewQuestionService interviewQuestionService) {
        this.interviewQuestionService = interviewQuestionService;
    }

    @Override
    public String name() {
        return "generate_jd_aware_questions";
    }

    @Override
    public String description() {
        return "根据指定 JD 和用户简历，从面试题库生成针对这条 JD 的面试题（每题带 JD_FOCUSED/HIT_RESUME/WEAK_POINT 标签）";
    }

    @Override
    public AgentToolDefinition definition() {
        return AgentToolDefinition.base(
                        name(),
                        "JD 针对性面试题",
                        description(),
                        AgentToolDomain.INTERVIEW,
                        AgentToolPermission.READ_USER_DATA,
                        AgentToolRiskLevel.LOW
                )
                .parameter(AgentToolDefinitionSupport.stringParam(
                        "jdDocId", true, "目标 JD 的文档 ID（RAGForge docId）"))
                .parameter(AgentToolDefinitionSupport.stringParam(
                        "company", false, "公司名（可选，用于结合该公司面经出题）"))
                .example("这个岗位面试会问什么")
                .example("根据这条 JD 给我出几道面试题")
                .build();
    }

    @Override
    public boolean supports(AgentToolContext context) {
        return true;
    }

    @Override
    public AgentToolResult execute(AgentToolContext context) {
        Map<String, Object> args = context.getArgs();
        Long jdDocId = parseLong(args, "jdDocId");
        if (jdDocId == null) {
            return AgentToolResult.failure(name(), "生成面试题失败", "缺少有效的 jdDocId 参数");
        }
        String company = stringArg(args, "company");

        JdAwareQuestionsVO vo = interviewQuestionService.generateJdAwareQuestions(
                jdDocId, context.getUserId(), company);

        int count = vo.getQuestions() == null ? 0 : vo.getQuestions().size();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("jdDocId", vo.getJdDocId());
        data.put("jdTitle", vo.getJdTitle());
        data.put("dataAvailable", vo.isDataAvailable());
        data.put("questionCount", count);
        data.put("questions", vo.getQuestions());
        data.put("aiSummary", vo.getAiSummary());

        String summary = vo.isDataAvailable()
                ? "已为「" + vo.getJdTitle() + "」生成 " + count + " 道面试题"
                : "未找到该岗位 JD，暂无法生成面试题";
        return AgentToolResult.success(name(), summary, data);
    }

    private static String stringArg(Map<String, Object> args, String key) {
        if (args == null || !args.containsKey(key) || args.get(key) == null) {
            return null;
        }
        String value = String.valueOf(args.get(key)).trim();
        return value.isEmpty() ? null : value;
    }

    private static Long parseLong(Map<String, Object> args, String key) {
        if (args == null || !args.containsKey(key) || args.get(key) == null) {
            return null;
        }
        try {
            String value = String.valueOf(args.get(key)).trim();
            return value.isEmpty() ? null : Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
