package com.careermate.interview.service;

import com.careermate.agent.tool.rag.RagRetrieveRequest;
import com.careermate.agent.tool.rag.RagRetrieveResult;
import com.careermate.agent.tool.rag.RagRetrieveScene;
import com.careermate.agent.tool.rag.RagRetrievedChunk;
import com.careermate.interview.InterviewQuestionPrompts;
import com.careermate.interview.dto.JdAwareQuestionsVO;
import com.careermate.knowledge.KnowledgeRetrievalService;
import com.careermate.knowledge.KnowledgeRetrievalSupport;
import com.careermate.llm.LlmClient;
import com.careermate.llm.dto.ChatMessage;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.dto.ChatResponse;
import com.careermate.resume.ResumeContext;
import com.careermate.resume.ResumeContextProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JD-aware 面试题生成服务 —— 围绕一条 JD，结合用户简历与面试题库出针对性面试题。
 *
 * <p>沿用 market/company 的成熟范式：按 docId 取 JD → 拼简历/题库上下文 → LLM 出题 → 标签归一 → 降级不抛异常。
 */
@Slf4j
@Service
public class InterviewQuestionService {

    private static final int MAX_CONTEXT_CHARS = 4000;
    private static final String JD_AWARE_CACHE = "interview:jd-aware-questions";
    private static final int JD_TOP_K = 50;
    private static final int INTERVIEW_TOP_K = 30;
    private static final String NO_RESUME_HINT =
            "（用户暂无默认简历，请仅基于 JD 出题，标签用 JD_FOCUSED）";
    private static final String NO_JD_SUMMARY =
            "未找到该岗位的 JD 内容，无法生成针对性面试题。";
    private static final Pattern JSON_BLOCK = Pattern.compile("\\{[\\s\\S]*\\}");
    private static final Set<String> VALID_TAGS = Set.of("JD_FOCUSED", "HIT_RESUME", "WEAK_POINT");

    private final KnowledgeRetrievalService knowledgeRetrievalService;
    private final ResumeContextProvider resumeContextProvider;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final com.careermate.agent.memory.AgentMemoryService agentMemoryService;
    private final org.springframework.cache.CacheManager cacheManager;

    public InterviewQuestionService(
            KnowledgeRetrievalService knowledgeRetrievalService,
            ResumeContextProvider resumeContextProvider,
            LlmClient llmClient,
            ObjectMapper objectMapper,
            com.careermate.agent.memory.AgentMemoryService agentMemoryService
    ) {
        this(knowledgeRetrievalService, resumeContextProvider, llmClient, objectMapper, agentMemoryService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public InterviewQuestionService(
            KnowledgeRetrievalService knowledgeRetrievalService,
            ResumeContextProvider resumeContextProvider,
            LlmClient llmClient,
            ObjectMapper objectMapper,
            com.careermate.agent.memory.AgentMemoryService agentMemoryService,
            org.springframework.beans.factory.ObjectProvider<org.springframework.cache.CacheManager> cacheManagerProvider
    ) {
        this.knowledgeRetrievalService = knowledgeRetrievalService;
        this.resumeContextProvider = resumeContextProvider;
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.agentMemoryService = agentMemoryService;
        this.cacheManager = cacheManagerProvider == null ? null : cacheManagerProvider.getIfAvailable();
    }

    /**
     * 出题结果缓存读写。
     *
     * <p>这条链路要走两次 RAG + 一次生成 5 道详题的 LLM，线上实测 32s——用户点开「按 JD 出题」
     * 要干等半分钟，同一条 JD 反复看更是每次都重算。JD 内容与用户简历短期内不变，缓存 12 小时。
     * 读写都静默失败：缓存挂了只是慢，不能让出题这件事本身失败。
     */
    private JdAwareQuestionsVO cachedQuestions(String cacheKey) {
        if (cacheManager == null) {
            return null;
        }
        try {
            org.springframework.cache.Cache cache = cacheManager.getCache(JD_AWARE_CACHE);
            return cache == null ? null : cache.get(cacheKey, JdAwareQuestionsVO.class);
        } catch (Exception e) {
            log.warn("jd-aware questions cache read failed, key={}, err={}", cacheKey, e.getMessage());
            return null;
        }
    }

    private void cacheQuestions(String cacheKey, JdAwareQuestionsVO value) {
        if (cacheManager == null || value == null || !value.isDataAvailable()) {
            // 只缓存真正出到题的结果：把 fallback 写进去，一次抖动就让这条 JD 十二小时都没题
            return;
        }
        try {
            org.springframework.cache.Cache cache = cacheManager.getCache(JD_AWARE_CACHE);
            if (cache != null) {
                cache.put(cacheKey, value);
            }
        } catch (Exception e) {
            log.warn("jd-aware questions cache put failed, key={}, err={}", cacheKey, e.getMessage());
        }
    }

    /** Reflexion：取用户历史模拟面试暴露的弱项，用于针对性出题。无则空串。 */
    private String resolveWeaknessContext(Long userId) {
        if (userId == null) {
            return "";
        }
        try {
            com.careermate.agent.memory.AgentMemoryContext ctx =
                    agentMemoryService.loadMemoryContext(userId, null);
            if (ctx == null) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            if (ctx.getInterviewWeaknessSummary() != null && !ctx.getInterviewWeaknessSummary().isBlank()) {
                sb.append(ctx.getInterviewWeaknessSummary().trim());
            }
            if (ctx.getWeaknessKeywords() != null && !ctx.getWeaknessKeywords().isEmpty()) {
                if (sb.length() > 0) {
                    sb.append("；");
                }
                sb.append("弱项关键词：").append(String.join("、", ctx.getWeaknessKeywords()));
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("resolveWeaknessContext failed: userId={}, err={}", userId, e.getMessage());
            return "";
        }
    }

    /**
     * 为一条 JD 生成针对性面试题（不指定公司）。
     */
    public JdAwareQuestionsVO generateJdAwareQuestions(Long jdDocId, Long userId) {
        return generateJdAwareQuestions(jdDocId, userId, null);
    }

    /**
     * 为一条 JD 生成针对性面试题，可结合该公司面经。
     *
     * @param jdDocId     目标 JD 的 RAGForge docId
     * @param userId      用户 ID（用于取简历个性化；可为 null）
     * @param companyName 公司名（可选；有则结合公司库+面经贴近真实考法出题）
     * @return 面试题列表；JD 未找到时 dataAvailable=false
     */
    public JdAwareQuestionsVO generateJdAwareQuestions(Long jdDocId, Long userId, String companyName) {
        try {
            if (jdDocId == null) {
                log.warn("generateJdAwareQuestions: jdDocId is null");
                return fallback(null);
            }

            String cacheKey = JD_AWARE_CACHE + ":" + jdDocId + ":" + userId + ":"
                    + (companyName == null ? "" : companyName.trim());
            JdAwareQuestionsVO cached = cachedQuestions(cacheKey);
            if (cached != null) {
                return cached;
            }

            // 简历、公司情报、历史弱项与 JD 检索互不依赖，先并发发出去，别一条条等
            var resumeFuture = java.util.concurrent.CompletableFuture.supplyAsync(() -> resolveResumeText(userId));
            var companyFuture = java.util.concurrent.CompletableFuture.supplyAsync(() -> resolveCompanyContext(companyName));
            var weaknessFuture = java.util.concurrent.CompletableFuture.supplyAsync(() -> resolveWeaknessContext(userId));

            RagRetrieveResult jdResult = knowledgeRetrievalService.retrieve(RagRetrieveRequest.builder()
                    .query("岗位职责 技能要求")
                    .scene(RagRetrieveScene.OPPORTUNITY)
                    .topK(JD_TOP_K)
                    .docIds(List.of(jdDocId))
                    .build());
            String jdContext = toContextText(jdResult);
            if (jdContext.isBlank()) {
                log.warn("generateJdAwareQuestions: empty JD context, jdDocId={}", jdDocId);
                return fallback(jdDocId);
            }
            String jdTitle = firstChunkTitle(jdResult);

            String resumeText = resumeFuture.join();

            RagRetrieveResult interviewResult = knowledgeRetrievalService.retrieve(RagRetrieveRequest.builder()
                    .query(jdTitle + " 面试 高频题 考点")
                    .scene(RagRetrieveScene.INTERVIEW)
                    .topK(INTERVIEW_TOP_K)
                    .build());
            String interviewContext = toContextText(interviewResult);

            String companyContext = companyFuture.join();
            String weaknessContext = weaknessFuture.join();

            String prompt = InterviewQuestionPrompts.jdAwarePrompt(
                    jdTitle, jdContext, resumeText, interviewContext, companyContext, weaknessContext);
            JdAwareQuestionsVO parsed = parseLlmJson(prompt, JdAwareQuestionsVO.class);
            if (parsed == null) {
                return fallback(jdDocId);
            }
            parsed.setJdDocId(jdDocId);
            cacheQuestions(cacheKey, parsed);
            if (parsed.getJdTitle() == null || parsed.getJdTitle().isBlank()) {
                parsed.setJdTitle(jdTitle);
            }
            parsed.setDataAvailable(true);
            normalizeQuestions(parsed);
            return parsed;
        } catch (Exception e) {
            log.warn("generateJdAwareQuestions failed: jdDocId={}, err={}", jdDocId, e.getMessage());
            return fallback(jdDocId);
        }
    }

    /** 有公司名则检索「公司库 + 面经」作为该公司面经上下文；无则返回空串。复用 getCompanyPrep 的检索模式。 */
    private String resolveCompanyContext(String companyName) {
        if (companyName == null || companyName.isBlank()) {
            return "";
        }
        try {
            String safe = companyName.trim();
            RagRetrieveResult result = knowledgeRetrievalService.retrieveMerged(List.of(
                    RagRetrieveRequest.builder()
                            .query(safe + " 面试 技术 考点")
                            .scene(RagRetrieveScene.COMPANY)
                            .topK(15)
                            .build(),
                    RagRetrieveRequest.builder()
                            .query(safe + " 面经")
                            .scene(RagRetrieveScene.INTERVIEW)
                            .topK(10)
                            .build()
            ));
            return toContextText(result);
        } catch (Exception e) {
            log.warn("resolveCompanyContext failed: company={}, err={}", companyName, e.getMessage());
            return "";
        }
    }

    private String resolveResumeText(Long userId) {
        ResumeContext resume = resumeContextProvider.getResumeContext(userId);
        if (resume != null && resume.isAvailable()
                && resume.getContextText() != null && !resume.getContextText().isBlank()) {
            return resume.getContextText();
        }
        return NO_RESUME_HINT;
    }

    private static String toContextText(RagRetrieveResult result) {
        if (result == null || !result.isSuccess()) {
            return "";
        }
        return KnowledgeRetrievalSupport.joinChunkContents(result.getChunks(), MAX_CONTEXT_CHARS);
    }

    private static String firstChunkTitle(RagRetrieveResult result) {
        if (result == null || result.getChunks() == null || result.getChunks().isEmpty()) {
            return "该岗位";
        }
        RagRetrievedChunk first = result.getChunks().get(0);
        String title = first.getSourceTitle() != null ? first.getSourceTitle() : first.getFileName();
        return title == null || title.isBlank() ? "该岗位" : title.trim();
    }

    /** 归一化题目：剔除空题、重排题号、标签统一为三类之一、参考要点非空。 */
    private static void normalizeQuestions(JdAwareQuestionsVO vo) {
        List<JdAwareQuestionsVO.JdAwareQuestion> questions = vo.getQuestions();
        if (questions == null || questions.isEmpty()) {
            vo.setQuestions(new ArrayList<>());
            return;
        }
        List<JdAwareQuestionsVO.JdAwareQuestion> normalized = new ArrayList<>();
        int no = 1;
        for (JdAwareQuestionsVO.JdAwareQuestion q : questions) {
            if (q == null || q.getQuestionText() == null || q.getQuestionText().isBlank()) {
                continue;
            }
            q.setQuestionNo(no++);
            String tag = q.getTag() == null ? "JD_FOCUSED" : q.getTag().trim().toUpperCase(Locale.ROOT);
            if (!VALID_TAGS.contains(tag)) {
                tag = "JD_FOCUSED";
            }
            q.setTag(tag);
            if (q.getReferencePoints() == null) {
                q.setReferencePoints(new ArrayList<>());
            }
            normalized.add(q);
        }
        vo.setQuestions(normalized);
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
            log.warn("Interview question LLM response empty, type={}", type.getSimpleName());
            return null;
        }
        String raw = response.getContent().trim();
        Matcher matcher = JSON_BLOCK.matcher(raw);
        if (!matcher.find()) {
            log.warn("Interview question LLM output not JSON, head={}",
                    raw.substring(0, Math.min(120, raw.length())));
            return null;
        }
        try {
            return objectMapper.readValue(matcher.group(), type);
        } catch (Exception e) {
            log.warn("Interview question LLM JSON parse failed, err={}", e.getMessage());
            return null;
        }
    }

    private static JdAwareQuestionsVO fallback(Long jdDocId) {
        JdAwareQuestionsVO vo = new JdAwareQuestionsVO();
        vo.setJdDocId(jdDocId);
        vo.setJdTitle("");
        vo.setQuestions(Collections.emptyList());
        vo.setAiSummary(NO_JD_SUMMARY);
        vo.setDataAvailable(false);
        return vo;
    }
}
