package com.careermate.resume.coldstart;

import com.careermate.common.exception.BizException;

/**
 * 简历填充就绪度（P1）。落库到 resumes.readiness，默认 READY 以保证存量语义不变。
 *
 * <p>DRAFT_SKELETON（默认引导占位骨架）豁免 P2 占位词检测，但导出/投递前必须
 * 先离开该状态（占位填充完成）——由 {@link #ensureExportable} 守卫。
 */
public final class ResumeReadiness {

    /** 可正常导出/投递。 */
    public static final String READY = "READY";
    /** 默认引导占位骨架，尚待用户填充。 */
    public static final String DRAFT_SKELETON = "DRAFT_SKELETON";

    /** 骨架里引导占位行的可检测标记（见 DefaultResumeSkeletonBuilder）。 */
    public static final String GUIDE_MARKER = "填写引导：";

    private ResumeReadiness() {
    }

    public static boolean isDraftSkeleton(String readiness) {
        return DRAFT_SKELETON.equals(readiness);
    }

    /**
     * 判断骨架是否已被用户填充完成：正文中不再残留任何引导占位标记，且有实质内容。
     */
    public static boolean isFilled(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        return !content.contains(GUIDE_MARKER);
    }

    /**
     * 依据当前正文推断就绪度：填充完成→READY，否则维持 DRAFT_SKELETON。
     */
    public static String resolveAfterEdit(String content) {
        return isFilled(content) ? READY : DRAFT_SKELETON;
    }

    /**
     * 导出/投递前守卫：草稿骨架未填完则拒绝，给出友好且可行动的提示。
     */
    public static void ensureExportable(String readiness) {
        if (isDraftSkeleton(readiness)) {
            throw new BizException(400,
                    "这份简历还是初始骨架，几处关键信息（如工作经历、项目）还没填。"
                            + "先在『我的简历』把带「填写引导」的地方补充完整，就能导出啦～");
        }
    }
}
