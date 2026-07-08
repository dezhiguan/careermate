package com.careermate.agent.reflect;

import com.careermate.llm.LlmClient;
import com.careermate.llm.LlmProperties;
import com.careermate.llm.dto.ChatMessage;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.dto.ChatResponse;
import com.careermate.mapper.AgentPlanMapper;
import com.careermate.model.entity.AgentPlanEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * A3：把用户任务拆解为结构化 plan（goals/subgoals/success_criteria）。success_criteria 要求知识库锚定。
 */
@Slf4j
@Service
public class AgentPlanner {

    private static final String SYSTEM_PROMPT = """
            你是求职 Agent 的任务拆解专家。把用户任务拆成结构化 plan。
            要求：goals ≤ 3；subgoals ≤ 8；success_criteria 必须"知识库锚定"且可核验
            （例如"覆盖岗位JD库命中的≥5项任职要求"、"薪资结论有薪资行情库分位数支撑"、"引用来源可核验、无脱离语料的编造"）。
            只输出 JSON，不要任何解释：
            {"goals":["..."],"subgoals":["..."],"success_criteria":["..."]}
            """;

    private final LlmClient llmClient;
    private final LlmProperties llmProperties;
    private final ObjectMapper objectMapper;
    private final AgentPlanMapper agentPlanMapper;
    private final ReflectionProperties reflectionProperties;

    public AgentPlanner(LlmClient llmClient, LlmProperties llmProperties, ObjectMapper objectMapper,
                        AgentPlanMapper agentPlanMapper, ReflectionProperties reflectionProperties) {
        this.llmClient = llmClient;
        this.llmProperties = llmProperties;
        this.objectMapper = objectMapper;
        this.agentPlanMapper = agentPlanMapper;
        this.reflectionProperties = reflectionProperties;
    }

    public AgentPlan plan(String runId, String userMessage) {
        AgentPlan plan = callAndParse(runId, userMessage);
        return persist(plan);
    }

    private AgentPlan callAndParse(String runId, String userMessage) {
        try {
            ChatResponse resp = llmClient.chat(ChatRequest.builder()
                    .temperature(reflectionProperties.getPlannerTemperature())
                    .messages(List.of(
                            ChatMessage.builder().role("system").content(SYSTEM_PROMPT).build(),
                            ChatMessage.builder().role("user").content(userMessage).build()))
                    .build());
            JsonNode json = ReflectionJsonSupport.extractJson(objectMapper, resp == null ? null : resp.getContent());
            List<String> goals = ReflectionJsonSupport.stringList(json, "goals");
            List<String> subgoals = ReflectionJsonSupport.stringList(json, "subgoals");
            List<String> criteria = ReflectionJsonSupport.stringList(json, "success_criteria");
            if (goals.isEmpty()) {
                return fallbackPlan(runId, userMessage);
            }
            return new AgentPlan(null, runId, 0,
                    capped(goals, 3), capped(subgoals, 8), criteria, null);
        } catch (Exception e) {
            log.warn("plan 生成失败，回退单目标 plan（不影响对话）: {}", e.getMessage());
            return fallbackPlan(runId, userMessage);
        }
    }

    private AgentPlan fallbackPlan(String runId, String userMessage) {
        return new AgentPlan(null, runId, 0,
                List.of("回答用户诉求：" + brief(userMessage)),
                List.of("检索相关知识库", "组织有据可依的回答"),
                List.of("回答基于知识库命中内容、无编造"),
                null);
    }

    private AgentPlan persist(AgentPlan plan) {
        AgentPlanEntity e = new AgentPlanEntity();
        e.setRunId(plan.runId());
        e.setRoundNo(plan.roundNo());
        e.setGoals(writeJson(plan.goals()));
        e.setSubgoals(writeJson(plan.subgoals()));
        e.setSuccessCriteria(writeJson(plan.successCriteria()));
        e.setRevisedFrom(plan.revisedFrom());
        e.setSanityStatus("PASSED");
        agentPlanMapper.insert(e);
        return plan.withPlanId(e.getId());
    }

    private List<String> capped(List<String> list, int max) {
        return list.size() <= max ? list : list.subList(0, max);
    }

    private String brief(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 40 ? s : s.substring(0, 40);
    }

    private String writeJson(List<String> list) {
        try {
            return objectMapper.writeValueAsString(list == null ? List.of() : list);
        } catch (Exception e) {
            return "[]";
        }
    }
}
