package com.careermate.keel;

import com.careermate.agent.tool.*;
import com.careermate.auth.identity.LocalUserMirror;
import com.careermate.model.entity.UserEntity;
import com.careermate.security.CurrentUserContext;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class KeelBusinessControllerTest {
    final AgentToolRegistry registry = mock(AgentToolRegistry.class);
    final AgentToolExecutionService execution = mock(AgentToolExecutionService.class);
    final LocalUserMirror users = mock(LocalUserMirror.class);
    final KeelBusinessController controller = new KeelBusinessController(registry, execution, users, "service-secret");

    @Test void deniesUnauthenticatedServiceBeforeReadingUsers() {
        assertThatThrownBy(() -> controller.execute("Bearer wrong", "get_default_resume", request(Map.of())))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(users, execution);
    }

    @Test void unknownIdentityNeverFallsBackToFirstUser() {
        assertThatThrownBy(() -> controller.execute("Bearer service-secret", "get_default_resume", request(Map.of())))
                .isInstanceOf(ResponseStatusException.class);
        verify(users).findByAuthUserId(42L);
        verifyNoInteractions(execution);
    }

    @Test void usesAuthIdentityMappedOwnerAndClearsContext() {
        var user = new UserEntity(); user.setId(200L); user.setAuthUserId(42L); user.setStatus("ACTIVE");
        when(users.findByAuthUserId(42L)).thenReturn(user);
        when(registry.definitionOf("get_default_resume")).thenReturn(Optional.of(
                AgentToolDefinition.builder().name("get_default_resume").enabled(true).build()));
        when(execution.execute(any(), eq("get_default_resume"))).thenAnswer(call -> {
            AgentToolContext ctx = call.getArgument(0);
            assertThat(ctx.getUserId()).isEqualTo(200L);
            assertThat(CurrentUserContext.getUserId()).isEqualTo(200L);
            return AgentToolResult.success("get_default_resume", "ok", Map.of());
        });
        assertThat(controller.execute("Bearer service-secret", "get_default_resume", request(Map.of())).isSuccess()).isTrue();
        assertThat(CurrentUserContext.get()).isNull();
    }

    @Test void rejectsModelSuppliedOwnerOverride() {
        var user = new UserEntity(); user.setId(200L); user.setStatus("ACTIVE");
        when(users.findByAuthUserId(42L)).thenReturn(user);
        when(registry.definitionOf("get_default_resume")).thenReturn(Optional.of(
                AgentToolDefinition.builder().name("get_default_resume").enabled(true).build()));
        assertThatThrownBy(() -> controller.execute("Bearer service-secret", "get_default_resume", request(Map.of("userId", 1))))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(execution);
    }

    private KeelBusinessController.ToolRequest request(Map<String, Object> args) {
        return new KeelBusinessController.ToolRequest(42L, "session", "query", args);
    }
}
