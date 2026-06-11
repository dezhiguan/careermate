package com.careermate.market;

/**
 * 市场行情 LLM Prompt 模板。
 */
public final class MarketPrompts {

    private static final String SALARY_PROMPT = """
            你是薪资分析专家。根据以下 JD 数据，分析 %s 在 %s 地区 %s 经验的薪资分布。
            要求：
            1. 从数据中提取薪资数字，估算 P25/P50/P75/P90 月薪（格式如"28K"）
            2. 判断当前市场薪资趋势
            3. aiSummary 不超过 80 字

            JD 数据：
            %s

            只输出以下格式的 JSON，不要任何其他文字：
            {"p25":"","p50":"","p75":"","p90":"","trend":"","aiSummary":""}
            """;

    private static final String SKILL_TRENDS_PROMPT = """
            你是技术市场分析专家。根据以下 JD 数据，分析 %s 岗位的技能需求热度。
            要求：
            1. 提取出现频率最高的 6 个技术技能
            2. 按频率从高到低排 rank 1-6
            3. level 只能是：高频/中频/低频
            4. growth 只能是：快涨/上涨/稳定/下降
            5. aiSummary 不超过 80 字

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
            3. matchScore：0-100 整数，反映匹配程度
            4. topSuggestion：最重要的一条改进建议，不超过 40 字
            5. aiSummary：不超过 80 字

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
            1. scale：公司规模（如"大厂 / 上市公司"、"中型企业"）
            2. stage：融资阶段（如"上市"、"D轮"、"未知"）
            3. techStack：从 JD 中提取该公司使用的技术栈（最多 8 个）
            4. currentJds：从 JD 中提取该公司当前在招的岗位名称（最多 5 个）
            5. aiSummary：不超过 80 字的公司简介

            JD 数据：
            %s

            只输出以下格式的 JSON，不要任何其他文字：
            {"companyName":"%s","scale":"","stage":"","techStack":[],"currentJds":[],"aiSummary":""}
            """;

    private MarketPrompts() {
    }

    public static String salaryPrompt(String role, String city, String years, String jdContext) {
        return String.format(SALARY_PROMPT, role, city, years, jdContext);
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
