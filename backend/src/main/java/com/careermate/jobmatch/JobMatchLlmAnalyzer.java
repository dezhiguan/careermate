package com.careermate.jobmatch;

import com.careermate.llm.LlmClient;
import com.careermate.llm.LlmProperties;
import com.careermate.llm.dto.ChatMessage;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.dto.ChatResponse;
import com.careermate.agent.tool.rag.RagRetrieveRequest;
import com.careermate.agent.tool.rag.RagRetrieveScene;
import com.careermate.agent.tool.rag.RagRetrieveResult;
import com.careermate.knowledge.KnowledgeRetrievalService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 调用 LLM 完成简历 vs JD 的语义匹配分析。
 * 失败（mock provider / 超时 / JSON 解析失败 / LLM 返回不合规）→ 返回 Optional.empty()，由上层 fallback。
 */
@Slf4j
@Component
public class JobMatchLlmAnalyzer {

    /** 严格提取响应里第一个 {...} JSON 块，兼容 LLM 偶尔带 markdown 围栏的情况 */
    private static final Pattern JSON_BLOCK = Pattern.compile("\\{[\\s\\S]*\\}");

    private static final String SYSTEM_PROMPT = """
        你是资深招聘顾问，擅长基于简历与 JD 做语义匹配分析。请严格输出 JSON，不要任何解释文字、不要 markdown 围栏。

        输出 Schema：
        {
          "matchScore": 整数 0~100,
          "matchLevel": "HIGH" | "MEDIUM" | "LOW",
          "matchedSkills": ["...", ...],
          "missingSkills": ["...", ...],
          "strengths": ["...一句话...", ...],
          "risks": ["...一句话...", ...],
          "suggestions": ["...具体可执行...", ...],
          "analysisSummary": "100~200 字总结"
        }

        分析要求：
        1. matchScore 必须基于「JD 要求覆盖度 + 简历项目相关度」综合给出
        2. 如果 JD 提到「消息中间件」而简历有 Kafka/RocketMQ → 必须算命中（语义等价）
        3. matchLevel：>=80 HIGH，>=60 MEDIUM，否则 LOW
        4. matchedSkills / missingSkills 用最常见的技术名词形式（Java / Spring Boot / Kubernetes 等）
        5. analysisSummary 必须包含分数 + 等级 + 关键命中 + 关键缺失
        """;

    private final LlmClient llmClient;
    private final LlmProperties llmProperties;
    private final ObjectMapper objectMapper;
    private final KnowledgeRetrievalService knowledgeRetrievalService;

    public JobMatchLlmAnalyzer(
        LlmClient llmClient,
        LlmProperties llmProperties,
        ObjectMapper objectMapper,
        KnowledgeRetrievalService knowledgeRetrievalService
    ) {
        this.llmClient = llmClient;
        this.llmProperties = llmProperties;
        this.objectMapper = objectMapper;
        this.knowledgeRetrievalService = knowledgeRetrievalService;
    }

    public Optional<JobMatchStructuredResult> tryAnalyze(
        String resumeContent, String jdContent, String jobTitle
    ) {
        // 1. mock provider 直接返回空，让上层走 fallback（保持现有 mock 行为）
        if (isMockProvider()) {
            return Optional.empty();
        }
        if (jdContent == null || jdContent.isBlank() || resumeContent == null || resumeContent.isBlank()) {
            return Optional.empty();
        }

        // 2. 拉 RAGForge 行业 JD 参考片段（前置注入，让 LLM 基于行业参考做判断）
        String ragSection = buildRagContext(jobTitle, jdContent);

        // 3. 拼用户 prompt
        String userPrompt = buildUserPrompt(resumeContent, jdContent, jobTitle, ragSection);

        // 4. 调 LLM
        ChatResponse response;
        try {
            ChatRequest request = ChatRequest.builder()
                .messages(List.of(
                    ChatMessage.builder().role("system").content(SYSTEM_PROMPT).build(),
                    ChatMessage.builder().role("user").content(userPrompt).build()
                ))
                .temperature(0.3)
                .build();
            response = llmClient.chat(request);
        } catch (Exception e) {
            log.warn("JobMatch LLM 调用失败（已降级）: err={}", e.getMessage());
            return Optional.empty();
        }

        if (response == null || response.getContent() == null || response.getContent().isBlank()) {
            log.warn("JobMatch LLM 返回为空，已降级");
            return Optional.empty();
        }

        // 5. 解析 JSON（兼容 LLM 误带 markdown 围栏的情况）
        String raw = response.getContent().trim();
        Matcher m = JSON_BLOCK.matcher(raw);
        if (!m.find()) {
            log.warn("JobMatch LLM 输出非 JSON，已降级: head=\"{}\"",
                raw.substring(0, Math.min(120, raw.length())));
            return Optional.empty();
        }
        String json = m.group();
        try {
            JobMatchStructuredResult parsed = objectMapper.readValue(json, JobMatchStructuredResult.class);
            if (!isValid(parsed)) {
                log.warn("JobMatch LLM JSON 校验失败，已降级: matchScore={} matchLevel={}",
                    parsed.matchScore(), parsed.matchLevel());
                return Optional.empty();
            }
            return Optional.of(parsed);
        } catch (Exception e) {
            log.warn("JobMatch LLM JSON 解析失败（已降级）: err={}", e.getMessage());
            return Optional.empty();
        }
    }

    private boolean isMockProvider() {
        String provider = llmProperties.getProvider();
        return provider == null || provider.isBlank() || "mock".equalsIgnoreCase(provider.trim());
    }

    private String buildRagContext(String jobTitle, String jdContent) {
        try {
            String preview = jdContent.length() > 200 ? jdContent.substring(0, 200) : jdContent;
            String q = (jobTitle == null || jobTitle.isBlank() ? "" : jobTitle.trim() + " ") + preview;
            String context = knowledgeRetrievalService.retrieveContextText(
                    RagRetrieveScene.OPPORTUNITY, q.trim(), 3);
            if (context.isBlank()) {
                return "";
            }
            return "\n【行业 JD 参考片段（来自 RAGForge）】\n- " + context.replace("\n", "\n- ") + "\n";
        } catch (Exception e) {
            return "";
        }
    }

    private String buildUserPrompt(String resume, String jd, String jobTitle, String ragSection) {
        String title = jobTitle == null ? "目标岗位" : jobTitle.trim();
        return """
            【目标岗位】%s

            【候选人简历】
            %s

            【岗位 JD】
            %s
            %s
            请基于上述信息严格输出符合 Schema 的 JSON。
            """.formatted(title, resume, jd, ragSection);
    }

    private boolean isValid(JobMatchStructuredResult r) {
        if (r == null) return false;
        if (r.matchScore() < 0 || r.matchScore() > 100) return false;
        String lv = r.matchLevel();
        if (lv == null) return false;
        return lv.equals("HIGH") || lv.equals("MEDIUM") || lv.equals("LOW");
    }
}
