package com.careermate.resume.version.support;

/**
 * #6「说一句即改」意图判定：区分「在已有简历上改某处」与「从 JD 重做整份」。
 * 供两处共用，保持关键词一致：
 * <ul>
 *   <li>{@code AgentStreamService} LLM 前的确定性拦截（防 function-calling 直接文本作答、不落版本）；</li>
 *   <li>{@code GenerateResumeFromJdTool} 内部把误选的 generate 重定向到 modify。</li>
 * </ul>
 */
public final class ResumeModifyIntent {

    private static final String[] MODIFY_KEYWORDS = {
            "改成", "换成", "调成", "改为", "加一段", "加上", "补一段", "补充", "删掉", "去掉",
            "换个说法", "换种说法", "调整", "微调", "改一下", "改下", "这里改", "这段改"
    };

    private static final String[] REGENERATE_KEYWORDS = {
            "重新生成", "重做", "重写整份", "再生成一份", "换一份", "重新定制"
    };

    private ResumeModifyIntent() {
    }

    /** 是「在已有稿上改某处」的微调意图（且不是「重做整份」）时返回 true。 */
    public static boolean isModifyOnExistingIntent(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String lower = message.toLowerCase();
        if (containsAny(lower, REGENERATE_KEYWORDS)) {
            return false;
        }
        return containsAny(lower, MODIFY_KEYWORDS);
    }

    private static boolean containsAny(String text, String[] keywords) {
        for (String k : keywords) {
            if (text.contains(k)) {
                return true;
            }
        }
        return false;
    }
}
