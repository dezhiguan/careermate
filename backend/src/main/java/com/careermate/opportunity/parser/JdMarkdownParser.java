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

    private static final String JD_FILENAME_PREFIX = "【JD】";
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

    /**
     * 解析 JD，并以文件名作为兜底数据源。
     *
     * <p>机会列表走 RAG 语义检索，命中的往往只是文档的部分 chunk（正文段落），
     * 承载 {@code # 【JD】} 标题与 {@code **公司**：} 元信息的头部 chunk 常常不在召回结果里，
     * 导致仅解析正文时公司/标题为空（前端显示「未知公司」，标题回退成整段文件名）。
     * 而文件名 {@code 【JD】{城市} · {公司} · {岗位} · {薪资}.md} 由采集侧确定性生成，
     * 每个 chunk 都携带，可作为可靠兜底。内容解析优先，文件名解析补空。
     */
    public ParsedJd parse(String markdown, String filename) {
        ParsedJd fromContent = parse(markdown);
        FilenameMeta fromName = parseFilename(filename);
        return new ParsedJd(
                firstNonBlank(fromContent.company(), fromName.company()),
                firstNonBlank(fromContent.title(), fromName.title()),
                fromContent.level(),
                firstNonBlank(fromContent.city(), fromName.city()),
                fromContent.experienceRange(),
                fromContent.experienceMin(),
                fromContent.experienceMax(),
                fromContent.education(),
                fromContent.companySize(),
                fromContent.publishedAt(),
                fromContent.skills(),
                fromContent.jobDescription(),
                firstNonBlank(fromContent.salaryRange(), fromName.salary()),
                fromContent.salaryMin(),
                fromContent.salaryMax()
        );
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
            String salaryField = blankToNull(extractMeta(markdown, "薪资"));

            String company = firstNonBlank(companyField, titlePart(titleParts, 0));
            String title = titlePart(titleParts, 1);
            String level;
            String salaryFromTitle = null;
            String cityFallback;
            if (titleParts.length >= 4) {
                // Boss 真实数据 H1 为「公司 | 岗位 | 薪资 | 城市」，设计样例为「公司 | 岗位 | 级别 | 城市」，
                // 同一段位既可能是薪资也可能是级别——按薪资特征判别，避免把薪资误标成级别。
                String segment = blankToNull(titlePart(titleParts, 2));
                if (isSalaryToken(segment)) {
                    salaryFromTitle = segment;
                    level = null;
                } else {
                    level = segment;
                }
                cityFallback = titlePart(titleParts, 3);
            } else if (titleParts.length == 3) {
                level = null;
                cityFallback = titlePart(titleParts, 2);
            } else {
                level = null;
                cityFallback = null;
            }
            String city = firstNonBlank(cityField, cityFallback);
            // 元信息 **薪资** 为权威来源，优先于 H1 段位
            String salaryRange = firstNonBlank(salaryField, salaryFromTitle);

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
                    salaryRange,
                    null,
                    null
            );
        } catch (Exception ignored) {
            return ParsedJd.empty();
        }
    }

    /**
     * 从 JD 文件名解析城市/公司/岗位/薪资。
     *
     * <p>采集侧生成规则：{@code 【JD】} + 非空字段按 {@code 城市 · 公司 · 岗位 · 薪资} 顺序用「 · 」拼接 + {@code .md}
     * （见 collector 的 chunk_builder.build_boss_jd）。城市在 Boss 数据中基本恒有且很短，
     * 薪资恒在末位，据此按序还原字段；缺薪资时末位不匹配薪资特征即退化为 城市·公司·岗位。
     */
    static FilenameMeta parseFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return FilenameMeta.EMPTY;
        }
        String name = filename.trim();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        // 去掉「 (1)」这类系统去重后缀
        name = name.replaceFirst("\\s*\\(\\d+\\)\\s*$", "").trim();
        // 仅信任采集侧确定性生成的「【JD】…」命名，其它文件名不臆测字段
        if (!name.startsWith(JD_FILENAME_PREFIX)) {
            return FilenameMeta.EMPTY;
        }
        name = name.substring(JD_FILENAME_PREFIX.length()).trim();
        if (name.isEmpty()) {
            return FilenameMeta.EMPTY;
        }

        List<String> tokens = Arrays.stream(name.split("·"))
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .collect(Collectors.toCollection(ArrayList::new));
        if (tokens.isEmpty()) {
            return FilenameMeta.EMPTY;
        }

        String salary = null;
        if (tokens.size() >= 2 && isSalaryToken(tokens.get(tokens.size() - 1))) {
            salary = tokens.remove(tokens.size() - 1);
        }

        String city = null;
        String company = null;
        String title = null;
        if (tokens.size() >= 3) {
            // 城市 · 公司 · 岗位[ · 岗位补充...]
            city = tokens.get(0);
            company = tokens.get(1);
            title = String.join(" · ", tokens.subList(2, tokens.size()));
        } else if (tokens.size() == 2) {
            if (salary != null) {
                // 有薪资却只剩两段，说明城市被丢弃：公司 · 岗位
                company = tokens.get(0);
                title = tokens.get(1);
            } else {
                // 城市基本恒有，优先按 城市 · 公司 还原
                city = tokens.get(0);
                company = tokens.get(1);
            }
        } else {
            company = tokens.get(0);
        }

        return new FilenameMeta(
                blankToNull(city),
                blankToNull(company),
                blankToNull(title),
                blankToNull(salary)
        );
    }

    private static boolean isSalaryToken(String token) {
        if (token == null) {
            return false;
        }
        return token.matches(".*\\d.*") && token.matches(".*[KkWw千万薪].*");
    }

    record FilenameMeta(String city, String company, String title, String salary) {
        static final FilenameMeta EMPTY = new FilenameMeta(null, null, null, null);
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
