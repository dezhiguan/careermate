package com.careermate.interview;

import com.careermate.jobmatch.JobMatchJsonSupport;
import com.careermate.llm.LlmClient;
import com.careermate.llm.LlmProperties;
import com.careermate.llm.dto.ChatMessage;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.dto.ChatResponse;
import com.careermate.model.entity.JobMatchEntity;
import com.careermate.model.entity.ResumeEntity;
import com.careermate.ragforge.RagForgeChunk;
import com.careermate.ragforge.RagForgeClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 调用 LLM 生成 5 道针对性面试题。失败 / mock / 解析错 → 返回 Optional.empty()，由上层 fallback。
 */
@Slf4j
@Component
public class InterviewLlmQuestionGenerator {

    private static final Pattern JSON_BLOCK = Pattern.compile("\\{[\\s\\S]*\\}");
    private static final Set<String> VALID_TYPES =
        Set.of("PROJECT", "SKILL", "GAP", "SYSTEM_DESIGN", "BEHAVIOR");

    private static final String SYSTEM_PROMPT = """
        你是资深技术面试官，需要为候选人生成 5 道针对性面试题。请严格输出 JSON，不要任何解释文字、不要 markdown 围栏。

        输出 Schema：
        {
          "questions": [
            { "questionNo": 1, "questionType": "PROJECT", "questionText": "...", "referencePoints": ["..."] },
            ... 恰好 5 道
          ]
        }

        题目分布（必须按此顺序与类型）：
        1. PROJECT：基于简历真实项目，让候选人讲解最有挑战的一个
        2. SKILL：技术深度题，从命中技能里挑一个最重要的考
        3. GAP：技能缺口题，从缺失技能里挑一个考，验证候选人学习能力
        4. SYSTEM_DESIGN：贴合目标岗位场景的系统设计题
        5. BEHAVIOR：行为/沟通/团队协作类问题

        要求：
        - questionText 必须有具体场景，不能是空泛的"请介绍 XX"
        - referencePoints 是评分参考要点（3~5 条），用于后续打分
        - 题目必须围绕候选人简历和目标岗位，不要通用模板题
        - questionNo 严格 1 到 5
        - questionType 只能用大写枚举值：PROJECT / SKILL / GAP / SYSTEM_DESIGN / BEHAVIOR
        """;

    private final LlmClient llmClient;
    private final LlmProperties llmProperties;
    private final ObjectMapper objectMapper;
    private final JobMatchJsonSupport jobMatchJsonSupport;

    public InterviewLlmQuestionGenerator(
        LlmClient llmClient,
        LlmProperties llmProperties,
        ObjectMapper objectMapper,
        JobMatchJsonSupport jobMatchJsonSupport
    ) {
        this.llmClient = llmClient;
        this.llmProperties = llmProperties;
        this.objectMapper = objectMapper;
        this.jobMatchJsonSupport = jobMatchJsonSupport;
    }

    public Optional<List<GeneratedQuestionList.LlmQuestion>> tryGenerate(
        ResumeEntity resume, Optional<JobMatchEntity> latestJobMatch
    ) {
        if (isMockProvider()) {
            return Optional.empty();
        }
        if (resume == null || resume.getContent() == null || resume.getContent().isBlank()) {
            return Optional.empty();
        }

        String userPrompt = buildUserPrompt(resume, latestJobMatch);

        ChatResponse response;
        try {
            ChatRequest request = ChatRequest.builder()
                .messages(List.of(
                    ChatMessage.builder().role("system").content(SYSTEM_PROMPT).build(),
                    ChatMessage.builder().role("user").content(userPrompt).build()
                ))
                .temperature(0.5)
                .build();
            response = llmClient.chat(request);
        } catch (Exception e) {
            log.warn("InterviewQ LLM 调用失败（已降级）: err={}", e.getMessage());
            return Optional.empty();
        }

        if (response == null || response.getContent() == null || response.getContent().isBlank()) {
            log.warn("InterviewQ LLM 返回为空，已降级");
            return Optional.empty();
        }

        String raw = response.getContent().trim();
        Matcher m = JSON_BLOCK.matcher(raw);
        if (!m.find()) {
            log.warn("InterviewQ LLM 输出非 JSON，已降级: head=\"{}\"",
                raw.substring(0, Math.min(120, raw.length())));
            return Optional.empty();
        }
        String json = m.group();
        try {
            GeneratedQuestionList parsed = objectMapper.readValue(json, GeneratedQuestionList.class);
            if (!isValid(parsed)) {
                log.warn("InterviewQ LLM JSON 校验失败（题数/类型不合规），已降级");
                return Optional.empty();
            }
            return Optional.of(parsed.questions());
        } catch (Exception e) {
            log.warn("InterviewQ LLM JSON 解析失败（已降级）: err={}", e.getMessage());
            return Optional.empty();
        }
    }

    private boolean isMockProvider() {
        String provider = llmProperties.getProvider();
        return provider == null || provider.isBlank() || "mock".equalsIgnoreCase(provider.trim());
    }

    private String buildUserPrompt(ResumeEntity resume, Optional<JobMatchEntity> latestJobMatch) {
        String resumeContent = resume.getContent() == null ? "" : resume.getContent();
        String resumeSummary = resumeContent.length() > 800
            ? resumeContent.substring(0, 800) + "..."
            : resumeContent;

        String jobTitle = latestJobMatch.map(JobMatchEntity::getJobTitle).orElse("未指定");
        String company = latestJobMatch.map(JobMatchEntity::getCompanyName).orElse("未指定");
        String matchedSkills = latestJobMatch
            .map(m -> String.join("、", jobMatchJsonSupport.readStringList(m.getMatchedSkills())))
            .orElse("");
        String missingSkills = latestJobMatch
            .map(m -> String.join("、", jobMatchJsonSupport.readStringList(m.getMissingSkills())))
            .orElse("");
        String jdSnippet = latestJobMatch
            .map(JobMatchEntity::getJdContent)
            .filter(s -> s != null && !s.isBlank())
            .map(s -> s.length() > 400 ? s.substring(0, 400) + "..." : s)
            .orElse("(无)");

        return """
            【目标公司】%s
            【目标岗位】%s
            【JD 摘要】
            %s

            【候选人简历摘要】
            %s

            【最近匹配命中技能】%s
            【最近匹配缺失技能】%s

            请基于上述信息生成 5 道题，严格输出符合 Schema 的 JSON。
            """.formatted(
                company.isBlank() ? "未指定" : company,
                jobTitle.isBlank() ? "未指定" : jobTitle,
                jdSnippet,
                resumeSummary,
                matchedSkills.isBlank() ? "(无)" : matchedSkills,
                missingSkills.isBlank() ? "(无)" : missingSkills
            );
    }

    private boolean isValid(GeneratedQuestionList list) {
        if (list == null || list.questions() == null || list.questions().size() != 5) {
            return false;
        }
        for (int i = 0; i < 5; i++) {
            GeneratedQuestionList.LlmQuestion q = list.questions().get(i);
            if (q == null) return false;
            if (q.questionNo() != i + 1) return false;
            if (q.questionType() == null || !VALID_TYPES.contains(q.questionType())) return false;
            if (q.questionText() == null || q.questionText().isBlank()) return false;
        }
        return true;
    }
}
