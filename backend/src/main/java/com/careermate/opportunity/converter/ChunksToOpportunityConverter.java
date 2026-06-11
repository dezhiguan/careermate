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

        ParsedJd parsed = jdMarkdownParser.parse(mergedContent);
        Long docId = sorted.get(0).docId();
        String filename = sorted.get(0).filename();
        Double ragScore = sorted.stream()
                .map(RagForgeChunk::finalScore)
                .filter(score -> score != null)
                .max(Double::compareTo)
                .orElse(null);

        String title = parsed.title() != null ? parsed.title() : filename;
        String publishedAt = parsed.publishedAt() == null ? null : parsed.publishedAt().toString();

        return new OpportunityListItemVO(
                "doc-" + docId,
                docId,
                parsed.company(),
                title,
                parsed.level(),
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
                null
        );
    }
}
