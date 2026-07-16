package com.careermate.company.service;

import com.careermate.agent.tool.rag.RagRetrieveRequest;
import com.careermate.agent.tool.rag.RagRetrieveResult;
import com.careermate.agent.tool.rag.RagRetrieveScene;
import com.careermate.company.CompanyAtmospherePrompts;
import com.careermate.company.dto.CompanyAtmosphereVO;
import com.careermate.knowledge.KnowledgeRetrievalService;
import com.careermate.knowledge.KnowledgeRetrievalSupport;
import com.careermate.llm.LlmClient;
import com.careermate.llm.dto.ChatMessage;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.dto.ChatResponse;
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
 * 公司氛围服务 —— 依据目标公司知识库（COMPANY scene）产出结构化的「公司氛围」。
 *
 * <p>遵循 market 智能分析的成熟范式：读 KB → 拼上下文 → LLM 抽取 JSON → 附来源 → 全程降级不抛异常。
 * 「不胡说」：证据不足时返回 dataAvailable=false 的兜底结果，明说暂无情报。
 */
@Slf4j
@Service
public class CompanyAtmosphereService {

    private static final int MAX_CONTEXT_CHARS = 4000;
    private static final String NO_DATA = "";
    private static final String NO_ATMOSPHERE_SUMMARY =
            "暂无该公司的氛围情报，建议结合面试沟通与公开渠道进一步了解。";
    private static final Pattern JSON_BLOCK = Pattern.compile("\\{[\\s\\S]*\\}");
    private static final Set<String> VALID_SENTIMENTS = Set.of("POSITIVE", "NEGATIVE", "NEUTRAL");

    private final KnowledgeRetrievalService knowledgeRetrievalService;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public CompanyAtmosphereService(
            KnowledgeRetrievalService knowledgeRetrievalService,
            LlmClient llmClient,
            ObjectMapper objectMapper
    ) {
        this.knowledgeRetrievalService = knowledgeRetrievalService;
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 获取某公司的氛围情报。
     *
     * @param company 公司名（不可为空）
     * @return 结构化氛围；无据时 dataAvailable=false 且给出兜底说明
     */
    public CompanyAtmosphereVO getCompanyAtmosphere(String company) {
        try {
            if (company == null || company.isBlank()) {
                log.warn("getCompanyAtmosphere: company is blank");
                return fallback("");
            }
            String safeCompany = company.trim();

            RagRetrieveResult ragResult = knowledgeRetrievalService.retrieveMerged(List.of(
                    RagRetrieveRequest.builder()
                            .query(safeCompany + " 公司 工作强度 加班 氛围 文化")
                            .scene(RagRetrieveScene.COMPANY)
                            .topK(20)
                            .build(),
                    RagRetrieveRequest.builder()
                            .query(safeCompany + " 面试 流程 风格 团队 口碑")
                            .scene(RagRetrieveScene.COMPANY)
                            .topK(15)
                            .build()
            ));

            String context = toContextText(ragResult);
            if (context.isBlank()) {
                log.warn("getCompanyAtmosphere: empty rag context, company={}", safeCompany);
                return fallback(safeCompany);
            }

            String prompt = CompanyAtmospherePrompts.atmospherePrompt(safeCompany, context);
            CompanyAtmosphereVO parsed = parseLlmJson(prompt, CompanyAtmosphereVO.class);
            if (parsed == null) {
                return fallback(safeCompany);
            }
            if (parsed.getCompanyName() == null || parsed.getCompanyName().isBlank()) {
                parsed.setCompanyName(safeCompany);
            }
            parsed.setDataAvailable(true);
            normalizeTags(parsed);
            attachSources(parsed, ragResult);
            return parsed;
        } catch (Exception e) {
            log.warn("getCompanyAtmosphere failed: company={}, err={}", company, e.getMessage());
            return fallback(company == null ? "" : company.trim());
        }
    }

    private static String toContextText(RagRetrieveResult ragResult) {
        if (ragResult == null || !ragResult.isSuccess()) {
            return "";
        }
        return KnowledgeRetrievalSupport.joinChunkContents(ragResult.getChunks(), MAX_CONTEXT_CHARS);
    }

    private void attachSources(CompanyAtmosphereVO vo, RagRetrieveResult ragResult) {
        vo.setCitations(knowledgeRetrievalService.toMarketCitations(ragResult));
        vo.setSourceSummaries(knowledgeRetrievalService.toSourceSummaries(ragResult));
    }

    /** 归一化文化标签：剔除空标签，统一情绪极性为 POSITIVE/NEGATIVE/NEUTRAL。 */
    private static void normalizeTags(CompanyAtmosphereVO vo) {
        List<CompanyAtmosphereVO.CultureTag> tags = vo.getCultureTags();
        if (tags == null || tags.isEmpty()) {
            vo.setCultureTags(new ArrayList<>());
            return;
        }
        List<CompanyAtmosphereVO.CultureTag> normalized = new ArrayList<>();
        for (CompanyAtmosphereVO.CultureTag tag : tags) {
            if (tag == null || tag.getLabel() == null || tag.getLabel().isBlank()) {
                continue;
            }
            String sentiment = tag.getSentiment() == null
                    ? "NEUTRAL"
                    : tag.getSentiment().trim().toUpperCase(Locale.ROOT);
            if (!VALID_SENTIMENTS.contains(sentiment)) {
                sentiment = "NEUTRAL";
            }
            normalized.add(new CompanyAtmosphereVO.CultureTag(tag.getLabel().trim(), sentiment));
        }
        vo.setCultureTags(normalized);
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
            log.warn("Company atmosphere LLM response empty, type={}", type.getSimpleName());
            return null;
        }
        String raw = response.getContent().trim();
        Matcher matcher = JSON_BLOCK.matcher(raw);
        if (!matcher.find()) {
            log.warn("Company atmosphere LLM output not JSON, head={}",
                    raw.substring(0, Math.min(120, raw.length())));
            return null;
        }
        try {
            return objectMapper.readValue(matcher.group(), type);
        } catch (Exception e) {
            log.warn("Company atmosphere LLM JSON parse failed, err={}", e.getMessage());
            return null;
        }
    }

    private static CompanyAtmosphereVO fallback(String companyName) {
        CompanyAtmosphereVO vo = new CompanyAtmosphereVO();
        vo.setCompanyName(companyName == null || companyName.isBlank() ? "未知公司" : companyName);
        vo.setWorkIntensity(NO_DATA);
        vo.setTeamReputation(NO_DATA);
        vo.setInterviewStyle(NO_DATA);
        vo.setOvertimeSignal(NO_DATA);
        vo.setCultureTags(Collections.emptyList());
        vo.setAiSummary(NO_ATMOSPHERE_SUMMARY);
        vo.setDataAvailable(false);
        vo.setCitations(Collections.emptyList());
        vo.setSourceSummaries(Collections.emptyList());
        return vo;
    }
}
