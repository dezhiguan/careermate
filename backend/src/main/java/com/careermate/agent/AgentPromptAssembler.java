package com.careermate.agent;

import com.careermate.resume.ResumeContext;

public final class AgentPromptAssembler {

    private static final String BASE_SYSTEM_PROMPT = """
            你是 CareerMate 求职 Agent。
            如果提供了「用户默认简历」，回答简历优化、岗位匹配、面试准备问题时必须优先参考该简历。
            不要编造简历中不存在的经历。
            如果简历上下文为空，请提示用户先创建或设置默认简历。
            """;

    private AgentPromptAssembler() {
    }

    public static String buildSystemPrompt(ResumeContext resumeContext) {
        StringBuilder sb = new StringBuilder(BASE_SYSTEM_PROMPT.trim());
        if (resumeContext != null && resumeContext.getContextText() != null && !resumeContext.getContextText().isBlank()) {
            sb.append("\n\n").append(resumeContext.getContextText().trim());
        }
        return sb.toString();
    }
}
