package com.careermate.agent.memory.ltm;

import com.careermate.agent.reflect.ReflectionJsonSupport;
import com.careermate.llm.LlmClient;
import com.careermate.llm.dto.ChatMessage;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.dto.ChatResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

/**
 * A4：把对话蒸馏为结构化 fact 并入库。强约束 JSON schema，最多 5 条；消息过少跳过。
 */
@Slf4j
@Service
public class ConsolidationService {

    private static final int MIN_MESSAGES = 4;
    private static final int MAX_FACTS = 5;
    private static final Set<String> VALID_TYPES = Set.of(
            "PREFERENCE", "SKILL", "EXPERIENCE", "GOAL", "CONSTRAINT");

    private static final String SYSTEM_PROMPT = """
            从以下求职对话中蒸馏该用户的稳定事实（偏好/技能/经历/目标/约束）。
            只保留明确、可复用、跨会话有价值的事实；忽略一次性问答。最多 5 条。
            fact_type ∈ {PREFERENCE,SKILL,EXPERIENCE,GOAL,CONSTRAINT}。只输出 JSON：
            {"facts":[{"fact_type":"PREFERENCE","fact_text":"只考虑远程岗位","confidence":0.8}]}
            """;

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final LongTermMemoryService longTermMemoryService;

    public ConsolidationService(LlmClient llmClient, ObjectMapper objectMapper,
                                LongTermMemoryService longTermMemoryService) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.longTermMemoryService = longTermMemoryService;
    }

    /**
     * 蒸馏一个用户的对话为 fact 并入库。
     *
     * @return 入库 fact 数
     */
    public int consolidate(Long userId, List<String> messages) {
        if (userId == null || messages == null || messages.size() < MIN_MESSAGES) {
            return 0;
        }
        try {
            String convo = String.join("\n", messages);
            ChatResponse resp = llmClient.chat(ChatRequest.builder()
                    .temperature(0.2)
                    .messages(List.of(
                            ChatMessage.builder().role("system").content(SYSTEM_PROMPT).build(),
                            ChatMessage.builder().role("user").content(convo).build()))
                    .build());
            JsonNode json = ReflectionJsonSupport.extractJson(objectMapper, resp == null ? null : resp.getContent());
            if (json == null) {
                return 0;
            }
            JsonNode facts = json.path("facts");
            if (!facts.isArray()) {
                return 0;
            }
            int stored = 0;
            for (JsonNode f : facts) {
                if (stored >= MAX_FACTS) {
                    break;
                }
                String type = ReflectionJsonSupport.textField(f, "fact_type", "").toUpperCase();
                String text = ReflectionJsonSupport.textField(f, "fact_text", "");
                double conf = ReflectionJsonSupport.doubleField(f, "confidence", 0.6);
                if (!VALID_TYPES.contains(type) || !StringUtils.hasText(text)) {
                    continue;
                }
                if (longTermMemoryService.store(userId, type, text, conf)) {
                    stored++;
                }
            }
            return stored;
        } catch (Exception e) {
            log.warn("对话蒸馏失败（跳过该用户，不影响系统）: userId={}, {}", userId, e.getMessage());
            return 0;
        }
    }
}
