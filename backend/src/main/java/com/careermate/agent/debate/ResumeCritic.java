package com.careermate.agent.debate;

import com.careermate.agent.reflect.ReflectionJsonSupport;
import com.careermate.llm.LlmClient;
import com.careermate.llm.dto.ChatMessage;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.dto.ChatResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * B1：Critic——独立审视简历稿对 JD 的匹配与真实性（低温度）。输出结构化 JSON。
 * 独立于 Writer 的 system prompt（防 self-bias）；解析失败返回中性分，避免 debate 卡死。
 */
@Slf4j
@Service
public class ResumeCritic {

    private static final String SYSTEM = """
            你是严格的简历评审官。对照 JD 审视简历稿：关键词/任职要求覆盖、量化成果、真实性（有无编造迹象）。
            不要复述稿件，只给评分与可执行改进。只输出 JSON：
            {"satisfaction":0.0-1.0,"criticism":"最关键的不足","suggestion":"具体如何改"}
            """;

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final DebateProperties properties;

    public ResumeCritic(LlmClient llmClient, ObjectMapper objectMapper, DebateProperties properties) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public CriticVerdict review(String jd, String draft) {
        try {
            String user = "目标 JD：\n" + (jd == null ? "" : jd) + "\n\n简历稿：\n" + (draft == null ? "" : draft);
            ChatResponse resp = llmClient.chat(ChatRequest.builder()
                    .temperature(properties.getCriticTemperature())
                    .messages(List.of(
                            ChatMessage.builder().role("system").content(SYSTEM).build(),
                            ChatMessage.builder().role("user").content(user).build()))
                    .build());
            JsonNode json = ReflectionJsonSupport.extractJson(objectMapper, resp == null ? null : resp.getContent());
            if (json == null) {
                return CriticVerdict.neutral();
            }
            double satisfaction = clamp(ReflectionJsonSupport.doubleField(json, "satisfaction", 0.5));
            String criticism = ReflectionJsonSupport.textField(json, "criticism", "");
            String suggestion = ReflectionJsonSupport.textField(json, "suggestion", "");
            return new CriticVerdict(satisfaction, criticism, suggestion);
        } catch (Exception e) {
            log.warn("Critic 评审失败，返回中性分: {}", e.getMessage());
            return CriticVerdict.neutral();
        }
    }

    private double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
