package com.careermate.keel;

import com.careermate.agent.tool.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class KeelAuditGateTest {
    @Test void auditFailurePreventsBusinessSideEffect() {
        var tool = mock(AgentTool.class);
        when(tool.name()).thenReturn("create_career_task");
        when(tool.definition()).thenReturn(AgentToolDefinition.builder().name("create_career_task").build());
        when(tool.supports(any())).thenReturn(true);
        var runtime = mock(KeelRuntimeClient.class);
        when(runtime.post(eq("audit"), any())).thenThrow(new IllegalStateException("audit down"));
        var execution = new AgentToolExecutionService(new AgentToolRegistry(List.of(tool)), new AgentToolArgumentValidator());
        execution.setRuntime(runtime);
        var result = execution.execute(AgentToolContext.builder().userId(42L).build(), "create_career_task");
        assertThat(result.isSuccess()).isFalse();
        verify(tool, never()).execute(any());
    }
}
