package com.careermate.market;

/**
 * 市场行情 LLM Prompt 模板。
 */
public final class MarketPrompts {

    private static final String SALARY_PROMPT = """
            你是薪资分析专家。根据以下 JD 数据，分析 %s 在 %s 地区、%s的薪资分布。
            要求：
            1. 从数据中提取薪资数字，估算 P25/P50/P75/P90 月薪（格式如"28K"）
            2. 判断当前市场薪资趋势
            3. aiSummary 不超过 80 字
            4. aiSummary 必须与上述经验口径完全一致：若口径为「全经验段（不限工作年限）」，
               则不得在结论中写成任何具体年限区间（如"3-5年经验"），只能表述为不限经验/全经验段

            JD 数据：
            %s

            只输出以下格式的 JSON，不要任何其他文字：
            {"p25":"","p50":"","p75":"","p90":"","trend":"","aiSummary":""}
            """;

    /** 分位已由样本算出时，LLM 只负责趋势与结论文案，不再估算数字。 */
    private static final String SALARY_NARRATIVE_PROMPT = """
            你是薪资分析专家。以下 P25/P50/P75/P90 已由 %s 在 %s 地区、%s 的真实招聘薪资样本
            （共 %d 条）统计得出，是确定的事实，不得修改、不得给出其它数字。

            P25=%s  P50=%s  P75=%s  P90=%s

            要求：
            1. trend：判断当前市场薪资趋势，只能是：上涨/稳中有升/平稳/下降
            2. aiSummary 不超过 80 字，只能引用上面给定的分位数字
            3. aiSummary 必须与上述经验口径完全一致：若口径为「全经验段（不限工作年限）」，
               则不得写成任何具体年限区间

            JD 数据：
            %s

            只输出以下格式的 JSON，不要任何其他文字：
            {"trend":"","aiSummary":""}
            """;

    private static final String SKILL_TRENDS_PROMPT = """
            你是技术市场分析专家。根据以下 JD 数据，分析 %s 岗位的技能需求热度。
            要求：
            1. 提取出现频率最高的 6 个技术技能
            2. name 必须是 JD 数据中原样出现的技能词，不得改写、翻译或杜撰未出现的技能
            3. 按频率从高到低排 rank 1-6
            4. level 只能是：高频/中频/低频
            5. growth 只能是：快涨/上涨/稳定/下降
            6. aiSummary 不超过 80 字

            JD 数据：
            %s

            只输出以下格式的 JSON，不要任何其他文字：
            {"skills":[{"rank":1,"name":"","level":"","growth":""}],"aiSummary":""}
            """;

    private static final String RESUME_GAP_PROMPT = """
            你是求职顾问。根据以下用户简历和市场 JD 数据，分析技能匹配情况。
            要求：
            1. hasSkills：用户简历中有、且 JD 里也要求的技能（最多 8 个）
            2. missingSkills：JD 里高频要求、但用户简历中没有的技能（最多 6 个）
            3. 技能名必须在对应原文中原样出现：hasSkills 需同时见于简历与 JD，
               missingSkills 需见于 JD 且不见于简历；不确定的宁可不写，不得凭常识补全
            4. matchScore：0-100 整数，反映匹配程度
            5. topSuggestion：最重要的一条改进建议，不超过 40 字
            6. aiSummary：不超过 80 字

            用户简历：
            %s

            市场 JD 数据：
            %s

            只输出以下格式的 JSON，不要任何其他文字：
            {"hasSkills":[],"missingSkills":[],"matchScore":0,"topSuggestion":"","aiSummary":""}
            """;

    private static final String COMPANY_PROMPT = """
            你是公司研究专家。根据以下 JD 数据，分析 %s 公司的基本情况。
            要求：
            1. scale：只写体量档位，四选一：大厂 / 中型企业 / 小型企业 / 初创公司。
               不要在这里写融资或上市状态，那是 stage 的内容
            2. stage：只写融资或上市阶段，如"上市"、"D轮"、"未融资"；JD 数据里无依据就写"未知"
            3. techStack：从 JD 中提取该公司使用的技术栈（最多 8 个），必须在 JD 原文中原样出现
            4. currentJds：从 JD 中提取该公司当前在招的岗位名称（最多 5 个），以原文岗位名为准
            5. 不得凭常识补全 JD 里没有的技术栈或岗位
            6. aiSummary：不超过 80 字的公司简介

            JD 数据：
            %s

            只输出以下格式的 JSON，不要任何其他文字：
            {"companyName":"%s","scale":"","stage":"","techStack":[],"currentJds":[],"aiSummary":""}
            """;

    private MarketPrompts() {
    }

    /**
     * @param yearsClause 经验口径描述，由 {@code MarketExperience.describe} 生成
     *                    （如「3-5年经验」「全经验段（不限工作年限）」）
     */
    public static String salaryPrompt(String role, String city, String yearsClause, String jdContext) {
        return String.format(SALARY_PROMPT, role, city, yearsClause, jdContext);
    }

    /** @param quantiles 依次为 P25/P50/P75/P90 的展示值（如「25K」） */
    public static String salaryNarrativePrompt(
            String role,
            String city,
            String yearsClause,
            int sampleCount,
            String[] quantiles,
            String jdContext
    ) {
        return String.format(SALARY_NARRATIVE_PROMPT, role, city, yearsClause, sampleCount,
                quantiles[0], quantiles[1], quantiles[2], quantiles[3], jdContext);
    }

    public static String skillTrendsPrompt(String role, String jdContext) {
        return String.format(SKILL_TRENDS_PROMPT, role, jdContext);
    }

    public static String resumeGapPrompt(String resumeText, String jdContext) {
        return String.format(RESUME_GAP_PROMPT, resumeText, jdContext);
    }

    public static String companyPrompt(String company, String jdContext) {
        return String.format(COMPANY_PROMPT, company, jdContext, company);
    }
}
