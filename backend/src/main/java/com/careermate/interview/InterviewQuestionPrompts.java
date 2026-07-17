package com.careermate.interview;

/**
 * JD-aware 面试题生成的 Prompt 模板。
 *
 * <p>只依据 JD + 简历 + 面试题库上下文出题，禁止编造；每题按 JD_FOCUSED / HIT_RESUME / WEAK_POINT 打标。
 */
public final class InterviewQuestionPrompts {

    private InterviewQuestionPrompts() {
    }

    /**
     * 构造 JD-aware 面试题生成 Prompt。
     *
     * @param jdTitle          JD 标题
     * @param jdContext        JD 正文（职责/要求）
     * @param resumeContext    用户简历上下文（无简历时给占位说明）
     * @param interviewContext 面试题库检索到的参考（可空）
     * @param companyContext   该公司的面经/面试情报（可空；有则据此贴近真实考法出题）
     * @return 要求模型仅输出 JSON 的用户 Prompt
     */
    public static String jdAwarePrompt(String jdTitle, String jdContext, String resumeContext,
                                       String interviewContext, String companyContext) {
        return jdAwarePrompt(jdTitle, jdContext, resumeContext, interviewContext, companyContext, null);
    }

    /**
     * @param weaknessContext 用户历史模拟面试暴露的弱项（Reflexion：据此针对性多出弱项方向的题；可空）
     */
    public static String jdAwarePrompt(String jdTitle, String jdContext, String resumeContext,
                                       String interviewContext, String companyContext, String weaknessContext) {
        String company = companyContext == null || companyContext.isBlank()
                ? "（暂无该公司面经，按 JD 通用考法出题即可）"
                : companyContext;
        String weakness = weaknessContext == null || weaknessContext.isBlank()
                ? "（暂无历史弱项记录）"
                : weaknessContext;
        return """
                你是资深面试辅导专家「小职」。请根据以下信息，为求职者生成针对这条 JD 的面试题。
                严格要求：
                1. 只依据 JD、简历、题库、该公司面经上下文出题，禁止编造与 JD 无关的题。
                2. 生成 6-8 道题，覆盖技术/系统设计/行为等，难度贴合该 JD；若有该公司面经，尽量贴近其真实考法。
                3. 每题打一个标签 tag：
                   - JD_FOCUSED：该 JD 的核心考察点
                   - HIT_RESUME：命中简历中已有的技能/经历
                   - WEAK_POINT：JD 要求但简历较薄弱、需重点准备
                4. 若下方【历史弱项】非空，请针对性地多出 2-3 道覆盖这些弱项方向的题，并打 WEAK_POINT。
                5. 只输出合法 JSON，不要任何多余文字、解释或代码块围栏。

                JSON 结构：
                {
                  "jdTitle": "%s",
                  "questions": [
                    {"questionNo":1,"questionText":"","questionType":"技术|系统设计|行为|HR",
                     "referencePoints":["要点1"],"tag":"JD_FOCUSED|HIT_RESUME|WEAK_POINT","rationale":"为什么问这题"}
                  ],
                  "aiSummary": "一句话小结"
                }

                【JD 内容】
                %s

                【用户简历】
                %s

                【面试题库参考】
                %s

                【该公司面经】
                %s

                【历史弱项（据此针对性出题）】
                %s
                """.formatted(
                jdTitle == null ? "" : jdTitle,
                jdContext == null ? "" : jdContext,
                resumeContext == null ? "" : resumeContext,
                interviewContext == null ? "" : interviewContext,
                company,
                weakness);
    }
}
