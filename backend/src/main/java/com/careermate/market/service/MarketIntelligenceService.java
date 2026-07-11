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
        // 修复永远 LOADING（同考点速查根因）：原「refreshAsync 异步计算 + 返回 LOADING」在无请求
        // 上下文的异步线程里 LLM 失败、且 @Cacheable 写 Redis 序列化失败被吞 → 结果永不入缓存 →
        // 每次都 LOADING。改为同步计算 + 手动静默缓存（写失败不影响请求）。
        SalaryInsightVO result = computeSalaryInsight(safeCity, safeRole, safeYears);
        cachePutQuietly("market:salary", cacheKey, result, result.getMeta());
        return result;
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
        SkillTrendsVO result = computeSkillTrends(safeCity, safeRole);
        cachePutQuietly("market:skill-trends", cacheKey, result, result.getMeta());
        return result;
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
        ResumeGapVO result = computeResumeGap(userId, safeJdId);
        cachePutQuietly("market:resume-gap", cacheKey, result, result.getMeta());
        return result;
    }

    public SalaryInsightVO computeSalaryInsight(String city, String role, String years) {
        try {
            String query = role + " " + city + " " + years + " 薪资 月薪";
            RagRetrieveResult ragResult = knowledgeRetrievalService.retrieve(RagRetrieveRequest.builder()
                    .query(query)
                    .scene(RagRetrieveScene.MARKET)
                    .topK(30)
                    .build());
            if (ragResult == null || !ragResult.isSuccess()) {
                return fallbackSalaryInsight();
            }
            String context = toContextText(ragResult);
            if (context.isBlank()) {
                log.warn("getSalaryInsight: empty rag context, role={}, city={}", role, city);
                return emptySalaryInsight();
            }
            String prompt = MarketPrompts.salaryPrompt(role, city, years, context);
            SalaryInsightVO parsed = parseLlmJson(prompt, SalaryInsightVO.class);
            if (parsed == null) {
                return fallbackSalaryInsight();
            }
            attachSources(parsed, ragResult);
            parsed.setMeta(CacheMeta.fresh());
            return parsed;
        } catch (Exception e) {
            log.warn("getSalaryInsight failed: {}", e.getMessage());
            return fallbackSalaryInsight();
        }
    }

    public SkillTrendsVO computeSkillTrends(String city, String role) {
        try {
            String query = role + " " + city + " 技能要求 技术栈 必备";
            RagRetrieveResult ragResult = knowledgeRetrievalService.retrieve(RagRetrieveRequest.builder()
                    .query(query)
                    .scene(RagRetrieveScene.MARKET)
                    .topK(40)
                    .build());
            if (ragResult == null || !ragResult.isSuccess()) {
                return fallbackSkillTrends();
            }
            String context = toContextText(ragResult);
            if (context.isBlank()) {
                log.warn("getSkillTrends: empty rag context, role={}", role);
                return emptySkillTrends();
            }
            String prompt = MarketPrompts.skillTrendsPrompt(role, context);
            SkillTrendsVO parsed = parseLlmJson(prompt, SkillTrendsVO.class);
            if (parsed == null) {
                return fallbackSkillTrends();
            }
            attachSources(parsed, ragResult);
            parsed.setMeta(CacheMeta.fresh());
            return parsed;
        } catch (Exception e) {
            log.warn("getSkillTrends failed: {}", e.getMessage());
            return fallbackSkillTrends();
        }
    }

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
            if (ragResult == null || !ragResult.isSuccess()) {
                return fallbackResumeGap();
            }
            String context = toContextText(ragResult);
            if (context.isBlank()) {
                log.warn("getResumeGap: empty rag context, userId={}, role={}", userId, role);
                return emptyResumeGap();
            }
            String prompt = MarketPrompts.resumeGapPrompt(resumeContext.getContent(), context);
            ResumeGapVO parsed = parseLlmJson(prompt, ResumeGapVO.class);
            if (parsed == null) {
                return fallbackResumeGap();
            }
            attachSources(parsed, ragResult);
            parsed.setMeta(CacheMeta.fresh());
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

    private <T> T cacheValue(String cacheName, String cacheKey, Class<T> type) {
        try {
            Cache cache = cacheManager.getCache(cacheName);
            return cache == null ? null : cache.get(cacheKey, type);
        } catch (Exception e) {
            log.warn("market cache read failed, cache={}, key={}, err={}", cacheName, cacheKey, e.getMessage());
            return null;
        }
    }

    /** 手动写缓存：跳过 null 与 DEGRADED 结果；写失败（如序列化异常）只记日志、绝不影响请求。 */
    private void cachePutQuietly(String cacheName, String cacheKey, Object value, CacheMeta meta) {
        if (value == null || (meta != null && "DEGRADED".equals(meta.state().name()))) {
            return;
        }
        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.put(cacheKey, value);
            }
        } catch (Exception e) {
            log.warn("market cache put failed, cache={}, key={}, err={}", cacheName, cacheKey, e.getMessage());
        }
    }

    private static void ensureFreshMeta(SalaryInsightVO vo) {
        if (vo.getMeta() == null) {
            vo.setMeta(CacheMeta.fresh());
        }
    }

    private static void ensureFreshMeta(SkillTrendsVO vo) {
        if (vo.getMeta() == null) {
            vo.setMeta(CacheMeta.fresh());
        }
    }

    private static void ensureFreshMeta(ResumeGapVO vo) {
        if (vo.getMeta() == null) {
            vo.setMeta(CacheMeta.fresh());
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
        return salaryInsightWithMeta(CacheMeta.degraded());
    }

    private static SalaryInsightVO loadingSalaryInsight() {
        return salaryInsightWithMeta(CacheMeta.loading());
    }

    private static SalaryInsightVO emptySalaryInsight() {
        SalaryInsightVO vo = salaryInsightWithMeta(CacheMeta.empty());
        vo.setAiSummary("");
        return vo;
    }

    private static SalaryInsightVO salaryInsightWithMeta(CacheMeta meta) {
        SalaryInsightVO vo = new SalaryInsightVO();
        vo.setP25(NO_DATA);
        vo.setP50(NO_DATA);
        vo.setP75(NO_DATA);
        vo.setP90(NO_DATA);
        vo.setTrend(NO_DATA);
        vo.setAiSummary(FALLBACK_SUMMARY);
        vo.setCitations(Collections.emptyList());
        vo.setSourceSummaries(Collections.emptyList());
        vo.setMeta(meta);
        return vo;
    }

    private static SkillTrendsVO fallbackSkillTrends() {
        return skillTrendsWithMeta(CacheMeta.degraded());
    }

    private static SkillTrendsVO loadingSkillTrends() {
        return skillTrendsWithMeta(CacheMeta.loading());
    }

    private static SkillTrendsVO emptySkillTrends() {
        SkillTrendsVO vo = skillTrendsWithMeta(CacheMeta.empty());
        vo.setAiSummary("");
        return vo;
    }

    private static SkillTrendsVO skillTrendsWithMeta(CacheMeta meta) {
        SkillTrendsVO vo = new SkillTrendsVO();
        vo.setSkills(List.of());
        vo.setAiSummary(FALLBACK_SUMMARY);
        vo.setCitations(Collections.emptyList());
        vo.setSourceSummaries(Collections.emptyList());
        vo.setMeta(meta);
        return vo;
    }

    private static ResumeGapVO fallbackResumeGap() {
        return resumeGapWithMeta(CacheMeta.degraded(), NO_DATA, FALLBACK_SUMMARY);
    }

    private static ResumeGapVO loadingResumeGap() {
        return resumeGapWithMeta(CacheMeta.loading(), NO_DATA, FALLBACK_SUMMARY);
    }

    private static ResumeGapVO emptyResumeGap() {
        return resumeGapWithMeta(CacheMeta.empty(), "", "");
    }

    private static ResumeGapVO resumeGapWithMeta(CacheMeta meta, String topSuggestion, String aiSummary) {
        ResumeGapVO vo = new ResumeGapVO();
        vo.setHasSkills(List.of());
        vo.setMissingSkills(List.of());
        vo.setMatchScore(0);
        vo.setTopSuggestion(topSuggestion);
        vo.setAiSummary(aiSummary);
        vo.setCitations(Collections.emptyList());
        vo.setSourceSummaries(Collections.emptyList());
        vo.setMeta(meta);
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
