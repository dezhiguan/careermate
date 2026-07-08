package com.careermate.agent.reflect;

import com.careermate.llm.LlmClient;
import com.careermate.llm.LlmProperties;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.dto.ChatResponse;
import com.careermate.mapper.AgentPlanMapper;
import com.careermate.mapper.AgentReflectionMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReflectionComponentsTest {

    private final ObjectMapperHolder omh = new ObjectMapperHolder();
    private final LlmClient llm = mock(LlmClient.class);
    private final LlmProperties llmProps = new LlmProperties();
    private final AgentPlanMapper planMapper = mock(AgentPlanMapper.class);
    private final AgentReflectionMapper reflectionMapper = mock(AgentReflectionMapper.class);
    private final ReflectionProperties props = new ReflectionProperties();

    private final AgentPlanner planner = new AgentPlanner(llm, llmProps, omh.mapper, planMapper, props);
    private final AgentReflector reflector = new AgentReflector(llm, omh.mapper, reflectionMapper, props);
    private final AgentRepairer repairer = new AgentRepairer(llm, omh.mapper, planMapper, props);

    private ChatResponse resp(String content) {
        return ChatResponse.builder().content(content).build();
    }

    // ---- Planner ----

    @Test
    void planner_parsesJsonAndCapsGoals() {
        when(llm.chat(any(ChatRequest.class))).thenReturn(resp("""
                这是plan：```json
                {"goals":["g1","g2","g3","g4"],"subgoals":["s1","s2"],"success_criteria":["覆盖JD库≥5项"]}
                ```"""));
        AgentPlan plan = planner.plan("run-1", "针对字节算法岗改简历");
        assertThat(plan.goals()).hasSize(3); // 上限 3
        assertThat(plan.subgoals()).containsExactly("s1", "s2");
        assertThat(plan.successCriteria()).contains("覆盖JD库≥5项");
        verify(planMapper).insert(any(com.careermate.model.entity.AgentPlanEntity.class));
    }

    @Test
    void planner_fallbackWhenLlmGarbage() {
        when(llm.chat(any(ChatRequest.class))).thenReturn(resp("对不起我不会"));
        AgentPlan plan = planner.plan("run-1", "帮我看看");
        assertThat(plan.goals()).isNotEmpty();
        assertThat(plan.successCriteria()).isNotEmpty();
    }

    @Test
    void planner_fallbackWhenLlmThrows() {
        when(llm.chat(any(ChatRequest.class))).thenThrow(new RuntimeException("timeout"));
        AgentPlan plan = planner.plan("run-1", "帮我看看");
        assertThat(plan.goals()).isNotEmpty();
    }

    // ---- Reflector ----

    @Test
    void reflector_parsesNotSatisfied() {
        when(llm.chat(any(ChatRequest.class))).thenReturn(resp(
                "{\"satisfied\":false,\"confidence\":0.4,\"gaps\":[\"缺Spring Cloud\"],\"suggestions\":[\"补检索\"],\"verdict\":\"REVISE\"}"));
        AgentPlan plan = new AgentPlan(1L, "run-1", 0, List.of("g"), List.of("s"), List.of("c"), null);
        Reflection r = reflector.review(plan, "只覆盖了一半");
        assertThat(r.satisfied()).isFalse();
        assertThat(r.gaps()).contains("缺Spring Cloud");
        assertThat(r.verdict()).isEqualTo("REVISE");
        verify(reflectionMapper).insert(any(com.careermate.model.entity.AgentReflectionEntity.class));
    }

    @Test
    void reflector_conservativeAcceptWhenUnparseable() {
        when(llm.chat(any(ChatRequest.class))).thenReturn(resp("无法判断"));
        AgentPlan plan = new AgentPlan(1L, "run-1", 0, List.of("g"), List.of("s"), List.of("c"), null);
        Reflection r = reflector.review(plan, "结果");
        assertThat(r.satisfied()).isTrue();
        assertThat(r.verdict()).isEqualTo("ACCEPT");
    }

    // ---- Repairer ----

    @Test
    void repairer_preservesGoalsAndIncrementsRound() {
        when(llm.chat(any(ChatRequest.class))).thenReturn(resp(
                "{\"goals\":[],\"subgoals\":[\"s1\",\"s2\",\"s3\"],\"success_criteria\":[\"c2\"]}"));
        AgentPlan plan = new AgentPlan(5L, "run-1", 0, List.of("原目标"), List.of("s0"), List.of("c0"), null);
        Reflection r = new Reflection(false, 0.3, List.of("gap"), List.of("补充"), "REVISE");
        AgentPlan revised = repairer.revise(plan, r);
        assertThat(revised.goals()).containsExactly("原目标"); // 空则保留原 goals
        assertThat(revised.subgoals()).containsExactly("s1", "s2", "s3");
        assertThat(revised.roundNo()).isEqualTo(1);
        assertThat(revised.revisedFrom()).isEqualTo(5L);
    }

    @Test
    void repairer_isStuck_whenSameSuggestions() {
        Reflection a = new Reflection(false, 0.3, List.of(), List.of("补检索"), "REVISE");
        Reflection b = new Reflection(false, 0.3, List.of(), List.of("补检索"), "REVISE");
        assertThat(repairer.isStuck(a, b)).isTrue();
    }

    @Test
    void repairer_notStuck_whenSuggestionsDiffer_orEmpty() {
        assertThat(repairer.isStuck(
                new Reflection(false, 0.3, List.of(), List.of("A"), "REVISE"),
                new Reflection(false, 0.3, List.of(), List.of("B"), "REVISE"))).isFalse();
        assertThat(repairer.isStuck(
                new Reflection(false, 0.3, List.of(), List.of(), "REVISE"),
                new Reflection(false, 0.3, List.of(), List.of(), "REVISE"))).isFalse();
        assertThat(repairer.isStuck(null, null)).isFalse();
    }

    /** 复用真实 ObjectMapper，避免 mock 序列化。 */
    static class ObjectMapperHolder {
        final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
    }
}
