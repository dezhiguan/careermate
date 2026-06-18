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

    public static String marketSkillTrends(String city, String role) {
        return "market:skill-trends:" + part(city) + ":" + part(role);
    }

    public static String marketResumeGap(Long userId, String jdId) {
        return "market:resume-gap:" + (userId == null ? "anonymous" : userId) + ":" + part(jdId);
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
