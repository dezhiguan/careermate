package com.careermate.agent.tool;

import com.careermate.agent.path.AgentPathMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentToolContextPathModeTest {

    @Test
    void defaultsToFast() {
        AgentToolContext ctx = AgentToolContext.builder().userId(7L).build();
        assertThat(ctx.getPathMode()).isEqualTo(AgentPathMode.FAST);
        assertThat(ctx.isDeep()).isFalse();
    }

    @Test
    void deepPathModeReflectedByIsDeep() {
        AgentToolContext ctx = AgentToolContext.builder()
                .userId(7L)
                .pathMode(AgentPathMode.DEEP)
                .build();
        assertThat(ctx.isDeep()).isTrue();
    }
}
