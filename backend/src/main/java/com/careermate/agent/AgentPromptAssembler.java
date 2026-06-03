package com.careermate.agent;

import com.careermate.jobmatch.JobMatchContext;
import com.careermate.resume.ResumeContext;

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

    public static String buildSystemPrompt(ResumeContext resumeContext, JobMatchContext jobMatchContext) {
        StringBuilder sb = new StringBuilder(BASE_SYSTEM_PROMPT.trim());
        if (resumeContext != null && resumeContext.getContextText() != null && !resumeContext.getContextText().isBlank()) {
            sb.append("\n\n").append(resumeContext.getContextText().trim());
        }
        if (jobMatchContext != null && jobMatchContext.getContextText() != null && !jobMatchContext.getContextText().isBlank()) {
            sb.append("\n\n").append(jobMatchContext.getContextText().trim());
        }
        return sb.toString();
    }
}
