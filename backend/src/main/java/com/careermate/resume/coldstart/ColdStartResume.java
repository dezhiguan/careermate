package com.careermate.resume.coldstart;

/**
 * 冷启动建档产物（P1）——一份可直接落库的初版简历。
 *
 * @param title         简历标题
 * @param content       Markdown 正文（L3/L2 含引导占位）
 * @param origin        来源（通常 COLD_START）
 * @param readiness     就绪度（骨架为 DRAFT_SKELETON）
 * @param sourceSignals 信号来源标记 JSON（供审计：conversation/career_profile/default_skeleton）
 */
public record ColdStartResume(
        String title,
        String content,
        String origin,
        String readiness,
        String sourceSignals
) {
    public boolean isDraftSkeleton() {
        return ResumeReadiness.isDraftSkeleton(readiness);
    }
}
