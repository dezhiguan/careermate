package com.careermate.llm.provider;

import com.careermate.llm.LlmProperties;

import java.util.regex.Pattern;

public final class LlmProviderDefaults {

    public static final String QWEN_ENDPOINT = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    public static final String QWEN_MODEL = "qwen-plus";
    public static final String DEEPSEEK_ENDPOINT = "https://api.deepseek.com/v1";
    public static final String DEEPSEEK_MODEL = "deepseek-chat";

    private static final Pattern SECRET_PATTERN = Pattern.compile(
            "(?i)(sk-[a-z0-9_-]{8,}|Bearer\\s+\\S+|api[_-]?key[\"']?\\s*[:=]\\s*\\S+)"
    );

    private LlmProviderDefaults() {
    }

    public static LlmProperties copyWithQwenDefaults(LlmProperties source) {
        LlmProperties copy = copy(source);
        if (isBlank(copy.getEndpoint())) {
            copy.setEndpoint(QWEN_ENDPOINT);
        }
        if (isBlank(copy.getModel()) || "mock-chat".equals(copy.getModel())) {
            copy.setModel(QWEN_MODEL);
        }
        return copy;
    }

    public static LlmProperties copyWithDeepSeekDefaults(LlmProperties source) {
        LlmProperties copy = copy(source);
        if (isBlank(copy.getEndpoint())) {
            copy.setEndpoint(DEEPSEEK_ENDPOINT);
        }
        if (isBlank(copy.getModel()) || "mock-chat".equals(copy.getModel())) {
            copy.setModel(DEEPSEEK_MODEL);
        }
        return copy;
    }

    public static LlmProperties copy(LlmProperties source) {
        LlmProperties copy = new LlmProperties();
        if (source == null) {
            return copy;
        }
        copy.setProvider(source.getProvider());
        copy.setModel(source.getModel());
        copy.setApiKey(source.getApiKey());
        copy.setEndpoint(source.getEndpoint());
        copy.setTimeoutMs(source.getTimeoutMs());
        copy.setMaxTokens(source.getMaxTokens());
        copy.setTemperature(source.getTemperature());
        return copy;
    }

    public static String userFacingHttpError(int statusCode) {
        if (statusCode == 401 || statusCode == 403) {
            return "LLM 认证失败，请检查 API Key 配置";
        }
        if (statusCode == 429) {
            return "LLM 请求过于频繁，请稍后重试";
        }
        if (statusCode >= 500) {
            return "LLM 服务暂时不可用，请稍后重试";
        }
        return "LLM 调用失败";
    }

    public static String sanitizeForLog(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String sanitized = SECRET_PATTERN.matcher(value).replaceAll("[REDACTED]");
        if (sanitized.length() > 500) {
            return sanitized.substring(0, 500) + "...";
        }
        return sanitized;
    }

    public static String safeStreamErrorMessage(Throwable error) {
        if (error == null) {
            return "模型服务异常，请稍后重试";
        }
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return "模型服务异常，请稍后重试";
        }
        if (SECRET_PATTERN.matcher(message).find()) {
            return "模型服务异常，请稍后重试";
        }
        if (message.length() > 240) {
            return message.substring(0, 240) + "...";
        }
        return message;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
