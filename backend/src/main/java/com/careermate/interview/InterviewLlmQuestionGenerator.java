package com.careermate.interview;

import com.careermate.jobmatch.JobMatchJsonSupport;
import com.careermate.llm.LlmClient;
import com.careermate.llm.LlmProperties;
import com.careermate.llm.dto.ChatMessage;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.dto.ChatResponse;
import com.careermate.model.entity.JobMatchEntity;
import com.careermate.model.entity.ResumeEntity;
import com.careermate.agent.tool.rag.RagRetrieveScene;
import com.careermate.knowledge.KnowledgeRetrievalService;
import com.careermate.prompt.PromptRenderResult;
import com.careermate.prompt.PromptTemplateService;
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

    private final LlmClient llmClient;
    private final LlmProperties llmProperties;
    private final ObjectMapper objectMapper;
    private final JobMatchJsonSupport jobMatchJsonSupport;
    private final KnowledgeRetrievalService knowledgeRetrievalService;
    private final PromptTemplateService promptTemplateService;

    public InterviewLlmQuestionGenerator(
        LlmClient llmClient,
        LlmProperties llmProperties,
        ObjectMapper objectMapper,
        JobMatchJsonSupport jobMatchJsonSupport,
        KnowledgeRetrievalService knowledgeRetrievalService,
        PromptTemplateService promptTemplateService
    ) {
        this.llmClient = llmClient;
        this.llmProperties = llmProperties;
        this.objectMapper = objectMapper;
        this.jobMatchJsonSupport = jobMatchJsonSupport;
        this.knowledgeRetrievalService = knowledgeRetrievalService;
        this.promptTemplateService = promptTemplateService;
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

        String ragSection = buildRagContext(
            latestJobMatch.map(JobMatchEntity::getJobTitle).orElse(""),
            latestJobMatch.map(m -> String.join(" ", jobMatchJsonSupport.readStringList(m.getMissingSkills()))).orElse("")
        );
        String userPrompt = buildUserPrompt(resume, latestJobMatch, ragSection);

        PromptRenderResult systemPrompt = promptTemplateService.render("interview-question-generate");
        log.info("InterviewQ prompt template: promptId={} version={}",
                systemPrompt.promptId(), systemPrompt.version());
        ChatResponse response;
        try {
            ChatRequest request = ChatRequest.builder()
                .messages(List.of(
                    ChatMessage.builder().role("system").content(systemPrompt.content()).build(),
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

    private String buildUserPrompt(ResumeEntity resume, Optional<JobMatchEntity> latestJobMatch, String ragSection) {
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
        String rag = ragSection == null ? "" : ragSection;

        return """
            【目标公司】%s
            【目标岗位】%s
            【JD 摘要】
            %s

            【候选人简历摘要】
            %s

            【最近匹配命中技能】%s
            【最近匹配缺失技能】%s
            %s
            请基于上述信息生成 5 道题，严格输出符合 Schema 的 JSON。
            """.formatted(
                company.isBlank() ? "未指定" : company,
                jobTitle.isBlank() ? "未指定" : jobTitle,
                jdSnippet,
                resumeSummary,
                matchedSkills.isBlank() ? "(无)" : matchedSkills,
                missingSkills.isBlank() ? "(无)" : missingSkills,
                rag
            );
    }

    private String buildRagContext(String jobTitle, String missingSkills) {
        try {
            String q = (jobTitle == null ? "" : jobTitle.trim() + " ")
                     + (missingSkills == null || missingSkills.isBlank() ? "" : missingSkills + " ")
                     + "面试考点";
            String context = knowledgeRetrievalService.retrieveContextText(
                    RagRetrieveScene.INTERVIEW, q.trim(), 3);
            if (context.isBlank()) {
                return "";
            }
            return "\n【面试题参考知识（来自 RAGForge）】\n- " + context.replace("\n", "\n- ") + "\n";
        } catch (Exception e) {
            return "";
        }
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
