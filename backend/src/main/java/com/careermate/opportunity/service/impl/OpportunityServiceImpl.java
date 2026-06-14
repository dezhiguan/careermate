package com.careermate.opportunity.service.impl;

import com.careermate.common.api.PageResult;
import com.careermate.common.exception.BizException;
import com.careermate.opportunity.cache.OpportunityCacheKeys;
import com.careermate.opportunity.converter.ChunksToOpportunityConverter;
import com.careermate.opportunity.dto.OpportunityDetailVO;
import com.careermate.opportunity.dto.OpportunityListItemVO;
import com.careermate.opportunity.dto.OpportunityListRequest;
import com.careermate.opportunity.dto.OpportunityPrepareResponse;
import com.careermate.opportunity.service.OpportunityService;
import com.careermate.profile.service.CareerProfileService;
import com.careermate.profile.dto.CareerProfileResponse;
import com.careermate.ragforge.RagForgeChunk;
import com.careermate.ragforge.RagForgeClient;
import com.careermate.model.entity.AgentSessionEntity;
import com.careermate.resume.service.ResumeService;
import com.careermate.workspace.support.WorkspaceSessionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OpportunityServiceImpl implements OpportunityService {

    private static final String DEFAULT_QUERY = "Java 后端";
    private static final int SEARCH_TOP_K = 30;
    private static final int DETAIL_SEARCH_TOP_K = 50;
    private static final Duration LIST_CACHE_TTL = Duration.ofMinutes(5);
    private static final Duration DETAIL_CACHE_TTL = Duration.ofMinutes(10);
    private static final String SORT_MATCH = "MATCH";
    private static final String SORT_LATEST = "LATEST";

    private static final List<String> SKILL_KEYWORDS = List.of(
            "Java", "Spring Boot", "Spring Cloud", "MyBatis", "PostgreSQL", "MySQL",
            "Redis", "Elasticsearch", "Docker", "Kubernetes", "RocketMQ", "Kafka",
            "RAG", "向量检索", "AI", "Agent", "Vue", "TypeScript", "算法设计", "信号处理"
    );

    private final RagForgeClient ragForgeClient;
    private final ResumeService resumeService;
    private final CareerProfileService careerProfileService;
    private final WorkspaceSessionRepository workspaceSessionRepository;
    private final ChunksToOpportunityConverter converter = new ChunksToOpportunityConverter();
    private final ObjectMapper objectMapper;
    private final Optional<StringRedisTemplate> redisTemplate;

    public OpportunityServiceImpl(
            RagForgeClient ragForgeClient,
            ResumeService resumeService,
            CareerProfileService careerProfileService,
            WorkspaceSessionRepository workspaceSessionRepository,
            ObjectMapper objectMapper,
            @Autowired(required = false) StringRedisTemplate redisTemplate
    ) {
        this.ragForgeClient = ragForgeClient;
        this.resumeService = resumeService;
        this.careerProfileService = careerProfileService;
        this.workspaceSessionRepository = workspaceSessionRepository;
        this.objectMapper = objectMapper;
        this.redisTemplate = Optional.ofNullable(redisTemplate);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<OpportunityListItemVO> list(Long userId, OpportunityListRequest request) {
        OpportunityListRequest safeRequest = request == null
                ? new OpportunityListRequest(null, null, null, 1, 10)
                : request;
        String query = resolveQuery(userId, safeRequest.keyword());
        String cacheKey = OpportunityCacheKeys.listKey(userId, sha256Hex(query));

        PageResult<OpportunityListItemVO> cached = readListCache(cacheKey);
        if (cached != null) {
            log.info("opportunity list cache hit, userId={}, queryHash={}", userId, sha256Hex(query));
            return paginate(cached, safeRequest.page(), safeRequest.size());
        }

        List<RagForgeChunk> chunks = ragForgeClient.searchJd(query, SEARCH_TOP_K);
        if (chunks.isEmpty()) {
            log.info("opportunity list empty from ragforge, userId={}, query={}", userId, query);
            return PageResult.empty(safeRequest.page(), safeRequest.size(), false, SORT_LATEST);
        }

        List<OpportunityListItemVO> items = converter.convert(chunks);
        ResumeContext resumeContext = resolveResumeContext(userId);
        List<OpportunityListItemVO> enriched = items.stream()
                .map(item -> applyMatch(item, resumeContext))
                .toList();

        String sortStrategy;
        List<OpportunityListItemVO> sorted;
        if (resumeContext.hasResume()) {
            sorted = enriched.stream()
                    .sorted(Comparator.comparing(
                            OpportunityListItemVO::matchScore,
                            Comparator.nullsLast(Comparator.reverseOrder())
                    ))
                    .toList();
            sortStrategy = SORT_MATCH;
        } else {
            sorted = enriched.stream()
                    .sorted(Comparator.comparing(
                            OpportunityListItemVO::ragScore,
                            Comparator.nullsLast(Comparator.reverseOrder())
                    ))
                    .toList();
            sortStrategy = SORT_LATEST;
        }

        PageResult<OpportunityListItemVO> full = new PageResult<>(
                sorted.size(),
                1,
                safeRequest.size(),
                resumeContext.hasResume(),
                sortStrategy,
                sorted
        );
        writeListCache(cacheKey, full);
        return paginate(full, safeRequest.page(), safeRequest.size());
    }

    @Override
    @Transactional(readOnly = true)
    public OpportunityDetailVO detail(Long userId, String jdId) {
        Long docId = parseDocId(jdId);
        String cacheKey = OpportunityCacheKeys.detailKey(userId, docId);

        OpportunityDetailVO cached = readDetailCache(cacheKey);
        if (cached != null) {
            log.info("opportunity detail cache hit, userId={}, docId={}", userId, docId);
            return cached;
        }

        List<RagForgeChunk> chunks = fetchChunksByDocId(docId);
        if (chunks.isEmpty()) {
            throw new BizException(404, "JD 不存在或已下架");
        }

        List<OpportunityListItemVO> converted = converter.convert(chunks);
        OpportunityListItemVO base = converted.get(0);
        ResumeContext resumeContext = resolveResumeContext(userId);
        OpportunityListItemVO matched = applyMatch(base, resumeContext);
        String jdContent = mergeChunkContent(chunks);

        OpportunityDetailVO detail = new OpportunityDetailVO(
                matched.jdId(),
                matched.docId(),
                matched.company(),
                matched.title(),
                matched.level(),
                matched.city(),
                matched.experienceRange(),
                matched.experienceMin(),
                matched.experienceMax(),
                matched.education(),
                matched.companySize(),
                matched.publishedAt(),
                matched.matchScore(),
                matched.matchTier(),
                matched.matchReasons(),
                matched.skills(),
                matched.ragScore(),
                matched.externalUrl(),
                jdContent,
                null,
                null,
                null
        );
        writeDetailCache(cacheKey, detail);
        return detail;
    }

    @Override
    @Transactional
    public OpportunityPrepareResponse prepare(Long userId, String jdId) {
        Long docId = parseDocId(jdId);
        String docIdStr = jdId.trim();

        AgentSessionEntity existing = workspaceSessionRepository.findActiveJdPrepSession(userId, docIdStr);
        if (existing != null) {
            return new OpportunityPrepareResponse(existing.getSessionId(), "/chat/" + existing.getSessionId());
        }

        List<RagForgeChunk> chunks = fetchChunksByDocId(docId);
        if (chunks.isEmpty()) {
            throw new BizException(404, "JD 不存在或已下架");
        }

        List<OpportunityListItemVO> converted = converter.convert(chunks);
        OpportunityListItemVO jdMeta = converted.get(0);
        String title = buildWorkspaceTitle(jdMeta.company(), jdMeta.title());
        String jdContent = mergeChunkContent(chunks);
        String jdSnapshotJson = buildJdSnapshotJson(jdMeta, jdContent);

        AgentSessionEntity session = workspaceSessionRepository.createJdPrepSession(
                userId, docIdStr, jdSnapshotJson, title
        );

        String welcomeContent = buildWelcomeContent(jdMeta);
        String welcomeMetadata = buildWelcomeMetadata(docIdStr);

        workspaceSessionRepository.appendMessage(
                userId,
                session,
                "assistant",
                welcomeContent,
                "CARD",
                welcomeMetadata,
                1
        );

        return new OpportunityPrepareResponse(session.getSessionId(), "/chat/" + session.getSessionId());
    }

    private static String buildWorkspaceTitle(String company, String position) {
        if (company != null && !company.isBlank() && position != null && !position.isBlank()) {
            return company.trim() + " " + position.trim();
        }
        if (position != null && !position.isBlank()) {
            return position.trim();
        }
        return "JD 准备空间";
    }

    private String buildJdSnapshotJson(OpportunityListItemVO jdMeta, String jdContent) {
        try {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("company", jdMeta.company());
            snapshot.put("title", jdMeta.title());
            snapshot.put("city", jdMeta.city());
            snapshot.put("experienceRange", jdMeta.experienceRange());
            snapshot.put("education", jdMeta.education());
            snapshot.put("skills", jdMeta.skills() == null ? List.of() : jdMeta.skills());
            if (jdContent != null && !jdContent.isBlank()) {
                snapshot.put("jdContent", jdContent);
            }
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            log.warn("build jd snapshot failed: {}", e.getMessage());
            return "{}";
        }
    }

    private static String buildWelcomeContent(OpportunityListItemVO jdMeta) {
        String company = jdMeta.company() == null ? "未知公司" : jdMeta.company();
        String title = jdMeta.title() == null ? "未知岗位" : jdMeta.title();
        String skills = topSkills(jdMeta.skills());
        return "我看到你选了「" + company + " - " + title + "」，\n"
                + "这岗位重 " + skills + "，\n"
                + "要不要我帮你按这个 JD 重写一版简历?";
    }

    private static String topSkills(List<String> skills) {
        if (skills == null || skills.isEmpty()) {
            return "相关技能";
        }
        return skills.stream().limit(3).collect(Collectors.joining("、"));
    }

    private String buildWelcomeMetadata(String docIdStr) {
        try {
            Map<String, Object> card = new LinkedHashMap<>();
            card.put("type", "OFFER_GENERATE_RESUME");
            card.put("jdId", docIdStr);
            card.put("actions", List.of(
                    Map.of("label", "好,帮我改", "action", "GENERATE_RESUME", "payload", docIdStr),
                    Map.of("label", "我先看看 JD", "action", "VIEW_JD", "payload", docIdStr)
            ));
            return objectMapper.writeValueAsString(Map.of("card", card));
        } catch (Exception e) {
            log.warn("build welcome metadata failed: {}", e.getMessage());
            return "{}";
        }
    }

    private PageResult<OpportunityListItemVO> paginate(
            PageResult<OpportunityListItemVO> full,
            int page,
            int size
    ) {
        if (page > 1) {
            return new PageResult<>(full.total(), page, size, full.hasResume(), full.sortStrategy(), List.of());
        }
        List<OpportunityListItemVO> sliced = full.items().stream().limit(size).toList();
        return new PageResult<>(full.total(), page, size, full.hasResume(), full.sortStrategy(), sliced);
    }

    private String resolveQuery(Long userId, String keyword) {
        if (keyword != null && !keyword.isBlank()) {
            return keyword.trim();
        }
        try {
            CareerProfileResponse profile = careerProfileService.getProfile(userId);
            String role = profile.getTargetRole();
            String city = profile.getTargetCity();
            List<String> parts = new ArrayList<>();
            if (role != null && !role.isBlank()) {
                parts.add(role.trim());
            }
            if (city != null && !city.isBlank()) {
                parts.add(city.trim());
            }
            if (!parts.isEmpty()) {
                return String.join(" ", parts);
            }
        } catch (Exception e) {
            log.warn("resolve query from profile failed, userId={}", userId, e);
        }
        return DEFAULT_QUERY;
    }

    private ResumeContext resolveResumeContext(Long userId) {
        try {
            Optional<com.careermate.model.entity.ResumeEntity> resumeOpt =
                    resumeService.getDefaultActiveResume(userId);
            if (resumeOpt.isEmpty()) {
                return ResumeContext.none();
            }
            String content = resumeOpt.get().getContent();
            List<String> skills = detectSkills(content);
            if (skills.isEmpty()) {
                CareerProfileResponse profile = careerProfileService.getProfile(userId);
                if (profile.getSkillKeywords() != null && !profile.getSkillKeywords().isEmpty()) {
                    skills = profile.getSkillKeywords();
                }
            }
            return new ResumeContext(true, skills);
        } catch (Exception e) {
            log.warn("resolve resume context failed, userId={}", userId, e);
            return ResumeContext.none();
        }
    }

    private OpportunityListItemVO applyMatch(OpportunityListItemVO item, ResumeContext resumeContext) {
        if (!resumeContext.hasResume()) {
            return copyItem(item, null, "UNKNOWN", List.of());
        }
        List<String> jdSkills = item.skills() == null ? List.of() : item.skills();
        List<String> userSkills = resumeContext.userSkills();
        Set<String> intersection = new LinkedHashSet<>(userSkills);
        intersection.retainAll(jdSkills);
        Set<String> union = new LinkedHashSet<>(userSkills);
        union.addAll(jdSkills);

        int baseScore = (int) Math.floor(60.0 * intersection.size() / Math.max(union.size(), 1));
        int ragBonus = item.ragScore() == null ? 0 : (int) Math.floor(40.0 * item.ragScore());
        int matchScore = Math.min(100, baseScore + ragBonus);
        String tier = resolveTier(matchScore);

        List<String> reasons = new ArrayList<>();
        if (!intersection.isEmpty()) {
            reasons.add("技能命中 " + String.join(", ", intersection));
        }
        if (item.ragScore() != null) {
            reasons.add(String.format(Locale.ROOT, "RAG 相关度 %.2f", item.ragScore()));
        }
        return copyItem(item, matchScore, tier, reasons);
    }

    private static String resolveTier(int matchScore) {
        if (matchScore >= 85) {
            return "HIGH";
        }
        if (matchScore >= 70) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private static OpportunityListItemVO copyItem(
            OpportunityListItemVO item,
            Integer matchScore,
            String matchTier,
            List<String> matchReasons
    ) {
        return new OpportunityListItemVO(
                item.jdId(),
                item.docId(),
                item.company(),
                item.title(),
                item.level(),
                item.city(),
                item.experienceRange(),
                item.experienceMin(),
                item.experienceMax(),
                item.education(),
                item.companySize(),
                item.publishedAt(),
                matchScore,
                matchTier,
                matchReasons,
                item.skills(),
                item.ragScore(),
                item.externalUrl()
        );
    }

    private List<RagForgeChunk> fetchChunksByDocId(Long docId) {
        List<RagForgeChunk> direct = ragForgeClient.fetchDocumentChunks(docId);
        if (!direct.isEmpty()) {
            return direct;
        }

        List<RagForgeChunk> chunks = ragForgeClient.searchJd(DEFAULT_QUERY, DETAIL_SEARCH_TOP_K);
        List<RagForgeChunk> filtered = filterByDocId(chunks, docId);
        if (!filtered.isEmpty()) {
            return filtered;
        }
        chunks = ragForgeClient.searchJd("工程师", DETAIL_SEARCH_TOP_K);
        return filterByDocId(chunks, docId);
    }

    private static List<RagForgeChunk> filterByDocId(List<RagForgeChunk> chunks, Long docId) {
        return chunks.stream()
                .filter(chunk -> docId.equals(chunk.docId()))
                .toList();
    }

    private static String mergeChunkContent(List<RagForgeChunk> chunks) {
        return chunks.stream()
                .sorted(Comparator.comparing(RagForgeChunk::chunkId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(RagForgeChunk::content)
                .filter(content -> content != null && !content.isBlank())
                .collect(Collectors.joining("\n"));
    }

    static Long parseDocId(String jdId) {
        if (jdId == null || !jdId.startsWith("doc-")) {
            throw new BizException(400, "非法 JD ID");
        }
        String raw = jdId.substring(4).trim();
        if (raw.isEmpty()) {
            throw new BizException(400, "非法 JD ID");
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw new BizException(400, "非法 JD ID");
        }
    }

    private List<String> detectSkills(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String lower = text.toLowerCase(Locale.ROOT);
        Set<String> found = new LinkedHashSet<>();
        for (String skill : SKILL_KEYWORDS) {
            if (skill.chars().anyMatch(c -> c > 127)) {
                if (text.contains(skill)) {
                    found.add(skill);
                }
            } else if (lower.contains(skill.toLowerCase(Locale.ROOT))) {
                found.add(skill);
            }
        }
        return new ArrayList<>(found);
    }

    private PageResult<OpportunityListItemVO> readListCache(String cacheKey) {
        return readCache(cacheKey, new TypeReference<>() {});
    }

    private OpportunityDetailVO readDetailCache(String cacheKey) {
        return readCache(cacheKey, new TypeReference<>() {});
    }

    private <T> T readCache(String cacheKey, TypeReference<T> type) {
        if (redisTemplate.isEmpty()) {
            return null;
        }
        try {
            String json = redisTemplate.get().opsForValue().get(cacheKey);
            if (json == null || json.isBlank()) {
                return null;
            }
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.warn("read opportunity cache failed, key={}", cacheKey, e);
            return null;
        }
    }

    private void writeListCache(String cacheKey, PageResult<OpportunityListItemVO> value) {
        writeCache(cacheKey, value, LIST_CACHE_TTL);
    }

    private void writeDetailCache(String cacheKey, OpportunityDetailVO value) {
        writeCache(cacheKey, value, DETAIL_CACHE_TTL);
    }

    private void writeCache(String cacheKey, Object value, Duration ttl) {
        if (redisTemplate.isEmpty()) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(value);
            redisTemplate.get().opsForValue().set(cacheKey, json, ttl);
        } catch (Exception e) {
            log.warn("write opportunity cache failed, key={}", cacheKey, e);
        }
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(input.hashCode());
        }
    }

    private record ResumeContext(boolean hasResume, List<String> userSkills) {
        static ResumeContext none() {
            return new ResumeContext(false, List.of());
        }
    }
}
