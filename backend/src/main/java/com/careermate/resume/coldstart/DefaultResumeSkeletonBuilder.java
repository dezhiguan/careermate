package com.careermate.resume.coldstart;

import java.util.List;

import org.springframework.stereotype.Component;

/**
 * 无上传冷启动的默认简历骨架构建器（P1）。
 *
 * <p>产出「默认格式 + 引导占位」的初版简历：
 * <ul>
 *   <li><b>L3</b>（什么都没有）：{@link #buildDefaultSkeleton()} 纯引导占位骨架。</li>
 *   <li><b>L2</b>（仅有画像）：{@link #buildFromProfile} 用目标岗位/技能预填，其余留引导占位。</li>
 * </ul>
 *
 * <p>铁律：只给结构与填写引导，<b>绝不预置任何虚构的公司/学历/经历</b>。
 * 引导行统一带 {@link ResumeReadiness#GUIDE_MARKER} 标记，便于判断是否填充完成；
 * 措辞刻意避开 P2 占位词（暂无/待补充/示例/公司A 等），不与质量校验冲突。
 */
@Component
public class DefaultResumeSkeletonBuilder {

    private static final String DEFAULT_TITLE = "我的简历（待完善）";
    private static final String NAME_PLACEHOLDER = "你的姓名";

    /** L3：什么都没提供时的系统默认骨架。 */
    public ColdStartResume buildDefaultSkeleton() {
        String content = render(null, List.of());
        return new ColdStartResume(
                DEFAULT_TITLE, content,
                ResumeOrigin.COLD_START, ResumeReadiness.DRAFT_SKELETON,
                signals("default_skeleton"));
    }

    /** L2：仅有画像时，用目标岗位/技能预填，其余留引导占位。 */
    public ColdStartResume buildFromProfile(String targetRole, List<String> skills) {
        List<String> safeSkills = skills == null ? List.of() : skills.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .toList();
        String content = render(trimToNull(targetRole), safeSkills);
        String title = trimToNull(targetRole) != null
                ? targetRole.trim() + " · 简历（待完善）"
                : DEFAULT_TITLE;
        return new ColdStartResume(
                title, content,
                ResumeOrigin.COLD_START, ResumeReadiness.DRAFT_SKELETON,
                signals("career_profile"));
    }

    private String render(String targetRole, List<String> skills) {
        String mark = ResumeReadiness.GUIDE_MARKER;
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(NAME_PLACEHOLDER).append("\n\n");
        sb.append("> ").append(mark).append("城市 · 手机 · 邮箱 · GitHub / 个人主页\n\n");

        sb.append("## 个人优势\n\n");
        if (targetRole != null) {
            sb.append("> ").append(mark)
                    .append("你的目标岗位是「").append(targetRole)
                    .append("」，用两三句话讲清你最能打动人的核心竞争力。\n\n");
        } else {
            sb.append("> ").append(mark)
                    .append("用两三句话讲清你最能打动人的核心竞争力（擅长什么、做出过什么结果）。\n\n");
        }

        sb.append("## 专业技能\n\n");
        if (!skills.isEmpty()) {
            sb.append(String.join(" · ", skills)).append("\n\n");
        } else {
            sb.append("> ").append(mark)
                    .append("列出你最擅长的技术/工具，用「·」分隔。\n\n");
        }

        sb.append("## 工作经历\n\n");
        sb.append("> ").append(mark)
                .append("公司 — 职位（起止时间）；每段用一句话讲清你做了什么、带来什么可量化的结果。\n\n");

        sb.append("## 项目经历\n\n");
        sb.append("> ").append(mark)
                .append("项目名称；技术方案 + 你的角色 + 成果。\n\n");

        sb.append("## 教育经历\n\n");
        sb.append("> ").append(mark)
                .append("学校 — 专业 · 学历（起止时间）。\n");
        return sb.toString();
    }

    private static String signals(String source) {
        return "{\"source\":\"" + source + "\"}";
    }

    private static String trimToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
