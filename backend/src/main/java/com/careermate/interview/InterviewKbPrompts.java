package com.careermate.interview;

/**
 * 面试知识库 LLM Prompt 模板。
 */
public final class InterviewKbPrompts {

    private static final String KB_QUESTIONS_PROMPT = """
            你是面试辅导专家。根据以下面试知识库内容，提炼关于「%s」的高频面试题和参考答案。
            要求：
            1. 提取 5 个最重要的面试题
            2. 每题附简洁参考答案（不超过 100 字）
            3. category 只能是：技术/行为/HR
            4. aiSummary 不超过 60 字，说明该方向的备考重点

            知识库内容：
            %s

            只输出以下格式的 JSON，不要任何其他文字：
            {"query":"","questions":[{"question":"","answer":"","category":""}],"aiSummary":""}
            """;

    private static final String KB_QUESTIONS_GENERAL_PROMPT = """
            你是面试辅导专家。面试知识库中暂无「%s」的直接资料，请基于你的通用知识，
            提炼关于「%s」的高频面试题和参考答案，确保对求职者有实际帮助。
            要求：
            1. 提取 5 个最重要的面试题
            2. 每题附简洁参考答案（不超过 100 字）
            3. category 只能是：技术/行为/HR
            4. aiSummary 不超过 60 字，并注明「以下为 AI 依据通用知识生成，暂未匹配到知识库资料」

            只输出以下格式的 JSON，不要任何其他文字：
            {"query":"","questions":[{"question":"","answer":"","category":""}],"aiSummary":""}
            """;

    private static final String COMPANY_PREP_PROMPT = """
            你是求职顾问，熟悉互联网公司面试风格。根据以下资料，分析 %s 的面试攻略。
            要求：
            1. interviewStyle：面试风格描述（不超过 50 字）
            2. techFocus：技术考查重点关键词（最多 6 个）
            3. commonQuestions：该公司高频面试题（最多 5 条）
            4. behaviorTips：行为面/HR面注意事项（不超过 60 字）
            5. aiSummary：综合备考建议（不超过 80 字）

            参考资料：
            %s

            只输出以下格式的 JSON，不要任何其他文字：
            {"companyName":"%s","interviewStyle":"","techFocus":[],"commonQuestions":[],"behaviorTips":"","aiSummary":""}
            """;

    private InterviewKbPrompts() {
    }

    public static String kbQuestionsPrompt(String query, String context) {
        return String.format(KB_QUESTIONS_PROMPT, query, context);
    }

    /** 知识库无命中时，让 LLM 依据通用知识现场生成考点题（评审 P0-3：空态给出路）。 */
    public static String kbQuestionsGeneralPrompt(String query) {
        return String.format(KB_QUESTIONS_GENERAL_PROMPT, query, query);
    }

    public static String companyPrepPrompt(String company, String context) {
        return String.format(COMPANY_PREP_PROMPT, company, context, company);
    }
}
