package com.careermate.agent.tool.springai;

import com.careermate.agent.tool.AgentTool;
import com.careermate.agent.tool.AgentToolContext;
import com.careermate.agent.tool.AgentToolDefinition;
import com.careermate.agent.tool.AgentToolDomain;
import com.careermate.agent.tool.AgentToolExecutionService;
import com.careermate.agent.tool.AgentToolPermission;
import com.careermate.agent.tool.AgentToolRegistry;
import com.careermate.agent.tool.AgentToolResult;
import com.careermate.agent.tool.AgentToolRiskLevel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SpringAiToolCallbackFactoryTest {

    @Mock
    private AgentToolRegistry registry;

    @Mock
    private AgentToolExecutionService executionService;

    @Mock
    private AgentTool supportedTool;

    @Mock
    private AgentTool unsupportedTool;

    private SpringAiToolCallbackFactory factory;
    private ObjectMapper objectMapper;
    private AgentToolContext baseContext;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        factory = new SpringAiToolCallbackFactory(registry, executionService, objectMapper);
        baseContext = AgentToolContext.builder()
                .userId(1L)
                .sessionId("S-001")
                .userMessage("帮我读取简历")
                .build();
    }

    @Test
    void createCallbacksFromRegistryDefinitions() throws Exception {
        AgentToolDefinition definition = resumeDefinition("get_default_resume");
        stubSupportedTools(definition);
        stubSupportedTool(definition, supportedTool);
        when(executionService.execute(any(), eq("get_default_resume")))
                .thenReturn(AgentToolResult.success("get_default_resume", "已读取", Map.of()));

        List<ToolCallback> callbacks = factory.createCallbacks(baseContext);

        assertEquals(1, callbacks.size());
        assertEquals("get_default_resume", callbacks.get(0).getToolDefinition().name());
        JsonNode output = objectMapper.readTree(callbacks.get(0).call("{}"));
        assertTrue(output.get("success").asBoolean());
        assertEquals("已读取", output.get("summary").asText());

        ArgumentCaptor<AgentToolContext> contextCaptor = ArgumentCaptor.forClass(AgentToolContext.class);
        verify(executionService).execute(contextCaptor.capture(), eq("get_default_resume"));
        assertEquals(1L, contextCaptor.getValue().getUserId());
        assertEquals("S-001", contextCaptor.getValue().getSessionId());
    }

    @Test
    void callbackReturnsStructuredJsonWithData() throws Exception {
        AgentToolDefinition definition = resumeDefinition("get_default_resume");
        stubSupportedTools(definition);
        stubSupportedTool(definition, supportedTool);
        when(executionService.execute(any(), eq("get_default_resume")))
                .thenReturn(AgentToolResult.success(
                        "get_default_resume",
                        "已读取",
                        Map.of("resumeId", 42L, "title", "我的简历")
                ));

        ToolCallback callback = factory.createCallbacks(baseContext).get(0);
        JsonNode output = objectMapper.readTree(callback.call("{}"));

        assertEquals("get_default_resume", output.get("toolName").asText());
        assertTrue(output.get("success").asBoolean());
        assertEquals("已读取", output.get("summary").asText());
        assertEquals(42L, output.get("data").get("resumeId").asLong());
        assertEquals("我的简历", output.get("data").get("title").asText());
        assertTrue(output.get("errorMessage").isNull());
    }

    @Test
    void callbackRejectsUserIdParameter() {
        AgentToolDefinition definition = resumeDefinition("get_default_resume");
        stubSupportedTools(definition);
        stubSupportedTool(definition, supportedTool);

        ToolCallback callback = factory.createCallbacks(baseContext).get(0);

        assertThrows(IllegalArgumentException.class, () -> callback.call("{\"userId\": 99}"));
    }

    @Test
    void disabledToolsAreExcluded() {
        AgentToolDefinition enabled = resumeDefinition("get_default_resume");
        AgentToolDefinition disabled = AgentToolDefinition.builder()
                .name("hidden_tool")
                .displayName("Hidden")
                .description("disabled")
                .domain(AgentToolDomain.GENERAL)
                .permission(AgentToolPermission.READ_USER_DATA)
                .riskLevel(AgentToolRiskLevel.LOW)
                .enabled(false)
                .build();
        stubSupportedTools(enabled, disabled);
        stubSupportedTool(enabled, supportedTool);

        List<ToolCallback> callbacks = factory.createCallbacks(baseContext);

        assertEquals(1, callbacks.size());
        assertEquals("get_default_resume", callbacks.get(0).getToolDefinition().name());
    }

    @Test
    void unsupportedToolIsExcludedFromCallbacks() {
        AgentToolDefinition supported = resumeDefinition("get_default_resume");
        AgentToolDefinition unsupported = resumeDefinition("get_latest_job_match");
        stubSupportedTools(supported, unsupported);
        stubSupportedTool(supported, supportedTool);
        when(registry.findByName("get_latest_job_match")).thenReturn(Optional.of(unsupportedTool));
        when(unsupportedTool.supports(baseContext)).thenReturn(false);

        List<ToolCallback> callbacks = factory.createCallbacks(baseContext);

        assertEquals(1, callbacks.size());
        assertEquals("get_default_resume", callbacks.get(0).getToolDefinition().name());
    }

    @Test
    void createCallbacksRequiresUserId() {
        AgentToolContext invalid = AgentToolContext.builder().sessionId("S-001").build();
        assertThrows(IllegalArgumentException.class, () -> factory.createCallbacks(invalid));
    }

    private AgentToolDefinition resumeDefinition(String name) {
        return AgentToolDefinition.builder()
                .name(name)
                .displayName("读取默认简历")
                .description("读取用户默认简历")
                .domain(AgentToolDomain.RESUME)
                .permission(AgentToolPermission.READ_USER_DATA)
                .riskLevel(AgentToolRiskLevel.LOW)
                .build();
    }

    private void stubSupportedTool(AgentToolDefinition definition, AgentTool tool) {
        when(registry.findByName(definition.getName())).thenReturn(Optional.of(tool));
        when(tool.supports(baseContext)).thenReturn(true);
    }

    private void stubSupportedTools(AgentToolDefinition... definitions) {
        when(registry.listDefinitions()).thenReturn(List.of(definitions));
    }
}
