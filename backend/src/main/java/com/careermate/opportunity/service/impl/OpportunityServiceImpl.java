package com.careermate.opportunity.service.impl;

import com.careermate.common.api.PageResult;
import com.careermate.common.api.CacheMeta;
import com.careermate.common.exception.BizException;
import com.careermate.cache.CacheKeys;
import com.careermate.opportunity.converter.ChunksToOpportunityConverter;
import com.careermate.opportunity.dto.OpportunityCitiesVO;
import com.careermate.opportunity.dto.OpportunityDetailVO;
import com.careermate.opportunity.dto.OpportunityListItemVO;
import com.careermate.opportunity.dto.OpportunityListRequest;
import com.careermate.opportunity.dto.OpportunityPrepareResponse;
import com.careermate.opportunity.service.OpportunityService;
import com.careermate.common.catalog.CityCatalog;
import com.careermate.profile.service.CareerProfileService;
import com.careermate.profile.dto.CareerProfileResponse;
import com.careermate.agent.tool.rag.RagRetrieveRequest;
import com.careermate.agent.tool.rag.RagRetrieveResult;
import com.careermate.agent.tool.rag.RagRetrieveScene;
import com.careermate.knowledge.KnowledgeRetrievalService;
import com.careermate.knowledge.KnowledgeRetrievalSupport;
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
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
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
    // 匹配池：一次固定取回上限，与页码解耦，去重后作为稳定 total，分页仅对内存池切片。
    // 上限 150，与 RAGForge /search 放宽后的 topK 上限（≤150）对齐——单次检索能拿到的最大量。
    private static final int OPPORTUNITY_POOL_SIZE = 150;
    private static final int DETAIL_SEARCH_TOP_K = 50;
    private static final Duration DETAIL_CACHE_TTL = Duration.ofMinutes(10);
    private static final String SORT_MATCH = "MATCH";
    private static final String SORT_LATEST = "LATEST";

    private static final List<String> SKILL_KEYWORDS = List.of(
            "Java", "Spring Boot", "Spring Cloud", "MyBatis", "PostgreSQL", "MySQL",
            "Redis", "Elasticsearch", "Docker", "Kubernetes", "RocketMQ", "Kafka",
            "RAG", "向量检索", "AI", "Agent", "Vue", "TypeScript", "算法设计", "信号处理"
    );

    private final KnowledgeRetrievalService knowledgeRetrievalService;
    private final RagForgeClient ragForgeClient;
    private final ResumeService resumeService;
    private final CareerProfileService careerProfileService;
    private final WorkspaceSessionRepository workspaceSessionRepository;
    private final CityCatalog cityCatalog;
    private final ChunksToOpportunityConverter converter = new ChunksToOpportunityConverter();
    private final ObjectMapper objectMapper;
    private final Optional<StringRedisTemplate> redisTemplate;

    @Autowired
    public OpportunityServiceImpl(
            KnowledgeRetrievalService knowledgeRetrievalService,
            RagForgeClient ragForgeClient,
            ResumeService resumeService,
            CareerProfileService careerProfileService,
            WorkspaceSessionRepository workspaceSessionRepository,
            CityCatalog cityCatalog,
            ObjectMapper objectMapper,
            @Autowired(required = false) StringRedisTemplate redisTemplate
    ) {
        this.knowledgeRetrievalService = knowledgeRetrievalService;
        this.ragForgeClient = ragForgeClient;
        this.resumeService = resumeService;
        this.careerProfileService = careerProfileService;
        this.workspaceSessionRepository = workspaceSessionRepository;
        this.cityCatalog = cityCatalog;
        this.objectMapper = objectMapper;
        this.redisTemplate = Optional.ofNullable(redisTemplate);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<OpportunityListItemVO> list(Long userId, OpportunityListRequest request) {
        OpportunityListRequest safeRequest = request == null
                ? new OpportunityListRequest(null, null, null, null, 1, 10)
                : request;
        return computeOpportunityList(userId, safeRequest);
    }

    @Override
    @Transactional(readOnly = true)
    public OpportunityCitiesVO cities(Long userId) {
        String defaultCity = CityCatalog.ANY;
        try {
            CareerProfileResponse profile = careerProfileService.getProfile(userId);
            String targetCity = normalize(profile.getTargetCity());
            if (targetCity != null && cityCatalog.contains(targetCity)) {
                defaultCity = targetCity;
            }
        } catch (Exception e) {
            log.warn("resolve default city from profile failed, userId={}", userId, e);
        }
        return new OpportunityCitiesVO(cityCatalog.cities(), defaultCity);
    }

    public PageResult<OpportunityListItemVO> computeOpportunityList(
            Long userId,
            OpportunityListRequest request
    ) {
        OpportunityListRequest safeRequest = request == null
                ? new OpportunityListRequest(null, null, null, null, 1, 10)
                : request;
        ResumeContext resumeContext = resolveResumeContext(userId);
        ListQueryPlan plan = buildListQueryPlan(userId, safeRequest, resumeContext);
        CachedOpportunityList cached = computeOpportunityListItems(
                plan.cacheKey(),
                plan.query(),
                searchTopKFor(safeRequest)
        );
        if (cached == null) {
            log.info("opportunity list empty from ragforge, userId={}, query={}", userId, plan.query());
            return PageResult.degradedEmpty(safeRequest.page(), safeRequest.size(), resumeContext.hasResume(), SORT_LATEST);
        }
        if (cached.items() == null || cached.items().isEmpty()) {
            log.info("opportunity list empty from ragforge, userId={}, query={}", userId, plan.query());
            CacheMeta.State state = cached.state() == null ? CacheMeta.State.EMPTY : cached.state();
            return new PageResult<>(
                    0,
                    safeRequest.page(),
                    safeRequest.size(),
                    resumeContext.hasResume(),
                    SORT_LATEST,
                    List.of(),
                    metaFromCached(cached, state)
            );
        }

        PageResult<OpportunityListItemVO> result = buildListPage(
                cached.items(),
                resumeContext,
                plan.genericMode(),
                plan.cityFilter(),
                safeRequest.page(),
                safeRequest.size(),
                metaFromCached(cached, CacheMeta.State.FRESH)
        );
        return result;
    }

    public CachedOpportunityList computeOpportunityListItems(String cacheKey, String query, int topK) {
        try {
            RagRetrieveResult result = knowledgeRetrievalService.retrieve(RagRetrieveRequest.builder()
                    .query(query)
                    .scene(RagRetrieveScene.OPPORTUNITY)
                    .topK(topK)
                    .build());
            if (!result.isSuccess()) {
                CacheMeta.State state = KnowledgeRetrievalService.ERROR_EMPTY_RESULTS.equals(result.getErrorCode())
                        ? CacheMeta.State.EMPTY
                        : CacheMeta.State.DEGRADED;
                log.warn(
                        "opportunity list retrieval fallback, key={}, query={}, errorCode={}, state={}",
                        cacheKey,
                        query,
                        result.getErrorCode(),
                        state
                );
                return new CachedOpportunityList(List.of(), System.currentTimeMillis(), state);
            }
            List<RagForgeChunk> chunks = result.getChunks().stream()
                    .map(KnowledgeRetrievalSupport::toRagForgeChunk)
                    .filter(chunk -> chunk != null)
                    .toList();
            if (chunks.isEmpty()) {
                return new CachedOpportunityList(List.of(), System.currentTimeMillis(), CacheMeta.State.EMPTY);
            }
            return new CachedOpportunityList(converter.convert(chunks), System.currentTimeMillis(), CacheMeta.State.FRESH);
        } catch (Exception e) {
            log.warn("compute opportunity list failed, key={}, query={}, err={}", cacheKey, query, e.getMessage());
            return new CachedOpportunityList(List.of(), null, CacheMeta.State.DEGRADED);
        }
    }

    /**
     * 匹配池大小与页码解耦：始终取回固定上限，去重后作为稳定 total，翻页只切内存池。
     * 这样 total 不再随页码变化，也不再被首页的小 topK 卡住条数。
     */
    private int searchTopKFor(OpportunityListRequest request) {
        return OPPORTUNITY_POOL_SIZE;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<OpportunityListItemVO> listCached(Long userId, OpportunityListRequest request) {
        OpportunityListRequest safeRequest = request == null
                ? new OpportunityListRequest(null, null, null, null, 1, 10)
                : request;
        return computeOpportunityList(userId, safeRequest);
    }

    private static CacheMeta metaFromCached(CachedOpportunityList cached, CacheMeta.State fallback) {
        CacheMeta.State state = cached.state() == null ? fallback : cached.state();
        Long cachedAt = cached.cachedAt();
        if (state == CacheMeta.State.EMPTY && cachedAt == null) {
            cachedAt = System.currentTimeMillis();
        }
        return new CacheMeta(state, cachedAt);
    }

    private PageResult<OpportunityListItemVO> buildListPage(
            List<OpportunityListItemVO> items,
            ResumeContext resumeContext,
            boolean genericMode,
            String cityFilter,
            int page,
            int size,
            CacheMeta meta
    ) {
        // 城市精确过滤：对整个匹配池过滤，再打分/排序/切页——total 取过滤后集合，翻页稳定不串城市。
        // 城市已由 JdMarkdownParser 确定性解析进 item.city()；解析不到城市的 JD 在指定城市时排除（不猜）。
        List<OpportunityListItemVO> pool = cityFilter == null
                ? items
                : items.stream()
                        .filter(item -> cityMatches(item.city(), cityFilter))
                        .toList();
        List<OpportunityListItemVO> enriched = pool.stream()
                .map(item -> applyMatch(item, resumeContext))
                .map(item -> genericMode ? asUnmatchedItem(item) : item)
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
                size,
                resumeContext.hasResume(),
                sortStrategy,
                sorted,
                meta
        );
        return paginate(full, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public OpportunityDetailVO detail(Long userId, String jdId) {
        Long docId = parseDocId(jdId);
        String cacheKey = "opportunity:detail:" + userId + ":" + docId;

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
                matched.salaryRange(),
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
        return buildWelcomeContent(jdMeta.company(), jdMeta.title(), jdMeta.skills());
    }

    static String buildWelcomeContent(String companyRaw, String titleRaw, List<String> skillsList) {
        String company = companyRaw == null ? "未知公司" : companyRaw;
        String title = titleRaw == null ? "未知岗位" : titleRaw;
        String skills = topSkills(skillsList);
        // 评审 P0-2：原文案「这岗位重 XXX」缺谓语、技能为空时更显突兀，改为通顺表达
        return "我看到你选了「" + company + " - " + title + "」，\n"
                + "这个岗位比较看重" + skills + "，\n"
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
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, size);
        int fromIndex = (safePage - 1) * safeSize;
        if (fromIndex >= full.items().size()) {
            return new PageResult<>(full.total(), page, size, full.hasResume(), full.sortStrategy(), List.of(), full.meta());
        }
        int toIndex = Math.min(fromIndex + safeSize, full.items().size());
        List<OpportunityListItemVO> sliced = full.items().subList(fromIndex, toIndex);
        return new PageResult<>(full.total(), page, size, full.hasResume(), full.sortStrategy(), sliced, full.meta());
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

    private SearchCriteria resolveSearchCriteria(Long userId, OpportunityListRequest request, String cityFilter) {
        String keyword = normalize(request.keyword());
        if (keyword != null) {
            // 方案B：城市作为「召回偏置」拼入查询串，让固定匹配池(≤150)优先装该城市岗位，
            // 破解"全国 top-150 里某城市只占零头"的天花板；城市准确性仍由 buildListPage 精确过滤兜底（偏置≠过滤）。
            String query = cityFilter == null ? keyword : keyword + " " + cityFilter;
            return new SearchCriteria(cityFilter == null ? "_" : cityFilter, "_", "_", keyword, query);
        }
        String role = normalize(request.position());
        String years = "_";
        if (role == null) {
            try {
                CareerProfileResponse profile = careerProfileService.getProfile(userId);
                role = normalize(profile.getTargetRole());
                years = normalize(profile.getSeniority());
            } catch (Exception e) {
                log.warn("resolve opportunity search criteria from profile failed, userId={}", userId, e);
            }
        }
        List<String> queryParts = new ArrayList<>();
        if (role != null) {
            queryParts.add(role);
        }
        // 方案B：城市回归查询串作召回偏置（仅取下拉显式选择的城市 cityFilter，与精确过滤同源；「不限」时不偏置）。
        if (cityFilter != null) {
            queryParts.add(cityFilter);
        }
        if (years != null && !"_".equals(years)) {
            queryParts.add(years);
        }
        String query = queryParts.isEmpty() ? DEFAULT_QUERY : String.join(" ", queryParts);
        return new SearchCriteria(
                cityFilter == null ? "_" : cityFilter,
                role == null ? "_" : role,
                years == null ? "_" : years,
                "_",
                query
        );
    }

    public String opportunityListCacheKey(Long userId, OpportunityListRequest request) {
        OpportunityListRequest safeRequest = request == null
                ? new OpportunityListRequest(null, null, null, null, 1, 10)
                : request;
        ResumeContext resumeContext = resolveResumeContext(userId);
        return buildListQueryPlan(userId, safeRequest, resumeContext).cacheKey();
    }

    private ListQueryPlan buildListQueryPlan(
            Long userId,
            OpportunityListRequest request,
            ResumeContext resumeContext
    ) {
        // 无简历 = 通用推荐态（不展示个性化匹配）；有简历 = 精准匹配态。查询一律按意向（关键词/城市/岗位/画像）驱动。
        boolean genericMode = !resumeContext.hasResume();
        // 方案B：城市回归查询串(召回偏置)+缓存键，让匹配池优先装该城市岗位；城市准确性由 buildListPage 精确过滤兜底。
        // 切城市 = 换池重召回（list 路径本就每次实时检索、无 Redis 缓存，故无缓存碎片成本）。
        String cityFilter = resolveCityFilter(request.city());
        SearchCriteria criteria = resolveSearchCriteria(userId, request, cityFilter);
        String cacheKey = CacheKeys.opportunityList(
                cityFilter == null ? "_" : cityFilter,
                criteria.role(),
                criteria.years(),
                criteria.keyword()
        );
        return new ListQueryPlan(genericMode, criteria.query(), cacheKey, cityFilter);
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
            return copyItem(item, null, "UNKNOWN", List.of(), List.of());
        }
        List<String> jdSkills = item.skills() == null ? List.of() : item.skills();
        List<String> userSkills = resumeContext.userSkills();
        Set<String> intersection = new LinkedHashSet<>(userSkills);
        intersection.retainAll(jdSkills);
        Set<String> union = new LinkedHashSet<>(userSkills);
        union.addAll(jdSkills);
        // 缺失技能 = JD 要求但用户技能里没有的（保持 JD 中出现顺序）
        List<String> missing = new ArrayList<>();
        for (String s : jdSkills) {
            if (!intersection.contains(s)) {
                missing.add(s);
            }
        }

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
        return copyItem(item, matchScore, tier, reasons, missing);
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
            List<String> matchReasons,
            List<String> missingSkills
    ) {
        return new OpportunityListItemVO(
                item.jdId(),
                item.docId(),
                item.company(),
                item.title(),
                item.level(),
                item.salaryRange(),
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
                missingSkills,
                item.skills(),
                item.ragScore(),
                item.externalUrl(),
                item.unmatched()
        );
    }

    /**
     * 通用推荐态：无画像用户不展示个性化匹配。清空匹配分/技能命中与缺失，
     * 标记 unmatched=true，让前端改为展示「上传简历解锁匹配」而非假匹配。
     */
    private static OpportunityListItemVO asUnmatchedItem(OpportunityListItemVO item) {
        return new OpportunityListItemVO(
                item.jdId(),
                item.docId(),
                item.company(),
                item.title(),
                item.level(),
                item.salaryRange(),
                item.city(),
                item.experienceRange(),
                item.experienceMin(),
                item.experienceMax(),
                item.education(),
                item.companySize(),
                item.publishedAt(),
                null,
                "UNKNOWN",
                List.of(),
                List.of(),
                item.skills(),
                item.ragScore(),
                item.externalUrl(),
                true
        );
    }

    private List<RagForgeChunk> fetchChunksByDocId(Long docId) {
        // 主路径：/search + docIds 精确过滤（API-Key 开放，可取任意 JD，不受岗位类型影响）。
        List<RagForgeChunk> byDocId = filterByDocId(searchOpportunityJdByDocId(docId), docId);
        if (!byDocId.isEmpty()) {
            return byDocId;
        }

        // 兜底1：/documents/{id}/chunks 直取（仅在对该端点有权限时有效）。
        List<RagForgeChunk> direct = ragForgeClient.fetchDocumentChunks(docId);
        if (!direct.isEmpty()) {
            return direct;
        }

        // 兜底2：通用查询搜索后按 docId 过滤（历史行为，覆盖有限）。
        List<RagForgeChunk> chunks = searchOpportunityJd(DEFAULT_QUERY, DETAIL_SEARCH_TOP_K);
        List<RagForgeChunk> filtered = filterByDocId(chunks, docId);
        if (!filtered.isEmpty()) {
            return filtered;
        }
        chunks = searchOpportunityJd("工程师", DETAIL_SEARCH_TOP_K);
        return filterByDocId(chunks, docId);
    }

    private List<RagForgeChunk> searchOpportunityJd(String query, int topK) {
        var result = knowledgeRetrievalService.retrieve(RagRetrieveRequest.builder()
                .query(query)
                .scene(RagRetrieveScene.OPPORTUNITY)
                .topK(topK)
                .build());
        if (!result.isSuccess()) {
            return List.of();
        }
        return result.getChunks().stream()
                .map(KnowledgeRetrievalSupport::toRagForgeChunk)
                .filter(chunk -> chunk != null)
                .toList();
    }

    /** 按 docId 精确取某 JD 的 chunks（经统一检索层 → /search + docIds 过滤，API-Key 可用）。 */
    private List<RagForgeChunk> searchOpportunityJdByDocId(Long docId) {
        var result = knowledgeRetrievalService.retrieve(RagRetrieveRequest.builder()
                .query("岗位职责")
                .scene(RagRetrieveScene.OPPORTUNITY)
                .topK(DETAIL_SEARCH_TOP_K)
                .docIds(List.of(docId))
                .build());
        if (!result.isSuccess()) {
            return List.of();
        }
        return result.getChunks().stream()
                .map(KnowledgeRetrievalSupport::toRagForgeChunk)
                .filter(chunk -> chunk != null)
                .toList();
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

    private OpportunityDetailVO readDetailCache(String cacheKey) {
        return readCache(cacheKey, new TypeReference<>() {});
    }

    private <T> T readCache(String cacheKey, TypeReference<T> type) {
        if (redisTemplate.isEmpty()) {
            return null;
        }
        try {
            ValueOperations<String, String> ops = redisTemplate.get().opsForValue();
            if (ops == null) {
                return null;
            }
            String json = ops.get(cacheKey);
            if (json == null || json.isBlank()) {
                return null;
            }
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.warn("read opportunity cache failed, key={}", cacheKey, e);
            return null;
        }
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
            ValueOperations<String, String> ops = redisTemplate.get().opsForValue();
            if (ops == null) {
                return;
            }
            ops.set(cacheKey, json, ttl);
        } catch (Exception e) {
            log.warn("write opportunity cache failed, key={}", cacheKey, e);
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * 解析城市硬过滤条件：取用户在下拉里的显式选择（request.city）。
     * 「不限」/空 返回 null（不过滤）；不回退画像目标城市，避免用户选「不限」后仍被过滤。
     */
    private String resolveCityFilter(String requestedCity) {
        String city = normalize(requestedCity);
        if (city == null || CityCatalog.ANY.equals(city)) {
            return null;
        }
        return city;
    }

    /**
     * 城市等值匹配（归一化）：去首尾空白、去「市」后缀、忽略大小写。
     * 目录值（如「广州」）与解析值（正文/文件名前缀，如「广州」/「广州市」）据此对齐。
     */
    private static boolean cityMatches(String itemCity, String targetCity) {
        String a = normalizeCityForMatch(itemCity);
        String b = normalizeCityForMatch(targetCity);
        return a != null && a.equals(b);
    }

    private static String normalizeCityForMatch(String city) {
        if (city == null) {
            return null;
        }
        String trimmed = city.trim();
        if (trimmed.endsWith("市")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.isEmpty() ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    private record SearchCriteria(String city, String role, String years, String keyword, String query) {
    }

    private record ListQueryPlan(boolean genericMode, String query, String cacheKey, String cityFilter) {
    }

    public record CachedOpportunityList(List<OpportunityListItemVO> items, Long cachedAt, CacheMeta.State state) {
        public CachedOpportunityList(List<OpportunityListItemVO> items, Long cachedAt) {
            this(items, cachedAt, items == null || items.isEmpty() ? CacheMeta.State.EMPTY : CacheMeta.State.FRESH);
        }
    }

    private record ResumeContext(boolean hasResume, List<String> userSkills) {
        static ResumeContext none() {
            return new ResumeContext(false, List.of());
        }
    }
}
