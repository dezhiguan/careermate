package com.careermate.agent.path;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentPathRouterTest {

    private final AgentPathRouter router = new AgentPathRouter();

    @Test
    void explicitDeepFlag_wins() {
        AgentPathDecision d = router.decide("今天天气怎么样", true);
        assertThat(d.isDeep()).isTrue();
        assertThat(d.reason()).isEqualTo("user_explicit");
    }

    @Test
    void plainQuery_isFast() {
        AgentPathDecision d = router.decide("帮我看看 Java 岗位薪资", false);
        assertThat(d.mode()).isEqualTo(AgentPathMode.FAST);
        assertThat(d.reason()).isEqualTo("default_fast");
    }

    @Test
    void blankMessage_isFast() {
        assertThat(router.decide("  ", false).mode()).isEqualTo(AgentPathMode.FAST);
        assertThat(router.decide(null, false).mode()).isEqualTo(AgentPathMode.FAST);
    }

    @Test
    void resumeCustomizeKeyword_isDeep() {
        AgentPathDecision d = router.decide("帮我针对字节算法岗定制简历", false);
        assertThat(d.isDeep()).isTrue();
        assertThat(d.reason()).isEqualTo("deep_resume");
    }

    @Test
    void interviewSimulationKeyword_isDeep() {
        AgentPathDecision d = router.decide("给我来一场模拟面试", false);
        assertThat(d.isDeep()).isTrue();
        assertThat(d.reason()).isEqualTo("deep_interview");
    }
}
