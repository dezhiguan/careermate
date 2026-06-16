package com.careermate.resume.version.export;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将 Markdown / 纯文本简历解析为结构化数据，供 PDF 简历版式渲染使用。
 * 不新增、不编造内容，仅做结构识别与分段。
 */
final class ResumeStructureParser {

    private static final Pattern MARKDOWN_HEADING = Pattern.compile("^#{1,3}\\s+(.+)$");
    private static final Pattern PHONE = Pattern.compile("(?:电话|手机|Tel|Phone)?[:：\\s]*(1[3-9]\\d{9})");
    private static final Pattern EMAIL = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern GITHUB = Pattern.compile("(?:https?://)?(?:www\\.)?github\\.com/[\\w-]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern GITEE = Pattern.compile("(?:https?://)?(?:www\\.)?gitee\\.com/[\\w-]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern LINKEDIN = Pattern.compile("(?:https?://)?(?:www\\.)?linkedin\\.com/in/[\\w-]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern CITY_LABEL = Pattern.compile("(?:城市|所在地|现居)[:：\\s]*([^|/，,\\s]{2,10})");
    private static final Pattern JOB_LABEL = Pattern.compile("(?:期望职位|求职岗位|求职意向|目标岗位)[:：\\s]*([^|/，,\\n]{2,30})");

    private static final Set<String> KNOWN_SECTION_TITLES = Set.of(
            "个人优势", "个人简介", "自我介绍", "求职意向", "技能栈", "专业技能",
            "工作经历", "实习经历", "项目经历", "教育经历", "证书", "其他"
    );

    private ResumeStructureParser() {
    }

    record ResumeStructure(String name, List<String> contactParts, List<ResumeSection> sections) {
    }

    record ResumeSection(String title, String contentMarkdown) {
    }

    static ResumeStructure parse(String markdown) {
        String source = MarkdownExportSupport.stripOptimizationMetaFromMarkdown(markdown).strip();
        if (source.isEmpty()) {
            return new ResumeStructure("个人简历", List.of(), List.of());
        }

        List<String> lines = List.of(source.split("\n", -1));
        int nameLineIndex = -1;
        String name = null;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            if (line.isEmpty()) {
                continue;
            }
            Matcher heading = MARKDOWN_HEADING.matcher(line);
            if (heading.matches()) {
                String title = cleanMarkdownText(heading.group(1));
                if (name == null && i < 3 && !isKnownSectionTitle(title)) {
                    name = title;
                    nameLineIndex = i;
                    continue;
                }
                if (isKnownSectionTitle(title) || name != null) {
                    break;
                }
            } else if (name == null && i < 3 && isPlausibleNameLine(line)) {
                name = cleanMarkdownText(line);
                nameLineIndex = i;
                break;
            }
        }

        if (name == null || name.isBlank()) {
            name = "个人简历";
            nameLineIndex = -1;
        }

        int firstSectionIndex = findFirstSectionIndex(lines, nameLineIndex);
        List<String> contactParts = extractContactParts(lines, nameLineIndex, firstSectionIndex);
        List<ResumeSection> sections = extractSections(lines, firstSectionIndex);

        if (sections.isEmpty()) {
            String body = source;
            if (nameLineIndex >= 0) {
                body = joinLines(lines, nameLineIndex + 1, lines.size());
            }
            body = body.strip();
            if (!body.isBlank()) {
                sections = List.of(new ResumeSection(null, body));
            }
        }

        return new ResumeStructure(name, contactParts, sections);
    }

    private static int findFirstSectionIndex(List<String> lines, int nameLineIndex) {
        for (int i = Math.max(nameLineIndex, 0) + 1; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            if (line.isEmpty()) {
                continue;
            }
            Matcher heading = MARKDOWN_HEADING.matcher(line);
            if (heading.matches()) {
                String title = cleanMarkdownText(heading.group(1));
                if (isKnownSectionTitle(title) || i > nameLineIndex + 2) {
                    return i;
                }
            } else if (isKnownSectionTitle(cleanMarkdownText(line))) {
                return i;
            }
        }
        return lines.size();
    }

    private static List<String> extractContactParts(List<String> lines, int nameLineIndex, int firstSectionIndex) {
        LinkedHashSet<String> parts = new LinkedHashSet<>();
        int start = nameLineIndex >= 0 ? nameLineIndex + 1 : 0;
        for (int i = start; i < firstSectionIndex; i++) {
            String line = lines.get(i).strip();
            if (line.isEmpty() || MARKDOWN_HEADING.matcher(line).matches()) {
                continue;
            }
            collectContactFromText(line, parts);
        }
        return List.copyOf(parts);
    }

    private static void collectContactFromText(String text, LinkedHashSet<String> parts) {
        String cleaned = cleanMarkdownText(text);
        if (cleaned.isBlank()) {
            return;
        }

        Matcher phone = PHONE.matcher(cleaned);
        if (phone.find()) {
            parts.add(phone.group(1));
        } else if (cleaned.matches(".*1[3-9]\\d{9}.*")) {
            Matcher bare = Pattern.compile("1[3-9]\\d{9}").matcher(cleaned);
            if (bare.find()) {
                parts.add(bare.group());
            }
        }

        Matcher email = EMAIL.matcher(cleaned);
        if (email.find()) {
            parts.add(email.group());
        }

        addIfMatched(parts, GITHUB, cleaned);
        addIfMatched(parts, GITEE, cleaned);
        addIfMatched(parts, LINKEDIN, cleaned);

        Matcher city = CITY_LABEL.matcher(cleaned);
        if (city.find()) {
            parts.add(city.group(1).strip());
        }

        Matcher job = JOB_LABEL.matcher(cleaned);
        if (job.find()) {
            parts.add(job.group(1).strip());
        }

        if (parts.isEmpty() && looksLikeContactLine(cleaned)) {
            for (String segment : cleaned.split("[|/]")) {
                String item = segment.strip();
                if (!item.isBlank() && !isKnownSectionTitle(item)) {
                    parts.add(item);
                }
            }
        }
    }

    private static void addIfMatched(LinkedHashSet<String> parts, Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            parts.add(matcher.group());
        }
    }

    private static List<ResumeSection> extractSections(List<String> lines, int startIndex) {
        List<ResumeSection> sections = new ArrayList<>();
        String currentTitle = null;
        StringBuilder currentBody = new StringBuilder();

        for (int i = startIndex; i < lines.size(); i++) {
            String raw = lines.get(i);
            String line = raw.strip();
            if (line.isEmpty()) {
                if (currentTitle != null) {
                    currentBody.append('\n');
                }
                continue;
            }

            String sectionTitle = detectSectionTitle(line);
            if (sectionTitle != null) {
                flushSection(sections, currentTitle, currentBody);
                currentTitle = sectionTitle;
                currentBody = new StringBuilder();
                continue;
            }

            if (currentTitle == null) {
                currentTitle = null;
                currentBody.append(raw).append('\n');
            } else {
                currentBody.append(raw).append('\n');
            }
        }
        flushSection(sections, currentTitle, currentBody);
        return sections;
    }

    private static void flushSection(List<ResumeSection> sections, String title, StringBuilder body) {
        String content = body.toString().strip();
        if (title == null && content.isBlank()) {
            return;
        }
        if (title == null && !content.isBlank()) {
            sections.add(new ResumeSection(null, content));
            return;
        }
        if (title != null) {
            sections.add(new ResumeSection(title, content));
        }
    }

    private static String detectSectionTitle(String line) {
        Matcher heading = MARKDOWN_HEADING.matcher(line);
        if (heading.matches()) {
            return cleanMarkdownText(heading.group(1));
        }
        String plain = cleanMarkdownText(line);
        if (isKnownSectionTitle(plain)) {
            return plain;
        }
        return null;
    }

    private static boolean isKnownSectionTitle(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.strip();
        if (KNOWN_SECTION_TITLES.contains(normalized)) {
            return true;
        }
        for (String known : KNOWN_SECTION_TITLES) {
            if (normalized.startsWith(known)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPlausibleNameLine(String line) {
        String cleaned = cleanMarkdownText(line);
        if (cleaned.isBlank() || cleaned.length() > 24) {
            return false;
        }
        if (cleaned.startsWith("-") || cleaned.startsWith("*") || cleaned.startsWith("|")) {
            return false;
        }
        if (EMAIL.matcher(cleaned).find() || cleaned.matches(".*1[3-9]\\d{9}.*")) {
            return false;
        }
        return !isKnownSectionTitle(cleaned);
    }

    private static boolean looksLikeContactLine(String line) {
        return line.contains("|") || line.contains("/")
                || EMAIL.matcher(line).find()
                || line.matches(".*1[3-9]\\d{9}.*")
                || GITHUB.matcher(line).find()
                || GITEE.matcher(line).find();
    }

    private static String cleanMarkdownText(String text) {
        if (text == null) {
            return "";
        }
        return text.strip()
                .replaceAll("^#+\\s*", "")
                .replaceAll("\\*\\*(.+?)\\*\\*", "$1")
                .replaceAll("\\*(.+?)\\*", "$1")
                .replaceAll("`([^`]+)`", "$1")
                .replaceAll("\\[(.+?)]\\([^)]*\\)", "$1")
                .strip();
    }

    private static String joinLines(List<String> lines, int from, int to) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < to; i++) {
            sb.append(lines.get(i));
            if (i < to - 1) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }
}
