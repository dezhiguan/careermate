package com.careermate.agent.multiagent;

final class FabricationRiskDetector {

    private FabricationRiskDetector() {
    }

    static boolean detect(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String lower = message.toLowerCase();
        if (containsAny(lower, "编造", "伪造", "虚构", "假学历", "假证书", "假经历", "造假", "杜撰")) {
            return true;
        }
        if (containsAny(lower, "没做过", "没有做过", "从未做过", "没参与过", "没做过项目")) {
            return returnWriteIntoResume(lower);
        }
        if (containsAny(lower, "编一个", "编个", "编一段", "编点")) {
            return lower.contains("简历") || lower.contains("经历") || lower.contains("项目");
        }
        if (lower.contains("大厂") && containsAny(lower, "编", "编造", "没有", "没做过", "虚构")) {
            return true;
        }
        return returnWriteIntoResume(lower) && containsAny(lower, "大厂", "阿里", "腾讯", "字节", "美团");
    }

    private static boolean returnWriteIntoResume(String lower) {
        return lower.contains("写进简历")
                || lower.contains("写入简历")
                || lower.contains("加到简历")
                || lower.contains("放进简历")
                || (lower.contains("简历") && containsAny(lower, "写进", "写入", "加上", "补充"));
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
