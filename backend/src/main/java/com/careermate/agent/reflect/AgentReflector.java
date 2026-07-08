package com.careermate.agent.reflect;

import com.careermate.llm.LlmClient;
import com.careermate.llm.dto.ChatMessage;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.dto.ChatResponse;
import com.careermate.mapper.AgentReflectionMapper;
import com.careermate.model.entity.AgentReflectionEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * A3：独立审视者。按 success_criteria 判断执行结果是否真正达标（低温度，防被"答案丰富"迷惑）。
 * reflector 严禁调工具；解析失败保守 ACCEPT，避免闭环卡死。
 */
@Slf4j
@Service
public class AgentReflector {

    private static final String SYSTEM_PROMPT = """
            你是严格的 Agent 审视者。给你一个 plan、成功标准和一份执行结果。
            判断结果是否"真正达成"目标，不要被表面的答案丰富迷惑；重点看是否被知识库语料支撑、有无编造。
            禁止复述执行过程，只关注差距。只输出 JSON：
            {"satisfied":true/false,"confidence":0.0-1.0,"gaps":["..."],"suggestions":["..."],"verdict":"REVISE"|"ACCEPT"|"FAIL"}
            """;

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final AgentReflectionMapper agentReflectionMapper;
    private final ReflectionProperties reflectionProperties;

    public AgentReflector(LlmClient llmClient, ObjectMapper objectMapper,
                          AgentReflectionMapper agentReflectionMapper, ReflectionProperties reflectionProperties) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.agentReflectionMapper = agentReflectionMapper;
        this.reflectionProperties = reflectionProperties;
    }

    public Reflection review(AgentPlan plan, String actSummary) {
        Reflection reflection = callAndParse(plan, actSummary);
        persist(plan, reflection);
        return reflection;
    }

    private Reflection callAndParse(AgentPlan plan, String actSummary) {
        try {
            String user = "目标：" + plan.goals()
                    + "\n成功标准：" + plan.successCriteria()
                    + "\n执行结果：" + (actSummary == null ? "" : actSummary);
            ChatRequest.ChatRequestBuilder req = ChatRequest.builder()
                    .temperature(reflectionProperties.getReflectorTemperature())
                    .messages(List.of(
                            ChatMessage.builder().role("system").content(SYSTEM_PROMPT).build(),
                            ChatMessage.builder().role("user").content(user).build()));
            if (StringUtils.hasText(reflectionProperties.getReflectorModel())) {
                req.model(reflectionProperties.getReflectorModel().trim());
            }
            ChatResponse resp = llmClient.chat(req.build());
            JsonNode json = ReflectionJsonSupport.extractJson(objectMapper, resp == null ? null : resp.getContent());
            if (json == null) {
                return Reflection.accept();
            }
            boolean satisfied = ReflectionJsonSupport.boolField(json, "satisfied", true);
            double confidence = ReflectionJsonSupport.doubleField(json, "confidence", 0.5);
            List<String> gaps = ReflectionJsonSupport.stringList(json, "gaps");
            List<String> suggestions = ReflectionJsonSupport.stringList(json, "suggestions");
            String verdict = ReflectionJsonSupport.textField(json, "verdict", satisfied ? "ACCEPT" : "REVISE");
            return new Reflection(satisfied, confidence, gaps, suggestions, verdict);
        } catch (Exception e) {
            log.warn("reflection 生成失败，保守 ACCEPT（不影响对话）: {}", e.getMessage());
            return Reflection.accept();
        }
    }

    private void persist(AgentPlan plan, Reflection reflection) {
        try {
            AgentReflectionEntity e = new AgentReflectionEntity();
            e.setRunId(plan.runId());
            e.setRoundNo(plan.roundNo());
            e.setPlanId(plan.planId());
            e.setSatisfied(reflection.satisfied());
            e.setConfidence(reflection.confidence());
            e.setGaps(writeJson(reflection.gaps()));
            e.setSuggestions(writeJson(reflection.suggestions()));
            e.setVerdict(reflection.verdict());
            e.setReflectorModel(StringUtils.hasText(reflectionProperties.getReflectorModel())
                    ? reflectionProperties.getReflectorModel() : "default");
            agentReflectionMapper.insert(e);
        } catch (Exception e) {
            log.warn("reflection 落库失败（不影响对话）: {}", e.getMessage());
        }
    }

    private String writeJson(List<String> list) {
        try {
            return objectMapper.writeValueAsString(list == null ? List.of() : list);
        } catch (Exception e) {
            return "[]";
        }
    }
}
