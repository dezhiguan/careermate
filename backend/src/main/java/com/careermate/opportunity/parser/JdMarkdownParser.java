package com.careermate.opportunity.parser;

import com.careermate.opportunity.dto.ParsedJd;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class JdMarkdownParser {

    private static final Pattern H1_PATTERN = Pattern.compile("^#\\s*【JD】(.+)$", Pattern.MULTILINE);
    private static final Pattern META_PATTERN = Pattern.compile(
            "\\*\\*(公司|城市|发布时间|经验|学历|规模|技术标签|薪资)\\*\\*[:：]\\s*(.+)$",
            Pattern.MULTILINE
    );
    private static final Pattern JOB_DESC_HEADER = Pattern.compile("^##\\s*职位描述\\s*$", Pattern.MULTILINE);
    private static final Pattern EXPERIENCE_RANGE_PATTERN = Pattern.compile("^(\\d+)-(\\d+)年$");
    private static final Set<String> EDUCATION_NOISE = Set.of("本科", "硕士", "博士", "大专", "学历不限");
    private static final Set<String> EXPERIENCE_NOISE = Set.of("不限", "应届");

    public JdMarkdownParser() {
    }

    public ParsedJd parse(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return ParsedJd.empty();
        }

        try {
            String[] titleParts = parseTitleParts(markdown);
            String companyField = extractMeta(markdown, "公司");
            String cityField = extractMeta(markdown, "城市");
            String experienceRange = blankToNull(extractMeta(markdown, "经验"));
            Integer[] experienceBounds = parseExperience(experienceRange);
            String rawSkills = extractMeta(markdown, "技术标签");
            String publishedRaw = extractMeta(markdown, "发布时间");

            String company = firstNonBlank(companyField, titlePart(titleParts, 0));
            String title = titlePart(titleParts, 1);
            String level;
            String cityFallback;
            if (titleParts.length >= 4) {
                level = blankToNull(titlePart(titleParts, 2));
                cityFallback = titlePart(titleParts, 3);
            } else if (titleParts.length == 3) {
                level = null;
                cityFallback = titlePart(titleParts, 2);
            } else {
                level = null;
                cityFallback = null;
            }
            String city = firstNonBlank(cityField, cityFallback);

            return new ParsedJd(
                    blankToNull(company),
                    blankToNull(title),
                    level,
                    blankToNull(city),
                    experienceRange,
                    experienceBounds[0],
                    experienceBounds[1],
                    blankToNull(extractMeta(markdown, "学历")),
                    blankToNull(extractMeta(markdown, "规模")),
                    parseDate(publishedRaw),
                    parseSkills(rawSkills),
                    blankToNull(extractJobDescription(markdown)),
                    null,
                    null,
                    null
            );
        } catch (Exception ignored) {
            return ParsedJd.empty();
        }
    }

    private static String[] parseTitleParts(String markdown) {
        Matcher matcher = H1_PATTERN.matcher(markdown);
        if (!matcher.find()) {
            return new String[0];
        }
        return matcher.group(1).split("\\s*\\|\\s*", -1);
    }

    private static String titlePart(String[] parts, int index) {
        if (parts == null || index >= parts.length) {
            return null;
        }
        return parts[index] == null ? null : parts[index].trim();
    }

    private static String extractMeta(String markdown, String label) {
        Matcher matcher = META_PATTERN.matcher(markdown);
        while (matcher.find()) {
            if (label.equals(matcher.group(1))) {
                return matcher.group(2).trim();
            }
        }
        return null;
    }

    private static String extractJobDescription(String markdown) {
        Matcher matcher = JOB_DESC_HEADER.matcher(markdown);
        if (!matcher.find()) {
            return null;
        }
        String tail = markdown.substring(matcher.end()).trim();
        return tail.isEmpty() ? null : tail;
    }

    private static Integer[] parseExperience(String experienceRange) {
        if (experienceRange == null) {
            return new Integer[]{null, null};
        }
        String normalized = experienceRange.trim();
        if ("不限".equals(normalized)) {
            return new Integer[]{null, null};
        }
        if ("应届".equals(normalized)) {
            return new Integer[]{0, 0};
        }
        Matcher matcher = EXPERIENCE_RANGE_PATTERN.matcher(normalized);
        if (matcher.matches()) {
            return new Integer[]{
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2))
            };
        }
        return new Integer[]{null, null};
    }

    static List<String> parseSkills(String rawSkills) {
        if (rawSkills == null || rawSkills.isBlank()) {
            return List.of();
        }
        return Arrays.stream(rawSkills.split(","))
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .filter(token -> !isSkillNoise(token))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static boolean isSkillNoise(String token) {
        if (EXPERIENCE_NOISE.contains(token)) {
            return true;
        }
        if (EDUCATION_NOISE.contains(token)) {
            return true;
        }
        if (token.matches("^\\d+年$") || token.matches("^\\d+-\\d+年$")) {
            return true;
        }
        return false;
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static String firstNonBlank(String primary, String fallback) {
        String p = blankToNull(primary);
        if (p != null) {
            return p;
        }
        return fallback;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
