package com.careermate.market.service;

import com.careermate.agent.tool.rag.RagRetrieveRequest;
import com.careermate.agent.tool.rag.RagRetrieveResult;
import com.careermate.agent.tool.rag.RagRetrieveScene;
import com.careermate.agent.tool.rag.RagRetrievedChunk;
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
import com.careermate.market.dto.SalaryNarrativeVO;
import com.careermate.market.dto.SkillTrendsVO;
import com.careermate.market.support.MarketDefaults;
import com.careermate.market.support.MarketExperience;
import com.careermate.market.support.SalaryParseSupport;
import com.careermate.market.support.SalarySamples;
import com.careermate.market.support.TermMentions;
import com.careermate.resume.ResumeContext;
import com.careermate.resume.ResumeContextProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Predicate;
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
    /** 达到此样本量才由后端直接算分位；不足则退回 LLM 估算。 */
    private static final int MIN_SALARY_SAMPLES = 8;
    /** 达到此样本量才用观测区间钳制 LLM 估出的分位。 */
    private static final int MIN_CLAMP_SAMPLES = 3;
    /** 岗位名里的通用词，判定「在招岗位」是否有原文支撑时先剔除，只比对特征词。 */
    private static final Pattern GENERIC_TITLE_WORDS = Pattern.compile(
            "高级|资深|初级|中级|专家|首席|实习|应届|校招|社招|工程师|开发|研发|岗位|岗|senior|junior|engineer|developer");
    /** 岗位名特征词：ASCII 段或中文段，分开取，避免「java后端」整体比对被误杀。 */
    private static final Pattern TITLE_FEATURE = Pattern.compile("[a-z0-9.+#]+|[\\u4e00-\\u9fa5]+");
    /** 公司名里的通用后缀，比对前先剥掉，让全称能命中语料里的简称。 */
    private static final Pattern COMPANY_GENERIC_WORDS = Pattern.compile(
            "股份有限公司|有限责任公司|有限公司|集团|公司|科技|技术|网络|信息|数字|软件|服务|"
                    + "（[^）]*）|\\([^)]*\\)");

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
        String safeRole = defaultText(role, MarketDefaults.ROLE);
        String safeCity = defaultText(city, MarketDefaults.CITY);
        // 「不限」是一等口径，不再被静默补成 3-5年（缓存 key 也据此区分）
        String safeYears = MarketExperience.normalize(years);
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
        return getSkillTrends(MarketDefaults.CITY, role);
    }

    public SkillTrendsVO getSkillTrends(String city, String role) {
        String safeCity = defaultText(city, MarketDefaults.CITY);
        String safeRole = defaultText(role, MarketDefaults.ROLE);
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
            // 「不限」不带经验维度进检索，避免把全经验段的查询窄化到某个年限区间
            String query = MarketExperience.isAny(years)
                    ? role + " " + city + " 薪资 月薪"
                    : role + " " + city + " " + years + " 薪资 月薪";
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
            String yearsClause = MarketExperience.describe(years);
            int[] samples = SalarySamples.extractMonthlyYuan(context);

            // 分位是纯算术，样本够就自己算，别让 LLM 估
            SalaryInsightVO parsed = samples.length >= MIN_SALARY_SAMPLES
                    ? computedSalaryInsight(role, city, yearsClause, samples, context)
                    : estimatedSalaryInsight(role, city, yearsClause, samples, context);
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
            // 技能热度必须查岗位 JD 库（OPPORTUNITY），不能查薪资行情库（MARKET）——后者是
            // 「城市×岗位薪资报告」，正文只有公司名与薪资表，没有技术栈，检出的高频词会是华为、
            // 中软国际这类企业名。同类需求 computeResumeGap 走的也是 OPPORTUNITY。
            RagRetrieveResult ragResult = knowledgeRetrievalService.retrieve(RagRetrieveRequest.builder()
                    .query(query)
                    .scene(RagRetrieveScene.OPPORTUNITY)
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
            applySkillHeat(parsed, context, role);
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
            applyGapGrounding(parsed, resumeContext.getContent(), context);
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
            // 向量检索按语义相似度召回，同一批 chunk 里混着别家公司的 JD（实测查腾讯与查华为
            // 会命中同一条岗位）。公司情报必须只看真正提到该公司的片段，否则等于张冠李戴。
            ragResult = retainChunksMentioning(ragResult, safeCompany);
            String context = toContextText(ragResult);
            if (context.isBlank()) {
                log.warn("getCompanyInsight: no chunk mentions the company, company={}", safeCompany);
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
            applyCompanyGrounding(parsed, context);
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

    /**
     * 用真实词频给技能热度赋值。
     *
     * <p>此前前端的热度条宽度只按名次线性递减（Top1 恒 100%、Top6 恒 17%），换任何岗位城市都一模一样，
     * 图形不承载任何数据。这里改为后端确定性统计每个技能在本次检索到的 JD 原文中的出现次数，
     * 热度 = 该技能次数 / 最高次数 × 100，并据此重排 rank。
     *
     * <p>同时作为反编造守卫：LLM 给出的技能若在原文中一次都没出现，直接剔除（全部为 0 时判定统计不可信，
     * 保留原始列表但热度置空，由前端隐藏热度条）。
     */
    static void applySkillHeat(SkillTrendsVO vo, String context, String role) {
        List<SkillTrendsVO.SkillItem> skills = vo == null ? null : vo.getSkills();
        if (skills == null || skills.isEmpty() || context == null || context.isBlank()) {
            return;
        }
        String haystack = TermMentions.haystack(context);
        int max = 0;
        for (SkillTrendsVO.SkillItem item : skills) {
            int mentions = TermMentions.count(haystack, item.getName());
            item.setMentions(mentions);
            max = Math.max(max, mentions);
        }
        if (max <= 0) {
            log.warn("skill heat: no skill matched retrieved context, role={}, skills={}", role, skills.size());
            skills.forEach(item -> {
                item.setMentions(null);
                item.setHeat(null);
            });
            return;
        }
        List<SkillTrendsVO.SkillItem> kept = new ArrayList<>();
        for (SkillTrendsVO.SkillItem item : skills) {
            if (item.getMentions() != null && item.getMentions() > 0) {
                kept.add(item);
            } else {
                log.warn("skill heat: drop fabricated skill not present in context, role={}, name={}",
                        role, item.getName());
            }
        }
        kept.sort(Comparator.comparingInt(SkillTrendsVO.SkillItem::getMentions).reversed());
        int finalMax = max;
        for (int i = 0; i < kept.size(); i++) {
            SkillTrendsVO.SkillItem item = kept.get(i);
            item.setRank(i + 1);
            item.setHeat((int) Math.round(item.getMentions() * 100.0 / finalMax));
        }
        vo.setSkills(kept);
    }

    /**
     * 只保留正文或文件名里真正提到该公司的 chunk。
     *
     * <p>公司名先剥掉「有限公司/股份/科技/集团」等通用后缀再比对，让用户输入的全称
     * （「腾讯科技（深圳）有限公司」）也能命中语料里的简称（「腾讯」）。
     */
    static RagRetrieveResult retainChunksMentioning(RagRetrieveResult ragResult, String company) {
        if (ragResult == null || ragResult.getChunks() == null || ragResult.getChunks().isEmpty()) {
            return ragResult;
        }
        String needle = companyCore(company);
        if (needle.isBlank()) {
            return ragResult;
        }
        List<RagRetrievedChunk> kept = ragResult.getChunks().stream()
                .filter(chunk -> TermMentions.appearsIn(TermMentions.haystack(chunk.getContent()), needle)
                        || TermMentions.appearsIn(TermMentions.haystack(chunk.getFileName()), needle))
                .toList();
        if (kept.size() == ragResult.getChunks().size()) {
            return ragResult;
        }
        log.info("company insight: filtered chunks by company, company={}, kept={}/{}",
                company, kept.size(), ragResult.getChunks().size());
        return RagRetrieveResult.builder()
                .success(ragResult.isSuccess())
                .query(ragResult.getQuery())
                .scene(ragResult.getScene())
                .chunks(kept)
                .fallbackUsed(ragResult.isFallbackUsed())
                .errorCode(ragResult.getErrorCode())
                .latencyMs(ragResult.getLatencyMs())
                .build();
    }

    /** 剥掉公司名里的通用后缀，得到用于比对的核心词。剥完过短则退回原名。 */
    private static String companyCore(String company) {
        if (company == null || company.isBlank()) {
            return "";
        }
        String core = COMPANY_GENERIC_WORDS.matcher(company.trim()).replaceAll("").trim();
        return core.length() >= 2 ? core : company.trim();
    }

    /** 样本够多：P25/P50/P75/P90 由真实薪资样本算出，LLM 只补趋势与文案。 */
    private SalaryInsightVO computedSalaryInsight(
            String role, String city, String yearsClause, int[] samples, String context) {
        String[] quantiles = {
                SalaryParseSupport.formatYuanToK(SalarySamples.percentile(samples, 0.25)),
                SalaryParseSupport.formatYuanToK(SalarySamples.percentile(samples, 0.50)),
                SalaryParseSupport.formatYuanToK(SalarySamples.percentile(samples, 0.75)),
                SalaryParseSupport.formatYuanToK(SalarySamples.percentile(samples, 0.90)),
        };
        SalaryInsightVO vo = new SalaryInsightVO();
        vo.setP25(quantiles[0]);
        vo.setP50(quantiles[1]);
        vo.setP75(quantiles[2]);
        vo.setP90(quantiles[3]);

        SalaryNarrativeVO narrative = parseLlmJson(
                MarketPrompts.salaryNarrativePrompt(
                        role, city, yearsClause, samples.length, quantiles, context),
                SalaryNarrativeVO.class);
        if (narrative == null) {
            // 文案拿不到不影响分位——数字本身是确定的
            log.warn("salary narrative unavailable, keep computed quantiles, role={}, city={}", role, city);
            vo.setTrend(NO_DATA);
            vo.setAiSummary("");
            return vo;
        }
        vo.setTrend(defaultText(narrative.trend(), NO_DATA));
        vo.setAiSummary(narrative.aiSummary() == null ? "" : narrative.aiSummary());
        return vo;
    }

    /**
     * 样本不足：仍由 LLM 估算分位，但估出来的值必须落在原文出现过的薪资区间内。
     *
     * <p>样本 ≥ {@link #MIN_CLAMP_SAMPLES} 时超界即钳到边界——真实值不可能落在观测范围之外；
     * 再少就只记日志不改数，样本太少时边界本身也不可信。
     */
    private SalaryInsightVO estimatedSalaryInsight(
            String role, String city, String yearsClause, int[] samples, String context) {
        SalaryInsightVO parsed = parseLlmJson(
                MarketPrompts.salaryPrompt(role, city, yearsClause, context), SalaryInsightVO.class);
        if (parsed == null) {
            return null;
        }
        if (samples.length < MIN_CLAMP_SAMPLES) {
            log.warn("salary quantiles unvalidated, too few samples in context: role={}, city={}, samples={}",
                    role, city, samples.length);
            return parsed;
        }
        int min = samples[0];
        int max = samples[samples.length - 1];
        parsed.setP25(clampToObserved(parsed.getP25(), min, max, "P25", role, city));
        parsed.setP50(clampToObserved(parsed.getP50(), min, max, "P50", role, city));
        parsed.setP75(clampToObserved(parsed.getP75(), min, max, "P75", role, city));
        parsed.setP90(clampToObserved(parsed.getP90(), min, max, "P90", role, city));
        return parsed;
    }

    private static String clampToObserved(
            String value, int min, int max, String label, String role, String city) {
        int yuan = SalaryParseSupport.parseToYuan(value);
        if (yuan == SalaryParseSupport.UNPARSEABLE) {
            return value;
        }
        int clamped = Math.min(Math.max(yuan, min), max);
        if (clamped != yuan) {
            log.warn("salary {} out of observed range, clamped: role={}, city={}, llm={}, observed=[{},{}]",
                    label, role, city, value, min, max);
            return SalaryParseSupport.formatYuanToK(clamped);
        }
        return value;
    }

    /**
     * 简历差距的原文支撑校验。
     *
     * <p>hasSkills / missingSkills 的语义本身就是集合关系，可以确定性校验，不必信 LLM：
     * <ul>
     *   <li>hasSkills = 简历里有 ∩ JD 里要求 —— 必须同时出现在简历原文与 JD 检索原文中</li>
     *   <li>missingSkills = JD 里要求 - 简历里有 —— 必须出现在 JD 原文中，且不在简历原文中</li>
     * </ul>
     * 不满足的项直接剔除：前者是给用户虚记的功劳，后者是凭空造出的差距，都会误导改简历的决策。
     *
     * <p>matchScore 不重算——它是 LLM 对经历、项目、深度的综合判断，不只由技能词集合决定。
     */
    static void applyGapGrounding(ResumeGapVO vo, String resumeText, String context) {
        if (vo == null || resumeText == null || resumeText.isBlank() || context == null || context.isBlank()) {
            return;
        }
        String resume = TermMentions.haystack(resumeText);
        String jd = TermMentions.haystack(context);

        List<String> has = groundedTerms(vo.getHasSkills(), term ->
                TermMentions.appearsIn(resume, term) && TermMentions.appearsIn(jd, term),
                term -> log.warn("resume gap: drop unsupported hasSkill, name={}", term));
        List<String> missing = groundedTerms(vo.getMissingSkills(), term ->
                TermMentions.appearsIn(jd, term) && !TermMentions.appearsIn(resume, term),
                term -> log.warn("resume gap: drop unsupported missingSkill, name={}", term));

        vo.setHasSkills(has);
        vo.setMissingSkills(missing);
    }

    /**
     * 公司情报的原文支撑校验。
     *
     * <p>techStack 与技能同类，按字面校验；currentJds 是岗位名，LLM 常做规范化改写
     * （原文「资深java研发工程师」→ 输出「Java后端工程师」），字面比对会误杀，
     * 因此只要求去掉通用词后还剩的特征词至少有一个在原文出现。
     */
    static void applyCompanyGrounding(CompanyInsightVO vo, String context) {
        if (vo == null || context == null || context.isBlank()) {
            return;
        }
        dedupeScaleAndStage(vo);
        String jd = TermMentions.haystack(context);

        vo.setTechStack(groundedTerms(vo.getTechStack(),
                term -> TermMentions.appearsIn(jd, term),
                term -> log.warn("company insight: drop unsupported techStack, company={}, name={}",
                        vo.getCompanyName(), term)));

        vo.setCurrentJds(groundedTerms(vo.getCurrentJds(),
                title -> jobTitleGrounded(jd, title),
                title -> log.warn("company insight: drop unsupported currentJd, company={}, title={}",
                        vo.getCompanyName(), title)));
    }

    /**
     * scale 只留体量档位，stage 只留融资/上市阶段。
     *
     * <p>模型常把两者拼在一起（scale=「大厂 / 上市公司」、stage=「上市」），展示出来是
     * 「大厂 / 上市公司 / 上市」，同一件事说两遍。prompt 已约束，这里再兜一层。
     */
    private static void dedupeScaleAndStage(CompanyInsightVO vo) {
        String scale = vo.getScale();
        if (scale == null || scale.isBlank() || !scale.contains("/")) {
            return;
        }
        String stage = vo.getStage() == null ? "" : vo.getStage().trim();
        List<String> parts = Arrays.stream(scale.split("/"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .filter(s -> stage.isBlank() || (!s.contains(stage) && !stage.contains(s)))
                .toList();
        if (parts.isEmpty()) {
            return;
        }
        // 只保留第一个体量描述，并列项本身就是冗余
        vo.setScale(parts.get(0));
    }

    /**
     * 岗位名去掉通用词后，特征词至少一个在原文出现即算有支撑；无特征词可判时保留。
     *
     * <p>特征词按「ASCII 段」与「中文段」分别切分——「Java后端工程师」剔除通用词后是
     * {@code java后端}，整体拿去比对会误杀（原文写的是「资深java研发工程师」），
     * 切成 {@code java} + {@code 后端} 后 java 命中即判定有支撑。
     */
    private static boolean jobTitleGrounded(String jd, String title) {
        if (title == null || title.isBlank()) {
            return false;
        }
        String stripped = GENERIC_TITLE_WORDS.matcher(title.toLowerCase(Locale.ROOT)).replaceAll(" ");
        List<String> features = new ArrayList<>();
        Matcher matcher = TITLE_FEATURE.matcher(stripped);
        while (matcher.find()) {
            String token = matcher.group();
            if (token.length() >= 2) {
                features.add(token);
            }
        }
        if (features.isEmpty()) {
            return true;
        }
        return features.stream().anyMatch(t -> TermMentions.appearsIn(jd, t));
    }

    private static List<String> groundedTerms(
            List<String> terms,
            Predicate<String> grounded,
            Consumer<String> onDrop
    ) {
        if (terms == null || terms.isEmpty()) {
            return terms == null ? List.of() : terms;
        }
        List<String> kept = new ArrayList<>();
        for (String term : terms) {
            if (term == null || term.isBlank()) {
                continue;
            }
            if (grounded.test(term)) {
                kept.add(term);
            } else {
                onDrop.accept(term);
            }
        }
        return kept;
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
                // 这里干的是「把检索到的行情报告里的分位数抄出来」这件事，不是创作。
                // 0.3 会让同一画像连续两次查到 p50=24K / 25K、p90=42K / 40K——用户刷新一下
                // 谈薪锚点就变了，行情页面因此不可信。取 0 让同输入尽量同输出。
                .temperature(0.0)
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
