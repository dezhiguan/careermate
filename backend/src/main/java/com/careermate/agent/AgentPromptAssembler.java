package com.careermate.agent;

import com.careermate.agent.context.CareerProfileContextResult;
import com.careermate.agent.context.ConversationContextResult;
import com.careermate.agent.tool.AgentToolResult;
import com.careermate.jobmatch.JobMatchContext;
import com.careermate.resume.ResumeContext;

import java.util.Map;

public final class AgentPromptAssembler {

    private static final String BASE_SYSTEM_PROMPT = """
            你是 CareerMate 求职 Agent。
            如果提供了「用户默认简历」，回答简历优化、岗位匹配、面试准备问题时必须优先参考该简历。
            如果提供了「最近岗位匹配结果」，回答岗位差距、简历优化、面试准备问题时必须结合该匹配结果。
            不要编造简历和岗位匹配中不存在的信息。
            如果缺少默认简历或岗位匹配记录，请提示用户先创建默认简历或先做岗位匹配。
            """;

    private AgentPromptAssembler() {
    }

    public static String buildBaseSystemPrompt() {
        return BASE_SYSTEM_PROMPT.trim();
    }

    public static String buildSystemPrompt(ResumeContext resumeContext, JobMatchContext jobMatchContext) {
        String prompt = buildBaseSystemPrompt();
        prompt = appendResumeContext(prompt, resumeContext);
        return appendJobMatchContext(prompt, jobMatchContext);
    }

    public static String appendCareerProfileContext(String systemPrompt, CareerProfileContextResult careerProfileContext) {
        if (careerProfileContext == null
                || !careerProfileContext.isAvailable()
                || careerProfileContext.getContextText() == null
                || careerProfileContext.getContextText().isBlank()) {
            return systemPrompt;
        }
        StringBuilder sb = new StringBuilder(systemPrompt == null ? "" : systemPrompt);
        sb.append("\n\n").append(careerProfileContext.getContextText().trim());
        return sb.toString();
    }

    public static String appendResumeContext(String systemPrompt, ResumeContext resumeContext) {
        if (resumeContext == null || resumeContext.getContextText() == null || resumeContext.getContextText().isBlank()) {
            return systemPrompt;
        }
        StringBuilder sb = new StringBuilder(systemPrompt == null ? "" : systemPrompt);
        sb.append("\n\n").append(resumeContext.getContextText().trim());
        return sb.toString();
    }

    public static String appendJobMatchContext(String systemPrompt, JobMatchContext jobMatchContext) {
        if (jobMatchContext == null || jobMatchContext.getContextText() == null || jobMatchContext.getContextText().isBlank()) {
            return systemPrompt;
        }
        StringBuilder sb = new StringBuilder(systemPrompt == null ? "" : systemPrompt);
        sb.append("\n\n").append(jobMatchContext.getContextText().trim());
        return sb.toString();
    }

    public static String appendConversationContext(String systemPrompt, ConversationContextResult conversationContext) {
        if (conversationContext == null
                || !conversationContext.isAvailable()
                || conversationContext.getContextText() == null
                || conversationContext.getContextText().isBlank()) {
            return systemPrompt;
        }
        StringBuilder sb = new StringBuilder(systemPrompt == null ? "" : systemPrompt);
        sb.append("\n\n").append(conversationContext.getContextText().trim());
        return sb.toString();
    }

    public static String appendToolResult(String systemPrompt, AgentToolResult toolResult) {
        if (toolResult == null) {
            return systemPrompt;
        }
        StringBuilder sb = new StringBuilder(systemPrompt == null ? "" : systemPrompt);
        sb.append("\n\n工具调用结果：\n");
        sb.append("工具：").append(toolResult.getToolName()).append('\n');
        sb.append("结果摘要：").append(toolResult.getSummary()).append('\n');
        if (toolResult.getData() != null && !toolResult.getData().isEmpty()) {
            sb.append("结构化数据：\n");
            for (Map.Entry<String, Object> entry : toolResult.getData().entrySet()) {
                sb.append("- ").append(entry.getKey()).append("：").append(entry.getValue()).append('\n');
            }
        }
        if (!toolResult.isSuccess() && toolResult.getErrorMessage() != null) {
            sb.append("错误：").append(toolResult.getErrorMessage()).append('\n');
        }
        return sb.toString();
    }

    public static String appendSpecialistResult(String prompt,
            com.careermate.agent.multiagent.SpecialistResult sr) {
        if (sr == null || sr.toolSummary() == null || sr.toolSummary().isBlank()) {
            return prompt;
        }
        return prompt + "\n\n【专家 Agent 结果 - " + sr.domain().name() + "】\n" + sr.toolSummary();
    }
}
