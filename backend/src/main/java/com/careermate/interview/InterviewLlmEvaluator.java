package com.careermate.interview;

import com.careermate.llm.LlmClient;
import com.careermate.llm.LlmProperties;
import com.careermate.llm.dto.ChatMessage;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.dto.ChatResponse;
import com.careermate.model.entity.InterviewQuestionEntity;
import com.careermate.ragforge.RagForgeChunk;
import com.careermate.ragforge.RagForgeClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 调用 LLM 评估面试回答。失败 / mock / 解析错 → 返回 Optional.empty()，由上层 fallback。
 */
@Slf4j
@Component
public class InterviewLlmEvaluator {

    private static final Pattern JSON_BLOCK = Pattern.compile("\\{[\\s\\S]*\\}");

    private static final String SYSTEM_PROMPT = """
        你是资深技术面试官，需要严格、专业地评估候选人的面试回答。请严格输出 JSON，不要任何解释文字、不要 markdown 围栏。

        输出 Schema：
        {
          "score": 整数 0~100,
          "feedback": "100~300 字的总体反馈",
          "strengths": ["...", ...],
          "improvements": ["...具体可执行的改进点...", ...]
        }

        评分标准：
        1. 回答覆盖参考要点的程度（最重要，占 50%）
        2. 技术深度（边界场景、性能、对比方案，占 30%）
        3. 表达结构（STAR / 总分总，占 20%）
        4. 单纯字数多不加分；废话连篇必须扣分
        5. 90+：完整覆盖要点 + 技术深度足 + 有量化结果
           70~89：覆盖大部分要点，缺少深度
           50~69：基础回答，缺少细节
           <50：跑题 / 回答不完整 / 概念性错误
        """;

    private final LlmClient llmClient;
    private final LlmProperties llmProperties;
    private final ObjectMapper objectMapper;
    private final RagForgeClient ragForgeClient;

    public InterviewLlmEvaluator(
        LlmClient llmClient,
        LlmProperties llmProperties,
        ObjectMapper objectMapper,
        RagForgeClient ragForgeClient
    ) {
        this.llmClient = llmClient;
        this.llmProperties = llmProperties;
        this.objectMapper = objectMapper;
        this.ragForgeClient = ragForgeClient;
    }

    public Optional<EvaluationStructuredResult> tryEvaluate(
        InterviewQuestionEntity question, String answerText, List<String> referencePoints
    ) {
        if (isMockProvider()) {
            return Optional.empty();
        }
        if (answerText == null || answerText.isBlank() || question == null) {
            return Optional.empty();
        }

        String ragSection = buildRagContext(question.getQuestionText() == null ? "" : question.getQuestionText());
        String userPrompt = buildUserPrompt(question, answerText, referencePoints, ragSection);

        ChatResponse response;
        try {
            ChatRequest request = ChatRequest.builder()
                .messages(List.of(
                    ChatMessage.builder().role("system").content(SYSTEM_PROMPT).build(),
                    ChatMessage.builder().role("user").content(userPrompt).build()
                ))
                .temperature(0.2)
                .build();
            response = llmClient.chat(request);
        } catch (Exception e) {
            log.warn("Interview LLM 调用失败（已降级）: err={}", e.getMessage());
            return Optional.empty();
        }

        if (response == null || response.getContent() == null || response.getContent().isBlank()) {
            log.warn("Interview LLM 返回为空，已降级");
            return Optional.empty();
        }

        String raw = response.getContent().trim();
        Matcher m = JSON_BLOCK.matcher(raw);
        if (!m.find()) {
            log.warn("Interview LLM 输出非 JSON，已降级: head=\"{}\"",
                raw.substring(0, Math.min(120, raw.length())));
            return Optional.empty();
        }
        String json = m.group();
        try {
            EvaluationStructuredResult parsed =
                objectMapper.readValue(json, EvaluationStructuredResult.class);
            if (!isValid(parsed)) {
                log.warn("Interview LLM JSON 校验失败，已降级: score={}", parsed.score());
                return Optional.empty();
            }
            return Optional.of(parsed);
        } catch (Exception e) {
            log.warn("Interview LLM JSON 解析失败（已降级）: err={}", e.getMessage());
            return Optional.empty();
        }
    }

    private boolean isMockProvider() {
        String provider = llmProperties.getProvider();
        return provider == null || provider.isBlank() || "mock".equalsIgnoreCase(provider.trim());
    }

    private String buildUserPrompt(InterviewQuestionEntity q, String answer, List<String> refs, String ragSection) {
        String qType = q.getQuestionType() == null ? "GENERAL" : q.getQuestionType();
        String qText = q.getQuestionText() == null ? "" : q.getQuestionText();
        String refsPart = (refs == null || refs.isEmpty())
            ? "(无)"
            : String.join("、", refs);
        String rag = ragSection == null ? "" : ragSection;
        return """
            【题目类型】%s
            【题目内容】%s
            【参考要点】%s
            %s
            【候选人回答】
            %s

            请基于上述信息严格输出符合 Schema 的 JSON。
            """.formatted(qType, qText, refsPart, rag, answer);
    }

    private String buildRagContext(String questionText) {
        try {
            List<RagForgeChunk> chunks = ragForgeClient.searchInterview(questionText, 3);
            if (chunks == null || chunks.isEmpty()) return "";
            StringBuilder sb = new StringBuilder("\n【参考答案知识点（来自 RAGForge）】\n");
            for (RagForgeChunk c : chunks) {
                String txt = c.content() == null ? "" : c.content();
                if (txt.length() > 200) txt = txt.substring(0, 200) + "...";
                sb.append("- ").append(txt).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private boolean isValid(EvaluationStructuredResult r) {
        if (r == null) return false;
        if (r.score() < 0 || r.score() > 100) return false;
        if (r.feedback() == null || r.feedback().isBlank()) return false;
        return true;
    }
}
