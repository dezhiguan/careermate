package com.careermate.agent.session.impl;

import com.careermate.mapper.AgentMessageMapper;
import com.careermate.mapper.AgentSessionMapper;
import com.careermate.mapper.AgentTaskStateMapper;
import com.careermate.mapper.AgentToolCallMapper;
import com.careermate.agent.sse.AgentTaskRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentSessionServiceImplPathModeTest {

    private final AgentSessionMapper sessionMapper = mock(AgentSessionMapper.class);
    private final AgentSessionServiceImpl service = new AgentSessionServiceImpl(
            sessionMapper,
            mock(AgentMessageMapper.class),
            mock(AgentToolCallMapper.class),
            mock(AgentTaskStateMapper.class),
            mock(AgentTaskRegistry.class)
    );

    @Test
    void normalize_deep_caseInsensitive() {
        assertThat(AgentSessionServiceImpl.normalizePathMode("DEEP")).isEqualTo("DEEP");
        assertThat(AgentSessionServiceImpl.normalizePathMode("deep")).isEqualTo("DEEP");
    }

    @Test
    void normalize_fastAndUnknownAndNull_defaultFast() {
        assertThat(AgentSessionServiceImpl.normalizePathMode("FAST")).isEqualTo("FAST");
        assertThat(AgentSessionServiceImpl.normalizePathMode("weird")).isEqualTo("FAST");
        assertThat(AgentSessionServiceImpl.normalizePathMode(null)).isEqualTo("FAST");
        assertThat(AgentSessionServiceImpl.normalizePathMode("")).isEqualTo("FAST");
    }

    @Test
    void recordPathMode_missingSession_swallowsErrorAndDoesNotThrow() {
        // 会话查不到时 getSessionByUser 抛 404，recordPathMode 应吞掉（非关键路径），不影响对话
        when(sessionMapper.selectOne(any())).thenReturn(null);
        assertThatCode(() -> service.recordPathMode(7L, "missing", "FAST"))
                .doesNotThrowAnyException();
    }
}
