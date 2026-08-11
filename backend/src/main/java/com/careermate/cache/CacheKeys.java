package com.careermate.cache;

public final class CacheKeys {

    private CacheKeys() {
    }

    public static String opportunityList(String city, String role, String years, String keyword) {
        return "opportunity:list:" + part(city) + ":" + part(role) + ":" + part(years) + ":" + part(keyword);
    }

    public static String marketSalary(String city, String role, String years) {
        return "market:salary:" + part(city) + ":" + part(role) + ":" + part(years);
    }

    /**
     * v2：SkillItem 新增真实词频字段（mentions/heat）。旧 key 下的缓存没有这两个字段，
     * 命中后前端会因为热度缺失而不画热度条，故换 key 让上线后立即产出新形状。
     * v3：检索源由薪资行情库改为岗位 JD 库，v2 缓存里存的是企业名而非技能，必须弃用。
     */
    public static String marketSkillTrends(String city, String role) {
        return "market:skill-trends:v3:" + part(city) + ":" + part(role);
    }

    public static String marketResumeGap(Long userId, String jdId) {
        return "market:resume-gap:" + (userId == null ? "anonymous" : userId) + ":" + part(jdId);
    }

    public static String interviewKbQuestions(String tag) {
        return "interview:kb-questions:" + part(tag);
    }

    public static String interviewCompanyPrep(String company) {
        return "interview:company-prep:" + part(company);
    }

    private static String part(String value) {
        if (value == null || value.isBlank()) {
            return "_";
        }
        return value.trim()
                .replace(':', '_')
                .replaceAll("\\s+", "_");
    }
}
