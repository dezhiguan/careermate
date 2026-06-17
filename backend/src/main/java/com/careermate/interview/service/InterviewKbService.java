package com.careermate.interview.service;

import com.careermate.agent.tool.rag.RagRetrieveRequest;
import com.careermate.agent.tool.rag.RagRetrieveScene;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class InterviewKbService {

    private static final int MAX_CONTEXT_CHARS = 4000;
    private static final Pattern JSON_BLOCK = Pattern.compile("\\{[\\s\\S]*\\}");

    private final KnowledgeRetrievalService knowledgeRetrievalService;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public InterviewKbService(
            KnowledgeRetrievalService knowledgeRetrievalService,
            LlmClient llmClient,
            ObjectMapper objectMapper
    ) {
        this.knowledgeRetrievalService = knowledgeRetrievalService;
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    public KbQuestionsVO getKbQuestions(String query) {
        try {
            String safeQuery = defaultText(query, "Java后端");
            String context = knowledgeRetrievalService.retrieveContextText(
                    RagRetrieveScene.INTERVIEW,
                    safeQuery + " 面试题 考点",
                    20
            );
            if (context.isBlank()) {
                log.warn("getKbQuestions: empty rag context, query={}", safeQuery);
                return fallbackKbQuestions(safeQuery);
            }
            String prompt = InterviewKbPrompts.kbQuestionsPrompt(safeQuery, context);
            KbQuestionsVO parsed = parseLlmJson(prompt, KbQuestionsVO.class);
            if (parsed == null) {
                return fallbackKbQuestions(safeQuery);
            }
            if (parsed.getQuery() == null || parsed.getQuery().isBlank()) {
                parsed.setQuery(safeQuery);
            }
            if (parsed.getQuestions() == null) {
                parsed.setQuestions(Collections.emptyList());
            }
            return parsed;
        } catch (Exception e) {
            log.warn("getKbQuestions failed: query={}, err={}", query, e.getMessage());
            return fallbackKbQuestions(defaultText(query, "Java后端"));
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

    private static KbQuestionsVO fallbackKbQuestions(String query) {
        KbQuestionsVO vo = new KbQuestionsVO();
        vo.setQuery(query);
        vo.setQuestions(Collections.emptyList());
        vo.setAiSummary("暂无相关面试资料");
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
