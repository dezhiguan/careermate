package com.careermate.company;

/**
 * 公司氛围能力的 Prompt 模板。
 *
 * <p>核心约束：只依据检索到的知识库上下文作答，<b>不编造</b>；证据不足时如实标注 dataAvailable=false。
 */
public final class CompanyAtmospherePrompts {

    private CompanyAtmospherePrompts() {
    }

    /**
     * 构造「公司氛围」抽取 Prompt。
     *
     * @param company 公司名
     * @param context 从目标公司知识库检索到的上下文
     * @return 要求模型仅输出 JSON 的用户 Prompt
     */
    public static String atmospherePrompt(String company, String context) {
        String safeCompany = company == null ? "" : company.trim();
        String safeContext = context == null ? "" : context;
        return """
                你是求职助手「小职」，请依据下方「情报上下文」总结「%s」的公司氛围。
                严格要求：
                1. 只依据上下文，禁止编造；上下文没有依据的维度请留空字符串或不给对应标签。
                2. 若上下文信息不足以支撑任何氛围判断，请将 dataAvailable 置为 false。
                3. cultureTags 每个标签给出情绪极性 sentiment：POSITIVE（正面）/ NEGATIVE（负面）/ NEUTRAL（中性）。
                4. 只输出合法 JSON，不要输出任何多余文字、解释或 markdown 代码块围栏。

                JSON 结构：
                {
                  "companyName": "公司名",
                  "workIntensity": "工作强度概述（无据留空）",
                  "teamReputation": "团队口碑概述（无据留空）",
                  "interviewStyle": "面试风格概述（无据留空）",
                  "overtimeSignal": "加班信号概述（无据留空）",
                  "cultureTags": [{"label": "标签", "sentiment": "POSITIVE|NEGATIVE|NEUTRAL"}],
                  "aiSummary": "一句话氛围小结（无据则说明暂无足够情报）",
                  "dataAvailable": true
                }

                情报上下文：
                %s
                """.formatted(safeCompany, safeContext);
    }
}
