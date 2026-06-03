package com.careermate.profile;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CareerProfileTargetRoleExtractor {

    private static final Pattern GOAL_PATTERN = Pattern.compile("我的目标是\\s*(.+)");
    private static final Pattern WANT_JOB_PATTERN = Pattern.compile("我想找\\s*(.+)");
    private static final Pattern APPLY_PATTERN = Pattern.compile("我准备投\\s*(.+)");
    private static final Pattern TARGET_ROLE_PATTERN = Pattern.compile("目标岗位是\\s*(.+)");

    public Optional<String> extract(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return Optional.empty();
        }
        String text = userMessage.trim();
        Optional<String> fromGoal = extractByPattern(GOAL_PATTERN, text);
        if (fromGoal.isPresent()) {
            return fromGoal;
        }
        Optional<String> fromWant = extractByPattern(WANT_JOB_PATTERN, text);
        if (fromWant.isPresent()) {
            return fromWant;
        }
        Optional<String> fromApply = extractByPattern(APPLY_PATTERN, text);
        if (fromApply.isPresent()) {
            return fromApply;
        }
        return extractByPattern(TARGET_ROLE_PATTERN, text);
    }

    private Optional<String> extractByPattern(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return Optional.empty();
        }
        String raw = matcher.group(1);
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(normalizeRole(raw.trim()));
    }

    private String normalizeRole(String raw) {
        String cleaned = raw.replaceAll("[。！？!?\\s]+$", "").trim();
        String lower = cleaned.toLowerCase(Locale.ROOT);
        if (lower.endsWith("岗位") && cleaned.length() > 2) {
            cleaned = cleaned.substring(0, cleaned.length() - 2).trim();
        }
        return cleaned;
    }
}
