package com.careermate.opportunity.converter;

import com.careermate.opportunity.dto.OpportunityListItemVO;
import com.careermate.opportunity.dto.ParsedJd;
import com.careermate.opportunity.parser.JdMarkdownParser;
import com.careermate.ragforge.RagForgeChunk;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ChunksToOpportunityConverter {

    private final JdMarkdownParser jdMarkdownParser = new JdMarkdownParser();

    public List<OpportunityListItemVO> convert(List<RagForgeChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }

        Map<Long, List<RagForgeChunk>> grouped = chunks.stream()
                .filter(chunk -> chunk.docId() != null)
                .collect(Collectors.groupingBy(RagForgeChunk::docId));

        return grouped.values().stream()
                .map(this::toListItem)
                .sorted(Comparator.comparing(OpportunityListItemVO::ragScore, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private OpportunityListItemVO toListItem(List<RagForgeChunk> docChunks) {
        List<RagForgeChunk> sorted = docChunks.stream()
                .sorted(Comparator.comparing(RagForgeChunk::chunkId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        String mergedContent = sorted.stream()
                .map(RagForgeChunk::content)
                .filter(content -> content != null && !content.isBlank())
                .collect(Collectors.joining("\n"));

        Long docId = sorted.get(0).docId();
        String filename = sorted.get(0).filename();
        // 语义检索常只召回文档部分 chunk，头部（公司/标题元信息）可能缺失，
        // 因此以文件名作为兜底解析公司/城市/岗位，避免前端出现「未知公司」。
        ParsedJd parsed = jdMarkdownParser.parse(mergedContent, filename);
        Double ragScore = sorted.stream()
                .map(RagForgeChunk::finalScore)
                .filter(score -> score != null)
                .max(Double::compareTo)
                .orElse(null);

        String title = sanitizeTitle(parsed.title() != null ? parsed.title() : filename);
        String publishedAt = parsed.publishedAt() == null ? null : parsed.publishedAt().toString();

        return new OpportunityListItemVO(
                "doc-" + docId,
                docId,
                parsed.company(),
                title,
                parsed.level(),
                parsed.salaryRange(),
                parsed.city(),
                parsed.experienceRange(),
                parsed.experienceMin(),
                parsed.experienceMax(),
                parsed.education(),
                parsed.companySize(),
                publishedAt,
                null,
                null,
                List.of(),
                parsed.skills(),
                ragScore,
                null,
                false
        );
    }

    /**
     * 清洗对用户展示的岗位标题（评审 P0-2）：去掉 .md 文件后缀、开头的 markdown 标题符号，
     * 以及 {@code 【JD】/【J0】} 之类内部标记，避免把源文件名/内部编号泄露到界面。
     */
    static String sanitizeTitle(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.trim().replaceFirst("^#+\\s*", "");
        // 剥离开头连续的【…】内部标记
        while (cleaned.startsWith("【")) {
            int end = cleaned.indexOf('】');
            if (end < 0) {
                break;
            }
            cleaned = cleaned.substring(end + 1).trim();
        }
        // 去掉结尾的 .md 文件后缀（忽略大小写，允许前置空格）
        cleaned = cleaned.replaceFirst("(?i)\\s*\\.md$", "");
        return cleaned.trim();
    }
}
