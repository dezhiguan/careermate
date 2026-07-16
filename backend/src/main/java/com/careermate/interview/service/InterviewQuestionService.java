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

    public InterviewQuestionService(
            KnowledgeRetrievalService knowledgeRetrievalService,
            ResumeContextProvider resumeContextProvider,
            LlmClient llmClient,
            ObjectMapper objectMapper
    ) {
        this.knowledgeRetrievalService = knowledgeRetrievalService;
        this.resumeContextProvider = resumeContextProvider;
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 为一条 JD 生成针对性面试题。
     *
     * @param jdDocId 目标 JD 的 RAGForge docId
     * @param userId  用户 ID（用于取简历个性化；可为 null）
     * @return 面试题列表；JD 未找到时 dataAvailable=false
     */
    public JdAwareQuestionsVO generateJdAwareQuestions(Long jdDocId, Long userId) {
        try {
            if (jdDocId == null) {
                log.warn("generateJdAwareQuestions: jdDocId is null");
                return fallback(null);
            }

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

            String resumeText = resolveResumeText(userId);

            RagRetrieveResult interviewResult = knowledgeRetrievalService.retrieve(RagRetrieveRequest.builder()
                    .query(jdTitle + " 面试 高频题 考点")
                    .scene(RagRetrieveScene.INTERVIEW)
                    .topK(INTERVIEW_TOP_K)
                    .build());
            String interviewContext = toContextText(interviewResult);

            String prompt = InterviewQuestionPrompts.jdAwarePrompt(jdTitle, jdContext, resumeText, interviewContext);
            JdAwareQuestionsVO parsed = parseLlmJson(prompt, JdAwareQuestionsVO.class);
            if (parsed == null) {
                return fallback(jdDocId);
            }
            parsed.setJdDocId(jdDocId);
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
