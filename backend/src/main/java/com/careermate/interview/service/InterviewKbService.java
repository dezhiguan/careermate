package com.careermate.interview.service;

import com.careermate.agent.tool.rag.RagRetrieveRequest;
import com.careermate.agent.tool.rag.RagRetrieveScene;
import com.careermate.cache.CacheKeys;
import com.careermate.common.api.CacheMeta;
import com.careermate.interview.dto.CompanyPrepVO;
import com.careermate.interview.dto.KbQuestionsVO;
import com.careermate.interview.InterviewKbPrompts;
import com.careermate.knowledge.KnowledgeRetrievalService;
import com.careermate.llm.LlmClient;
import com.careermate.llm.dto.ChatMessage;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.dto.ChatResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class InterviewKbService {

    private static final int MAX_CONTEXT_CHARS = 4000;
    private static final Pattern JSON_BLOCK = Pattern.compile("\\{[\\s\\S]*\\}");

    private final KnowledgeRetrievalService knowledgeRetrievalService;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final CacheManager cacheManager;
    private final ApplicationContext applicationContext;

    public InterviewKbService(
            KnowledgeRetrievalService knowledgeRetrievalService,
            LlmClient llmClient,
            ObjectMapper objectMapper
    ) {
        this(knowledgeRetrievalService, llmClient, objectMapper, null, null);
    }

    @Autowired
    public InterviewKbService(
            KnowledgeRetrievalService knowledgeRetrievalService,
            LlmClient llmClient,
            ObjectMapper objectMapper,
            ObjectProvider<CacheManager> cacheManagerProvider,
            ApplicationContext applicationContext
    ) {
        this.knowledgeRetrievalService = knowledgeRetrievalService;
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.cacheManager = cacheManagerProvider == null ? null : cacheManagerProvider.getIfAvailable();
        this.applicationContext = applicationContext;
    }

    public KbQuestionsVO getKbQuestions(String query) {
        String safeQuery = defaultText(query, "Java后端");
        if (!cacheFacadeEnabled()) {
            return computeKbQuestions(safeQuery);
        }
        String cacheKey = CacheKeys.interviewKbQuestions(safeQuery);
        KbQuestionsVO cached = cacheValue("interview:kb-questions", cacheKey, KbQuestionsVO.class);
        if (cached != null) {
            ensureFreshMeta(cached);
            return cached;
        }
        refreshAsync(() -> self().computeKbQuestions(safeQuery));
        return loadingKbQuestions(safeQuery);
    }

    @Cacheable(
            cacheNames = "interview:kb-questions",
            key = "T(com.careermate.cache.CacheKeys).interviewKbQuestions(#tag)",
            unless = "#result == null || (#result.meta != null && #result.meta.state().name() == 'DEGRADED')"
    )
    public KbQuestionsVO computeKbQuestions(String tag) {
        try {
            String safeQuery = defaultText(tag, "Java后端");
            String context = knowledgeRetrievalService.retrieveContextText(
                    RagRetrieveScene.INTERVIEW,
                    safeQuery + " 面试题 考点",
                    20
            );
            if (context.isBlank()) {
                // 评审 P0-3：知识库无命中时，不再直接返回「暂无题目」，改用 AI 通用知识现场生成，
                // 保证推荐关键词（如 Spring Boot）也总能拿到有用考点题。
                log.warn("getKbQuestions: empty rag context, fallback to general generation, query={}", safeQuery);
                return generateGeneralKbQuestions(safeQuery);
            }
            String prompt = InterviewKbPrompts.kbQuestionsPrompt(safeQuery, context);
            KbQuestionsVO parsed = parseLlmJson(prompt, KbQuestionsVO.class);
            if (parsed == null || parsed.getQuestions() == null || parsed.getQuestions().isEmpty()) {
                // LLM 未从知识库提炼出题目，同样回退到通用生成而非空结果。
                return generateGeneralKbQuestions(safeQuery);
            }
            if (parsed.getQuery() == null || parsed.getQuery().isBlank()) {
                parsed.setQuery(safeQuery);
            }
            parsed.setMeta(CacheMeta.fresh());
            return parsed;
        } catch (Exception e) {
            log.warn("getKbQuestions failed: query={}, err={}", tag, e.getMessage());
            return fallbackKbQuestions(defaultText(tag, "Java后端"));
        }
    }

    public CompanyPrepVO getCompanyPrep(String company) {
        try {
            if (company == null || company.isBlank()) {
                log.warn("getCompanyPrep: company is blank");
                return fallbackCompanyPrep("");
            }
            String safeCompany = company.trim();
            String context = knowledgeRetrievalService.retrieveMergedContextText(
                    List.of(
                            RagRetrieveRequest.builder()
                                    .query(safeCompany + " 面试 技术")
                                    .scene(RagRetrieveScene.COMPANY)
                                    .topK(20)
                                    .build(),
                            RagRetrieveRequest.builder()
                                    .query(safeCompany + " 面经")
                                    .scene(RagRetrieveScene.INTERVIEW)
                                    .topK(10)
                                    .build()
                    ),
                    MAX_CONTEXT_CHARS
            );
            if (context.isBlank()) {
                log.warn("getCompanyPrep: empty rag context, company={}", safeCompany);
                return fallbackCompanyPrep(safeCompany);
            }
            String prompt = InterviewKbPrompts.companyPrepPrompt(safeCompany, context);
            CompanyPrepVO parsed = parseLlmJson(prompt, CompanyPrepVO.class);
            if (parsed == null) {
                return fallbackCompanyPrep(safeCompany);
            }
            if (parsed.getCompanyName() == null || parsed.getCompanyName().isBlank()) {
                parsed.setCompanyName(safeCompany);
            }
            if (parsed.getTechFocus() == null) {
                parsed.setTechFocus(Collections.emptyList());
            }
            if (parsed.getCommonQuestions() == null) {
                parsed.setCommonQuestions(Collections.emptyList());
            }
            return parsed;
        } catch (Exception e) {
            log.warn("getCompanyPrep failed: company={}, err={}", company, e.getMessage());
            return fallbackCompanyPrep(company == null ? "" : company.trim());
        }
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
            log.warn("Interview KB LLM response empty, type={}", type.getSimpleName());
            return null;
        }
        String raw = response.getContent().trim();
        Matcher matcher = JSON_BLOCK.matcher(raw);
        if (!matcher.find()) {
            log.warn("Interview KB LLM output not JSON, type={}, head={}",
                    type.getSimpleName(), raw.substring(0, Math.min(120, raw.length())));
            return null;
        }
        try {
            return objectMapper.readValue(matcher.group(), type);
        } catch (Exception e) {
            log.warn("Interview KB LLM JSON parse failed, type={}, err={}", type.getSimpleName(), e.getMessage());
            return null;
        }
    }

    private static String defaultText(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private boolean cacheFacadeEnabled() {
        return cacheManager != null && applicationContext != null;
    }

    private InterviewKbService self() {
        return applicationContext.getBean(InterviewKbService.class);
    }

    private <T> T cacheValue(String cacheName, String cacheKey, Class<T> type) {
        try {
            Cache cache = cacheManager.getCache(cacheName);
            return cache == null ? null : cache.get(cacheKey, type);
        } catch (Exception e) {
            log.warn("interview kb cache read failed, cache={}, key={}, err={}", cacheName, cacheKey, e.getMessage());
            return null;
        }
    }

    private void refreshAsync(Runnable runnable) {
        CompletableFuture.runAsync(() -> {
            try {
                runnable.run();
            } catch (Exception e) {
                log.warn("interview kb cache async refresh failed: {}", e.getMessage());
            }
        });
    }

    private static void ensureFreshMeta(KbQuestionsVO vo) {
        if (vo.getMeta() == null) {
            vo.setMeta(CacheMeta.fresh());
        }
    }

    /**
     * 知识库无命中时的 AI 现场生成（评审 P0-3）。生成失败或仍为空则退回 {@link #fallbackKbQuestions}。
     */
    private KbQuestionsVO generateGeneralKbQuestions(String query) {
        try {
            KbQuestionsVO parsed = parseLlmJson(InterviewKbPrompts.kbQuestionsGeneralPrompt(query), KbQuestionsVO.class);
            if (parsed == null || parsed.getQuestions() == null || parsed.getQuestions().isEmpty()) {
                return fallbackKbQuestions(query);
            }
            if (parsed.getQuery() == null || parsed.getQuery().isBlank()) {
                parsed.setQuery(query);
            }
            parsed.setMeta(CacheMeta.fresh());
            return parsed;
        } catch (Exception e) {
            log.warn("generateGeneralKbQuestions failed: query={}, err={}", query, e.getMessage());
            return fallbackKbQuestions(query);
        }
    }

    private static KbQuestionsVO fallbackKbQuestions(String query) {
        KbQuestionsVO vo = new KbQuestionsVO();
        vo.setQuery(query);
        vo.setQuestions(Collections.emptyList());
        vo.setAiSummary("暂无相关面试资料");
        vo.setMeta(CacheMeta.degraded());
        return vo;
    }

    private static KbQuestionsVO loadingKbQuestions(String query) {
        KbQuestionsVO vo = new KbQuestionsVO();
        vo.setQuery(query);
        vo.setQuestions(Collections.emptyList());
        vo.setAiSummary("");
        vo.setMeta(CacheMeta.loading());
        return vo;
    }

    private static CompanyPrepVO fallbackCompanyPrep(String companyName) {
        CompanyPrepVO vo = new CompanyPrepVO();
        if (companyName == null || companyName.isBlank()) {
            vo.setCompanyName("");
            vo.setAiSummary("请输入公司名称");
        } else {
            vo.setCompanyName(companyName);
            vo.setAiSummary("暂无该公司面试资料，请尝试其他公司名称");
        }
        vo.setInterviewStyle("");
        vo.setTechFocus(Collections.emptyList());
        vo.setCommonQuestions(Collections.emptyList());
        vo.setBehaviorTips("");
        return vo;
    }
}
