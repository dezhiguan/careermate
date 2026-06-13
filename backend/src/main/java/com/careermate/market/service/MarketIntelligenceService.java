package com.careermate.market.service;

import com.careermate.llm.LlmClient;
import com.careermate.llm.dto.ChatMessage;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.dto.ChatResponse;
import com.careermate.market.MarketPrompts;
import com.careermate.market.dto.CompanyInsightVO;
import com.careermate.market.dto.ResumeGapVO;
import com.careermate.market.dto.SalaryInsightVO;
import com.careermate.market.dto.SkillTrendsVO;
import com.careermate.ragforge.RagForgeChunk;
import com.careermate.ragforge.RagForgeClient;
import com.careermate.resume.ResumeContext;
import com.careermate.resume.ResumeContextProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MarketIntelligenceService {

    private static final int MAX_CONTEXT_CHARS = 4000;
    private static final String FALLBACK_SUMMARY = "暂时无法获取市场数据，请稍后再试";
    private static final String NO_DATA = "暂无数据";
    private static final Pattern JSON_BLOCK = Pattern.compile("\\{[\\s\\S]*\\}");

    private final RagForgeClient ragForgeClient;
    private final LlmClient llmClient;
    private final ResumeContextProvider resumeContextProvider;
    private final ObjectMapper objectMapper;

    public MarketIntelligenceService(
            RagForgeClient ragForgeClient,
            LlmClient llmClient,
            ResumeContextProvider resumeContextProvider,
            ObjectMapper objectMapper
    ) {
        this.ragForgeClient = ragForgeClient;
        this.llmClient = llmClient;
        this.resumeContextProvider = resumeContextProvider;
        this.objectMapper = objectMapper;
    }

    public SalaryInsightVO getSalaryInsight(String role, String city, String years) {
        try {
            String safeRole = defaultText(role, "Java后端");
            String safeCity = defaultText(city, "广州");
            String safeYears = defaultText(years, "3-5年");
            String query = safeRole + " " + safeCity + " " + safeYears + " 薪资 月薪";
            List<RagForgeChunk> chunks = ragForgeClient.searchJd(query, 30);
            String context = buildContext(chunks);
            if (context.isBlank()) {
                log.warn("getSalaryInsight: empty rag context, role={}, city={}", safeRole, safeCity);
                return fallbackSalaryInsight();
            }
            String prompt = MarketPrompts.salaryPrompt(safeRole, safeCity, safeYears, context);
            SalaryInsightVO parsed = parseLlmJson(prompt, SalaryInsightVO.class);
            return parsed != null ? parsed : fallbackSalaryInsight();
        } catch (Exception e) {
            log.warn("getSalaryInsight failed: {}", e.getMessage());
            return fallbackSalaryInsight();
        }
    }

    public SkillTrendsVO getSkillTrends(String role) {
        try {
            String safeRole = defaultText(role, "Java后端");
            String query = safeRole + " 技能要求 技术栈 必备";
            List<RagForgeChunk> chunks = ragForgeClient.searchJd(query, 40);
            String context = buildContext(chunks);
            if (context.isBlank()) {
                log.warn("getSkillTrends: empty rag context, role={}", safeRole);
                return fallbackSkillTrends();
            }
            String prompt = MarketPrompts.skillTrendsPrompt(safeRole, context);
            SkillTrendsVO parsed = parseLlmJson(prompt, SkillTrendsVO.class);
            return parsed != null ? parsed : fallbackSkillTrends();
        } catch (Exception e) {
            log.warn("getSkillTrends failed: {}", e.getMessage());
            return fallbackSkillTrends();
        }
    }

    public ResumeGapVO getResumeGap(Long userId) {
        try {
            ResumeContext resumeContext = resumeContextProvider.getResumeContext(userId);
            if (!resumeContext.isAvailable()
                    || resumeContext.getContent() == null
                    || resumeContext.getContent().isBlank()) {
                return emptyResumeGap();
            }
            String role = extractRoleFromResume(resumeContext);
            String query = role + " 岗位要求 技术能力";
            List<RagForgeChunk> chunks = ragForgeClient.searchJd(query, 30);
            String context = buildContext(chunks);
            if (context.isBlank()) {
                log.warn("getResumeGap: empty rag context, userId={}, role={}", userId, role);
                return fallbackResumeGap();
            }
            String prompt = MarketPrompts.resumeGapPrompt(resumeContext.getContent(), context);
            ResumeGapVO parsed = parseLlmJson(prompt, ResumeGapVO.class);
            return parsed != null ? parsed : fallbackResumeGap();
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
            List<RagForgeChunk> profileChunks = ragForgeClient.searchJd(safeCompany + " 公司 技术栈 规模", 20);
            List<RagForgeChunk> jobChunks = ragForgeClient.searchJd(safeCompany + " 岗位 招聘", 10);
            List<RagForgeChunk> merged = mergeDistinctChunks(profileChunks, jobChunks);
            String context = buildContext(merged);
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
            return parsed;
        } catch (Exception e) {
            log.warn("getCompanyInsight failed: company={}, err={}", company, e.getMessage());
            return fallbackCompanyInsight(company == null ? "" : company.trim());
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

    private static String buildContext(List<RagForgeChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "";
        }
        String joined = chunks.stream()
                .map(RagForgeChunk::content)
                .filter(content -> content != null && !content.isBlank())
                .collect(Collectors.joining("\n"));
        if (joined.length() <= MAX_CONTEXT_CHARS) {
            return joined;
        }
        return joined.substring(0, MAX_CONTEXT_CHARS);
    }

    private static List<RagForgeChunk> mergeDistinctChunks(List<RagForgeChunk> first, List<RagForgeChunk> second) {
        Map<String, RagForgeChunk> distinct = new LinkedHashMap<>();
        appendDistinct(distinct, first);
        appendDistinct(distinct, second);
        return new ArrayList<>(distinct.values());
    }

    private static void appendDistinct(Map<String, RagForgeChunk> distinct, List<RagForgeChunk> chunks) {
        if (chunks == null) {
            return;
        }
        for (RagForgeChunk chunk : chunks) {
            if (chunk == null) {
                continue;
            }
            String key = chunk.chunkId() != null
                    ? "id:" + chunk.chunkId()
                    : "content:" + (chunk.content() == null ? "" : chunk.content());
            distinct.putIfAbsent(key, chunk);
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
        return vo;
    }

    private static SkillTrendsVO fallbackSkillTrends() {
        SkillTrendsVO vo = new SkillTrendsVO();
        vo.setSkills(List.of());
        vo.setAiSummary(FALLBACK_SUMMARY);
        return vo;
    }

    private static ResumeGapVO fallbackResumeGap() {
        ResumeGapVO vo = new ResumeGapVO();
        vo.setHasSkills(List.of());
        vo.setMissingSkills(List.of());
        vo.setMatchScore(0);
        vo.setTopSuggestion(NO_DATA);
        vo.setAiSummary(FALLBACK_SUMMARY);
        return vo;
    }

    private static ResumeGapVO emptyResumeGap() {
        ResumeGapVO vo = new ResumeGapVO();
        vo.setHasSkills(List.of());
        vo.setMissingSkills(List.of());
        vo.setMatchScore(0);
        vo.setTopSuggestion("");
        vo.setAiSummary("");
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
        return vo;
    }
}
