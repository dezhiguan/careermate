package com.careermate.agent.reflect;

import com.careermate.llm.LlmClient;
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
 * A3：根据 reflection 的差距/建议修订 plan（保留原始 goals，补子目标 ≤8），并检测卡死（连续 2 轮相同建议）。
 */
@Slf4j
@Service
public class AgentRepairer {

    private static final String SYSTEM_PROMPT = """
            你是 Agent plan 修订者。给你原 plan、差距与建议，产出修订后的 plan。
            要求：保留原始 goals；针对差距增补/调整 subgoals（≤8）与 success_criteria；success_criteria 仍需知识库锚定。
            只输出 JSON：{"goals":["..."],"subgoals":["..."],"success_criteria":["..."]}
            """;

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final AgentPlanMapper agentPlanMapper;
    private final ReflectionProperties reflectionProperties;

    public AgentRepairer(LlmClient llmClient, ObjectMapper objectMapper,
                         AgentPlanMapper agentPlanMapper, ReflectionProperties reflectionProperties) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.agentPlanMapper = agentPlanMapper;
        this.reflectionProperties = reflectionProperties;
    }

    public AgentPlan revise(AgentPlan plan, Reflection reflection) {
        AgentPlan revised = callAndParse(plan, reflection);
        return persist(revised);
    }

    /** 卡死检测：连续两轮 reflection 的 suggestions 完全相同。 */
    public boolean isStuck(Reflection previous, Reflection current) {
        if (previous == null || current == null) {
            return false;
        }
        return previous.suggestions().equals(current.suggestions()) && !current.suggestions().isEmpty();
    }

    private AgentPlan callAndParse(AgentPlan plan, Reflection reflection) {
        try {
            String user = "原 plan goals：" + plan.goals()
                    + "\n原 subgoals：" + plan.subgoals()
                    + "\n差距：" + reflection.gaps()
                    + "\n建议：" + reflection.suggestions();
            ChatResponse resp = llmClient.chat(ChatRequest.builder()
                    .temperature(reflectionProperties.getPlannerTemperature())
                    .messages(List.of(
                            ChatMessage.builder().role("system").content(SYSTEM_PROMPT).build(),
                            ChatMessage.builder().role("user").content(user).build()))
                    .build());
            JsonNode json = ReflectionJsonSupport.extractJson(objectMapper, resp == null ? null : resp.getContent());
            List<String> goals = ReflectionJsonSupport.stringList(json, "goals");
            List<String> subgoals = ReflectionJsonSupport.stringList(json, "subgoals");
            List<String> criteria = ReflectionJsonSupport.stringList(json, "success_criteria");
            // 保留原始 goals，避免修订丢目标
            if (goals.isEmpty()) {
                goals = plan.goals();
            }
            if (subgoals.isEmpty()) {
                subgoals = plan.subgoals();
            }
            if (criteria.isEmpty()) {
                criteria = plan.successCriteria();
            }
            return new AgentPlan(null, plan.runId(), plan.roundNo() + 1,
                    goals, capped(subgoals, 8), criteria, plan.planId());
        } catch (Exception e) {
            log.warn("plan 修订失败，沿用原 plan（不影响对话）: {}", e.getMessage());
            return new AgentPlan(null, plan.runId(), plan.roundNo() + 1,
                    plan.goals(), plan.subgoals(), plan.successCriteria(), plan.planId());
        }
    }

    private AgentPlan persist(AgentPlan plan) {
        AgentPlanEntity e = new AgentPlanEntity();
        e.setRunId(plan.runId());
        e.setRoundNo(plan.roundNo());
        e.setGoals(writeJson(plan.goals()));
        e.setSubgoals(writeJson(plan.subgoals()));
        e.setSuccessCriteria(writeJson(plan.successCriteria()));
        e.setRevisedFrom(plan.revisedFrom());
        e.setSanityStatus("RETRIED");
        agentPlanMapper.insert(e);
        return plan.withPlanId(e.getId());
    }

    private List<String> capped(List<String> list, int max) {
        return list.size() <= max ? list : list.subList(0, max);
    }

    private String writeJson(List<String> list) {
        try {
            return objectMapper.writeValueAsString(list == null ? List.of() : list);
        } catch (Exception e) {
            return "[]";
        }
    }
}
