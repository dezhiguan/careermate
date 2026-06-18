package com.careermate.market.service;

import com.careermate.agent.tool.rag.RagRetrieveRequest;
import com.careermate.agent.tool.rag.RagRetrieveResult;
import com.careermate.agent.tool.rag.RagRetrieveScene;
import com.careermate.cache.CacheKeys;
import com.careermate.common.api.CacheMeta;
import com.careermate.knowledge.KnowledgeRetrievalService;
import com.careermate.knowledge.KnowledgeRetrievalSupport;
import com.careermate.llm.LlmClient;
import com.careermate.llm.dto.ChatMessage;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.dto.ChatResponse;
import com.careermate.market.MarketPrompts;
import com.careermate.market.dto.CompanyInsightVO;
import com.careermate.market.dto.ResumeGapVO;
import com.careermate.market.dto.SalaryInsightVO;
import com.careermate.market.dto.SkillTrendsVO;
import com.careermate.resume.ResumeContext;
import com.careermate.resume.ResumeContextProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MarketIntelligenceService {

    private static final int MAX_CONTEXT_CHARS = 4000;
    private static final String FALLBACK_SUMMARY = "暂时无法获取市场数据，请稍后再试";
    private static final String NO_DATA = "暂无数据";
    private static final Pattern JSON_BLOCK = Pattern.compile("\\{[\\s\\S]*\\}");

    private final KnowledgeRetrievalService knowledgeRetrievalService;
    private final LlmClient llmClient;
    private final ResumeContextProvider resumeContextProvider;
    private final ObjectMapper objectMapper;
    private final CacheManager cacheManager;
    private final ApplicationContext applicationContext;

    /** Unit-test constructor: no Spring proxy/cache, keep synchronous behavior. */
    public MarketIntelligenceService(
            KnowledgeRetrievalService knowledgeRetrievalService,
            LlmClient llmClient,
            ResumeContextProvider resumeContextProvider,
            ObjectMapper objectMapper
    ) {
        this(knowledgeRetrievalService, llmClient, resumeContextProvider, objectMapper, null, null);
    }

    @Autowired
    public MarketIntelligenceService(
            KnowledgeRetrievalService knowledgeRetrievalService,
            LlmClient llmClient,
            ResumeContextProvider resumeContextProvider,
            ObjectMapper objectMapper,
            ObjectProvider<CacheManager> cacheManagerProvider,
            ApplicationContext applicationContext
    ) {
        this.knowledgeRetrievalService = knowledgeRetrievalService;
        this.llmClient = llmClient;
        this.resumeContextProvider = resumeContextProvider;
        this.objectMapper = objectMapper;
        this.cacheManager = cacheManagerProvider == null ? null : cacheManagerProvider.getIfAvailable();
        this.applicationContext = applicationContext;
    }

    public SalaryInsightVO getSalaryInsight(String role, String city, String years) {
        String safeRole = defaultText(role, "Java后端");
        String safeCity = defaultText(city, "广州");
        String safeYears = defaultText(years, "3-5年");
        if (!cacheFacadeEnabled()) {
            return computeSalaryInsight(safeCity, safeRole, safeYears);
        }
        String cacheKey = CacheKeys.marketSalary(safeCity, safeRole, safeYears);
        SalaryInsightVO cached = cacheValue("market:salary", cacheKey, SalaryInsightVO.class);
        if (cached != null) {
            ensureFreshMeta(cached);
            return cached;
        }
        refreshAsync(() -> self().computeSalaryInsight(safeCity, safeRole, safeYears));
        return degradedSalaryInsight();
    }

    public SkillTrendsVO getSkillTrends(String role) {
        return getSkillTrends("广州", role);
    }

    public SkillTrendsVO getSkillTrends(String city, String role) {
        String safeCity = defaultText(city, "广州");
        String safeRole = defaultText(role, "Java后端");
        if (!cacheFacadeEnabled()) {
            return computeSkillTrends(safeCity, safeRole);
        }
        String cacheKey = CacheKeys.marketSkillTrends(safeCity, safeRole);
        SkillTrendsVO cached = cacheValue("market:skill-trends", cacheKey, SkillTrendsVO.class);
        if (cached != null) {
            ensureFreshMeta(cached);
            return cached;
        }
        refreshAsync(() -> self().computeSkillTrends(safeCity, safeRole));
        return degradedSkillTrends();
    }

    public ResumeGapVO getResumeGap(Long userId) {
        return getResumeGap(userId, "default");
    }

    public ResumeGapVO getResumeGap(Long userId, String jdId) {
        String safeJdId = defaultText(jdId, "default");
        if (!cacheFacadeEnabled()) {
            return computeResumeGap(userId, safeJdId);
        }
        String cacheKey = CacheKeys.marketResumeGap(userId, safeJdId);
        ResumeGapVO cached = cacheValue("market:resume-gap", cacheKey, ResumeGapVO.class);
        if (cached != null) {
            ensureFreshMeta(cached);
            return cached;
        }
        refreshAsync(() -> self().computeResumeGap(userId, safeJdId));
        return degradedResumeGap();
    }

    @Cacheable(
            cacheNames = "market:salary",
            key = "T(com.careermate.cache.CacheKeys).marketSalary(#city, #role, #years)",
            unless = "#result.meta != null && #result.meta.degraded"
    )
    public SalaryInsightVO computeSalaryInsight(String city, String role, String years) {
        try {
            String query = role + " " + city + " " + years + " 薪资 月薪";
            RagRetrieveResult ragResult = knowledgeRetrievalService.retrieve(RagRetrieveRequest.builder()
                    .query(query)
                    .scene(RagRetrieveScene.MARKET)
                    .topK(30)
                    .build());
            String context = toContextText(ragResult);
            if (context.isBlank()) {
                log.warn("getSalaryInsight: empty rag context, role={}, city={}", role, city);
                return fallbackSalaryInsight();
            }
            String prompt = MarketPrompts.salaryPrompt(role, city, years, context);
            SalaryInsightVO parsed = parseLlmJson(prompt, SalaryInsightVO.class);
            if (parsed == null) {
                return fallbackSalaryInsight();
            }
            attachSources(parsed, ragResult);
            parsed.setMeta(CacheMeta.freshNow());
            return parsed;
        } catch (Exception e) {
            log.warn("getSalaryInsight failed: {}", e.getMessage());
            return fallbackSalaryInsight();
        }
    }

    @Cacheable(
            cacheNames = "market:skill-trends",
            key = "T(com.careermate.cache.CacheKeys).marketSkillTrends(#city, #role)",
            unless = "#result.meta != null && #result.meta.degraded"
    )
    public SkillTrendsVO computeSkillTrends(String city, String role) {
        try {
            String query = role + " " + city + " 技能要求 技术栈 必备";
            RagRetrieveResult ragResult = knowledgeRetrievalService.retrieve(RagRetrieveRequest.builder()
                    .query(query)
                    .scene(RagRetrieveScene.MARKET)
                    .topK(40)
                    .build());
            String context = toContextText(ragResult);
            if (context.isBlank()) {
                log.warn("getSkillTrends: empty rag context, role={}", role);
                return fallbackSkillTrends();
            }
            String prompt = MarketPrompts.skillTrendsPrompt(role, context);
            SkillTrendsVO parsed = parseLlmJson(prompt, SkillTrendsVO.class);
            if (parsed == null) {
                return fallbackSkillTrends();
            }
            attachSources(parsed, ragResult);
            parsed.setMeta(CacheMeta.freshNow());
            return parsed;
        } catch (Exception e) {
            log.warn("getSkillTrends failed: {}", e.getMessage());
            return fallbackSkillTrends();
        }
    }

    @Cacheable(
            cacheNames = "market:resume-gap",
            key = "T(com.careermate.cache.CacheKeys).marketResumeGap(#userId, #jdId)",
            unless = "#result.meta != null && #result.meta.degraded"
    )
    public ResumeGapVO computeResumeGap(Long userId, String jdId) {
        try {
            ResumeContext resumeContext = resumeContextProvider.getResumeContext(userId);
            if (!resumeContext.isAvailable()
                    || resumeContext.getContent() == null
                    || resumeContext.getContent().isBlank()) {
                return emptyResumeGap();
            }
            String role = extractRoleFromResume(resumeContext);
            String query = role + " 岗位要求 技术能力";
            RagRetrieveResult ragResult = knowledgeRetrievalService.retrieve(RagRetrieveRequest.builder()
                    .query(query)
                    .scene(RagRetrieveScene.OPPORTUNITY)
                    .topK(30)
                    .build());
            String context = toContextText(ragResult);
            if (context.isBlank()) {
                log.warn("getResumeGap: empty rag context, userId={}, role={}", userId, role);
                return fallbackResumeGap();
            }
            String prompt = MarketPrompts.resumeGapPrompt(resumeContext.getContent(), context);
            ResumeGapVO parsed = parseLlmJson(prompt, ResumeGapVO.class);
            if (parsed == null) {
                return fallbackResumeGap();
            }
            attachSources(parsed, ragResult);
            parsed.setMeta(CacheMeta.freshNow());
            return parsed;
        } catch (Exception e) {
            log.warn("getResumeGap failed: userId={}, err={}", userId, e.getMessage());
            return fallbackResumeGap();
        }
    }

    public CompanyInsightVO getCompanyInsight(String company) {
        try {
            if (company == null || company.isBlank()) {
                log.warn("getCompanyInsight: company is blank");
                return fallbackCompanyInsight("");
            }
            String safeCompany = company.trim();
            RagRetrieveResult ragResult = knowledgeRetrievalService.retrieveMerged(List.of(
                    RagRetrieveRequest.builder()
                            .query(safeCompany + " 公司 技术栈 规模")
                            .scene(RagRetrieveScene.COMPANY)
                            .topK(20)
                            .build(),
                    RagRetrieveRequest.builder()
                            .query(safeCompany + " 岗位 招聘")
                            .scene(RagRetrieveScene.COMPANY)
                            .topK(10)
                            .build()
            ));
            String context = toContextText(ragResult);
            if (context.isBlank()) {
                log.warn("getCompanyInsight: empty rag context, company={}", safeCompany);
                return fallbackCompanyInsight(safeCompany);
            }
            String prompt = MarketPrompts.companyPrompt(safeCompany, context);
            CompanyInsightVO parsed = parseLlmJson(prompt, CompanyInsightVO.class);
            if (parsed == null) {
                return fallbackCompanyInsight(safeCompany);
            }
            if (parsed.getCompanyName() == null || parsed.getCompanyName().isBlank()) {
                parsed.setCompanyName(safeCompany);
            }
            attachSources(parsed, ragResult);
            return parsed;
        } catch (Exception e) {
            log.warn("getCompanyInsight failed: company={}, err={}", company, e.getMessage());
            return fallbackCompanyInsight(company == null ? "" : company.trim());
        }
    }

    private boolean cacheFacadeEnabled() {
        return cacheManager != null && applicationContext != null;
    }

    private MarketIntelligenceService self() {
        return applicationContext.getBean(MarketIntelligenceService.class);
    }

    private <T> T cacheValue(String cacheName, String cacheKey, Class<T> type) {
        try {
            Cache cache = cacheManager.getCache(cacheName);
            return cache == null ? null : cache.get(cacheKey, type);
        } catch (Exception e) {
            log.warn("market cache read failed, cache={}, key={}, err={}", cacheName, cacheKey, e.getMessage());
            return null;
        }
    }

    private void refreshAsync(Runnable runnable) {
        CompletableFuture.runAsync(() -> {
            try {
                runnable.run();
            } catch (Exception e) {
                log.warn("market cache async refresh failed: {}", e.getMessage());
            }
        });
    }

    private static void ensureFreshMeta(SalaryInsightVO vo) {
        if (vo.getMeta() == null) {
            vo.setMeta(CacheMeta.freshNow());
        }
    }

    private static void ensureFreshMeta(SkillTrendsVO vo) {
        if (vo.getMeta() == null) {
            vo.setMeta(CacheMeta.freshNow());
        }
    }

    private static void ensureFreshMeta(ResumeGapVO vo) {
        if (vo.getMeta() == null) {
            vo.setMeta(CacheMeta.freshNow());
        }
    }

    private static String toContextText(RagRetrieveResult ragResult) {
        if (ragResult == null || !ragResult.isSuccess()) {
            return "";
        }
        return KnowledgeRetrievalSupport.joinChunkContents(
                ragResult.getChunks(),
                MAX_CONTEXT_CHARS
        );
    }

    private void attachSources(SalaryInsightVO vo, RagRetrieveResult ragResult) {
        vo.setCitations(knowledgeRetrievalService.toMarketCitations(ragResult));
        vo.setSourceSummaries(knowledgeRetrievalService.toSourceSummaries(ragResult));
    }

    private void attachSources(SkillTrendsVO vo, RagRetrieveResult ragResult) {
        vo.setCitations(knowledgeRetrievalService.toMarketCitations(ragResult));
        vo.setSourceSummaries(knowledgeRetrievalService.toSourceSummaries(ragResult));
    }

    private void attachSources(ResumeGapVO vo, RagRetrieveResult ragResult) {
        vo.setCitations(knowledgeRetrievalService.toMarketCitations(ragResult));
        vo.setSourceSummaries(knowledgeRetrievalService.toSourceSummaries(ragResult));
    }

    private void attachSources(CompanyInsightVO vo, RagRetrieveResult ragResult) {
        vo.setCitations(knowledgeRetrievalService.toMarketCitations(ragResult));
        vo.setSourceSummaries(knowledgeRetrievalService.toSourceSummaries(ragResult));
    }

    private <T> T parseLlmJson(String userPrompt, Class<T> type) {
        ChatResponse response = llmClient.chat(ChatRequest.builder()
                .messages(List.of(
                        ChatMessage.builder()
                                .role("system")
                                .content("你只输出合法 JSON，不输出任何其他文字或解释。")
                                .build(),
                        ChatMessage.builder().role("user").content(userPrompt).build()
                ))
                .temperature(0.3)
                .build());
        if (response == null || response.getContent() == null || response.getContent().isBlank()) {
            log.warn("Market LLM response empty, type={}", type.getSimpleName());
            return null;
        }
        String raw = response.getContent().trim();
        Matcher matcher = JSON_BLOCK.matcher(raw);
        if (!matcher.find()) {
            log.warn("Market LLM output not JSON, type={}, head={}",
                    type.getSimpleName(), raw.substring(0, Math.min(120, raw.length())));
            return null;
        }
        try {
            return objectMapper.readValue(matcher.group(), type);
        } catch (Exception e) {
            log.warn("Market LLM JSON parse failed, type={}, err={}", type.getSimpleName(), e.getMessage());
            return null;
        }
    }

    private static String extractRoleFromResume(ResumeContext resumeContext) {
        if (resumeContext.getTitle() != null && !resumeContext.getTitle().isBlank()) {
            return resumeContext.getTitle().trim();
        }
        String content = resumeContext.getContent();
        if (content == null || content.isBlank()) {
            return "Java后端";
        }
        String lower = content.toLowerCase(Locale.ROOT);
        if (lower.contains("java") && (content.contains("后端") || lower.contains("backend"))) {
            return "Java后端";
        }
        if (content.contains("前端") || lower.contains("frontend")) {
            return "前端";
        }
        if (content.contains("算法")) {
            return "算法工程师";
        }
        if (content.contains("测试")) {
            return "测试工程师";
        }
        return "Java后端";
    }

    private static String defaultText(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private static SalaryInsightVO fallbackSalaryInsight() {
        SalaryInsightVO vo = new SalaryInsightVO();
        vo.setP25(NO_DATA);
        vo.setP50(NO_DATA);
        vo.setP75(NO_DATA);
        vo.setP90(NO_DATA);
        vo.setTrend(NO_DATA);
        vo.setAiSummary(FALLBACK_SUMMARY);
        vo.setCitations(Collections.emptyList());
        vo.setSourceSummaries(Collections.emptyList());
        vo.setMeta(CacheMeta.degradedNow());
        return vo;
    }

    private static SalaryInsightVO degradedSalaryInsight() {
        return fallbackSalaryInsight();
    }

    private static SkillTrendsVO fallbackSkillTrends() {
        SkillTrendsVO vo = new SkillTrendsVO();
        vo.setSkills(List.of());
        vo.setAiSummary(FALLBACK_SUMMARY);
        vo.setCitations(Collections.emptyList());
        vo.setSourceSummaries(Collections.emptyList());
        vo.setMeta(CacheMeta.degradedNow());
        return vo;
    }

    private static SkillTrendsVO degradedSkillTrends() {
        return fallbackSkillTrends();
    }

    private static ResumeGapVO fallbackResumeGap() {
        ResumeGapVO vo = new ResumeGapVO();
        vo.setHasSkills(List.of());
        vo.setMissingSkills(List.of());
        vo.setMatchScore(0);
        vo.setTopSuggestion(NO_DATA);
        vo.setAiSummary(FALLBACK_SUMMARY);
        vo.setCitations(Collections.emptyList());
        vo.setSourceSummaries(Collections.emptyList());
        vo.setMeta(CacheMeta.degradedNow());
        return vo;
    }

    private static ResumeGapVO degradedResumeGap() {
        return fallbackResumeGap();
    }

    private static ResumeGapVO emptyResumeGap() {
        ResumeGapVO vo = new ResumeGapVO();
        vo.setHasSkills(List.of());
        vo.setMissingSkills(List.of());
        vo.setMatchScore(0);
        vo.setTopSuggestion("");
        vo.setAiSummary("");
        vo.setCitations(Collections.emptyList());
        vo.setSourceSummaries(Collections.emptyList());
        vo.setMeta(CacheMeta.freshNow());
        return vo;
    }

    private static CompanyInsightVO fallbackCompanyInsight(String companyName) {
        CompanyInsightVO vo = new CompanyInsightVO();
        vo.setCompanyName(companyName == null || companyName.isBlank() ? NO_DATA : companyName);
        vo.setScale(NO_DATA);
        vo.setStage(NO_DATA);
        vo.setTechStack(List.of());
        vo.setCurrentJds(List.of());
        vo.setAiSummary(FALLBACK_SUMMARY);
        vo.setCitations(Collections.emptyList());
        vo.setSourceSummaries(Collections.emptyList());
        return vo;
    }
}
