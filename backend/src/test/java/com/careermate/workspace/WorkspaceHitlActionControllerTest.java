package com.careermate.workspace;

import com.careermate.security.CurrentUser;
import com.careermate.security.CurrentUserContext;
import com.careermate.testsupport.TestUsers;
import com.careermate.workspace.pending.PendingActionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
class WorkspaceHitlActionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUpUser() {
        CurrentUserContext.set(CurrentUser.builder()
                .userId(TestUsers.USER_A)
                .username(TestUsers.USER_A_NAME)
                .role("user")
                .authenticated(true)
                .build());
    }

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    @Test
    void generateResumeActionReturnsConfirmActionCard() throws Exception {
        String workspaceId = createJdPrepWorkspace();

        mockMvc.perform(post("/api/workspace/{workspaceId}/action", workspaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"action":"GENERATE_RESUME","payload":"doc-1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.card.type").value("CONFIRM_ACTION"))
                .andExpect(jsonPath("$.data.card.actionId").isNotEmpty());
    }

    @Test
    void confirmPendingActionReturnsSseEndpointWithPendingActionId() throws Exception {
        String workspaceId = createJdPrepWorkspace();
        String actionId = requestGenerateResumeActionId(workspaceId);

        mockMvc.perform(post("/api/workspace/{workspaceId}/action", workspaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"action":"CONFIRM_PENDING_ACTION","payload":"%s"}
                                """.formatted(actionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.noop").value(false))
                .andExpect(jsonPath("$.data.sseEndpoint", containsString("pendingActionId=" + actionId)))
                .andExpect(jsonPath("$.data.sseEndpoint", containsString("jdId=doc-1")));
    }

    @Test
    void cancelPendingActionReturnsCancelledCardAndTrace() throws Exception {
        String workspaceId = createJdPrepWorkspace();
        String actionId = requestGenerateResumeActionId(workspaceId);

        mockMvc.perform(post("/api/workspace/{workspaceId}/action", workspaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"action":"CANCEL_PENDING_ACTION","payload":"%s"}
                                """.formatted(actionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.card.type").value("ACTION_CANCELLED"))
                .andExpect(jsonPath("$.data.card.actionId").value(actionId));

        String traceBody = mockMvc.perform(get("/api/agent/sessions/{sessionId}/trace", workspaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode traces = objectMapper.readTree(traceBody).path("data");
        assertTrue(containsTrace(traces, PendingActionService.TRACE_HITL_PENDING));
        assertTrue(containsTrace(traces, PendingActionService.TRACE_HITL_CANCELLED));
    }

    private String createJdPrepWorkspace() throws Exception {
        var createResult = mockMvc.perform(post("/api/workspace")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workspaceType": "JD_PREP",
                                  "title": "HITL 测试空间",
                                  "contextMetadata": {"jdId": "doc-1", "company": "测试公司", "title": "工程师"}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode root = objectMapper.readTree(createResult.getResponse().getContentAsString());
        return root.path("data").path("workspaceId").asText();
    }

    private String requestGenerateResumeActionId(String workspaceId) throws Exception {
        var result = mockMvc.perform(post("/api/workspace/{workspaceId}/action", workspaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"action":"GENERATE_RESUME","payload":"doc-1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.card.type").value("CONFIRM_ACTION"))
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("data").path("card").path("actionId").asText();
    }

    private static boolean containsTrace(JsonNode traces, String toolName) {
        if (!traces.isArray()) {
            return false;
        }
        for (JsonNode trace : traces) {
            if (toolName.equals(trace.path("toolName").asText())) {
                return true;
            }
        }
        return false;
    }
}
