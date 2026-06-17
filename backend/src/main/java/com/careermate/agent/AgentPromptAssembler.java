package com.careermate.agent;

import com.careermate.agent.context.CareerProfileContextResult;
import com.careermate.agent.context.ConversationContextResult;
import com.careermate.agent.tool.AgentToolResult;
import com.careermate.jobmatch.JobMatchContext;
import com.careermate.resume.ResumeContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AgentPromptAssembler {

    private static final Set<String> SAFE_STRUCTURED_KEYS = Set.of(
            "toolName", "success", "count", "chunkCount", "citation", "citations",
            "contentPreview", "sourceTitle", "score", "scene", "queryLength",
            "workflowId", "artifactId", "queryPreview", "reasonCode", "blocked",
            "fallbackUsed", "errorCode", "latencyMs"
    );

    private static final Set<String> SENSITIVE_STRUCTURED_KEYS = Set.of(
            "content", "jdContent", "resumeContent", "fullText", "rawOutput",
            "chunks", "previews", "query"
    );

    private static final String BASE_SYSTEM_PROMPT = """
            你是小职，CareerMate 的 AI 求职助手。

            【上下文规则】
            - 提供了「用户默认简历」时，简历优化、岗位匹配、面试准备必须优先参考该简历
            - 提供了「最近岗位匹配结果」时，岗位差距、简历优化、面试准备必须结合该结果
            - 不编造简历和岗位匹配中不存在的信息
            - 缺少默认简历或岗位匹配记录时，提示用户先创建

            【回复风格】
            - 用简洁、精准的中文，不用冗长铺垫，直接给出分析或建议
            - 不用"好的！""当然可以！""很高兴为你服务"等客套句开头
            - 多个要点用 Markdown 列表（`-`），步骤用有序列表（`1.`）
            - 重要词汇用 **加粗**，让用户能快速扫描
            - 段落之间空一行，避免文字堆成一块
            - 单次回复控制在 300 字以内，用户追问时再展开
            - 给出建议时，按重要性排序，最关键的放第一条

            【输出结构】
            - 默认使用以下结构，不要自由发挥成长篇文章：
              1. 先给一行「结论」
              2. 再给 3-5 条「关键建议」
              3. 最后给 1-3 条「下一步」
            - 不要超过 3 个小标题
            - 每个要点不超过 2 行
            - 如果用户问题很简单，只回复 1 个结论 + 2-3 条建议
            - 不要输出大段连续文字
            - 不要堆很多泛泛而谈的建议
            - 所有建议必须和用户的简历、JD、岗位匹配或当前上下文相关
            - 不确定时先问 1 个关键问题，不要列一堆可能性

            【推荐模板】
            **结论**
            一句话直接回答。

            **关键建议**
            - **建议名**：具体原因 + 怎么做。
            - **建议名**：具体原因 + 怎么做。
            - **建议名**：具体原因 + 怎么做。

            **下一步**
            1. 用户下一步应该做什么。
            2. 如果需要，提示上传/选择简历或 JD。

            【结构注意】
            - 不要在所有场景都机械输出完整模板
            - 简单问题要短
            - 复杂问题才展开
            - 不要超过 300 字，除非用户明确要求详细分析
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

    public static boolean shouldAppendSpecialistResult(
            com.careermate.agent.multiagent.SpecialistResult specialistResult) {
        if (specialistResult == null) {
            return false;
        }
        if (specialistResult.getStatus() == com.careermate.agent.multiagent.SpecialistResultStatus.BLOCKED) {
            return true;
        }
        if (specialistResult.getStatus() == com.careermate.agent.multiagent.SpecialistResultStatus.NO_ACTION) {
            return false;
        }
        return specialistResult.getSummary() != null && !specialistResult.getSummary().isBlank();
    }

    public static String appendSpecialistResult(String prompt,
            com.careermate.agent.multiagent.SpecialistResult specialistResult) {
        if (!shouldAppendSpecialistResult(specialistResult)) {
            return prompt;
        }
        StringBuilder sb = new StringBuilder(prompt == null ? "" : prompt);
        sb.append("\n\n【专家协作结果 - ").append(specialistResult.getDomain().name()).append("】\n");
        if (specialistResult.getAgentName() != null && !specialistResult.getAgentName().isBlank()) {
            sb.append("专家：").append(specialistResult.getAgentName()).append('\n');
        }
        sb.append("状态：").append(specialistResult.getStatus().name()).append('\n');
        if (specialistResult.getSummary() != null && !specialistResult.getSummary().isBlank()) {
            sb.append("摘要：").append(specialistResult.getSummary()).append('\n');
        }
        if (specialistResult.getToolName() != null && !specialistResult.getToolName().isBlank()) {
            sb.append("工具：").append(specialistResult.getToolName()).append('\n');
        }
        if (specialistResult.getWarnings() != null && !specialistResult.getWarnings().isEmpty()) {
            sb.append("警告：").append(String.join("；", specialistResult.getWarnings())).append('\n');
        }
        if (specialistResult.getCitations() != null && !specialistResult.getCitations().isEmpty()) {
            sb.append("引用：").append(String.join("；", specialistResult.getCitations())).append('\n');
        }
        appendSafeStructuredData(sb, specialistResult.getStructuredData());
        if (specialistResult.getStatus() == com.careermate.agent.multiagent.SpecialistResultStatus.BLOCKED) {
            sb.append("约束：不得执行会写入或编造虚假经历的工具；只能基于真实经历给出优化建议。\n");
        }
        return sb.toString();
    }

    private static void appendSafeStructuredData(StringBuilder sb, Map<String, Object> structuredData) {
        if (structuredData == null || structuredData.isEmpty()) {
            return;
        }
        sb.append("结构化字段：\n");
        for (Map.Entry<String, Object> entry : structuredData.entrySet()) {
            String key = entry.getKey();
            if (SAFE_STRUCTURED_KEYS.contains(key)) {
                sb.append("- ").append(key).append("：").append(entry.getValue()).append('\n');
                continue;
            }
            if (SENSITIVE_STRUCTURED_KEYS.contains(key)) {
                sb.append("- ").append(key).append("Length：")
                        .append(lengthOf(entry.getValue()))
                        .append('\n');
            }
        }
    }

    private static int lengthOf(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof String text) {
            return text.length();
        }
        if (value instanceof List<?> list) {
            return list.size();
        }
        if (value instanceof Map<?, ?> map) {
            return map.size();
        }
        return String.valueOf(value).length();
    }

    public static String appendReActTrace(String systemPrompt,
            com.careermate.agent.react.ReActTrace trace) {
        if (trace == null || !trace.hasSteps()) {
            return systemPrompt;
        }
        return (systemPrompt == null ? "" : systemPrompt)
            + "\n\n" + trace.toContextText();
    }

    /**
     * 当会话为 JD 准备空间时，向系统提示注入工作空间上下文，
     * 让 AI 知道 JD 已加载、可直接调用 generate_resume_from_jd 工具。
     */
    public static String appendWorkspaceContext(String systemPrompt,
            String workspaceType, String jdId, String jdSnapshot) {
        if (!"JD_PREP".equals(workspaceType) || jdId == null || jdId.isBlank()) {
            return systemPrompt;
        }
        StringBuilder sb = new StringBuilder(systemPrompt == null ? "" : systemPrompt);
        sb.append("\n\n【当前工作空间】");
        sb.append("\n类型：JD 准备空间（generate_resume_from_jd 工具在此空间可用）");
        sb.append("\n目标 JD ID：").append(jdId);
        if (jdSnapshot != null && !jdSnapshot.isBlank()) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper =
                        new com.fasterxml.jackson.databind.ObjectMapper();
                java.util.Map<?, ?> snap = mapper.readValue(jdSnapshot, java.util.Map.class);
                Object company = snap.get("company");
                Object title = snap.get("title");
                if (company != null || title != null) {
                    sb.append("\n目标岗位：");
                    if (company != null && !company.toString().isBlank()) {
                        sb.append(company);
                    }
                    if (company != null && title != null) {
                        sb.append(" · ");
                    }
                    if (title != null && !title.toString().isBlank()) {
                        sb.append(title);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        sb.append("\n\n【工具调用规则 - JD 空间】");
        sb.append("\n- 用户要求生成简历、定制简历、写简历时：直接调用 generate_resume_from_jd，");
        sb.append("不要要求用户再提供 JD，JD 已在工作空间中加载");
        sb.append("\n- 生成成功后对话卡片会出现「下载 PDF」按钮，告知用户点击下载");
        return sb.toString();
    }
}
