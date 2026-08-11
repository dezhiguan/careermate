package com.careermate.workspace;

import com.careermate.agent.sse.SseEmitterService;
import com.careermate.resume.version.workflow.GenerateResumeFromJdWorkflow;
import com.careermate.workspace.pending.PendingActionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.careermate.security.CurrentUser;
import com.careermate.security.CurrentUserContext;
import com.careermate.testsupport.TestUsers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
class WorkspaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GenerateResumeFromJdWorkflow generateResumeFromJdWorkflow;

    @MockBean
    private PendingActionService pendingActionService;

    @MockBean(name = "agentExecutor")
    private TaskExecutor agentExecutor;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUpUser() {
        CurrentUserContext.set(CurrentUser.builder()
                .userId(TestUsers.USER_A)
                .username(TestUsers.USER_A_NAME)
                .role("user")
                .authenticated(true)
                .build());
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(agentExecutor).execute(any(Runnable.class));
    }

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    @Test
    void createWorkspaceReturnsWorkspaceId() throws Exception {
        mockMvc.perform(post("/api/workspace")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workspaceType": "MARKET",
                                  "title": "广州 Java后端",
                                  "goalText": "生成谈薪脚本",
                                  "entryAction": "NEGOTIATION_SCRIPT",
                                  "contextMetadata": {"city": "广州", "role": "Java后端"}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.workspaceId").isNotEmpty())
                .andExpect(jsonPath("$.data.redirectPath").value(org.hamcrest.Matchers.startsWith("/chat/WS-")))
                .andExpect(jsonPath("$.data.workspaceType").value("MARKET"));
    }

    @Test
    void createWorkspaceThenGetWorkspaceShouldSucceed() throws Exception {
        var createResult = mockMvc.perform(post("/api/workspace")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workspaceType": "MARKET",
                                  "title": "广州 Java后端",
                                  "goalText": "生成谈薪脚本",
                                  "entryAction": "NEGOTIATION_SCRIPT",
                                  "contextMetadata": {"city": "广州", "role": "Java后端"}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.workspaceId").isNotEmpty())
                .andReturn();

        JsonNode root = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String workspaceId = root.path("data").path("workspaceId").asText();

        mockMvc.perform(get("/api/workspace/{workspaceId}", workspaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.sessionId").value(workspaceId))
                .andExpect(jsonPath("$.data.workspaceType").value("MARKET"));
    }

    @Test
    void createWorkspaceRequiresAuthenticatedUserContext() throws Exception {
        CurrentUserContext.clear();

        mockMvc.perform(post("/api/workspace")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workspaceType": "MARKET",
                                  "title": "test"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void generateResumeStreamWithoutPendingActionIdRejected() throws Exception {
        // SSE 端点必须在协商到的 text/event-stream 里回错：客户端带 Accept: text/event-stream 时，
        // 抛异常走 JSON 异常处理器没有可用的消息转换器，生产上会退化成 500 空响应体，
        // 用户看不到任何原因、线上也没有 traceId。改为 200 + 一条 error 事件承载原因。
        String body = mockMvc.perform(get("/api/workspace/WS-any/generate-resume/stream")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(body).contains("event:error");
        assertThat(body).contains("简历生成需要先确认");

        verify(pendingActionService, never()).validateAndConsumeConfirmed(
                anyLong(), anyString(), anyString(), anyString()
        );
        verify(generateResumeFromJdWorkflow, never()).generate(
                anyLong(), anyString(), anyString(), any(SseEmitterService.class)
        );
    }

    @Test
    void generateResumeStreamWithConfirmedPendingActionStartsWorkflow() throws Exception {
        var createResult = mockMvc.perform(post("/api/workspace")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workspaceType": "JD_PREP",
                                  "title": "测试 JD 空间",
                                  "contextMetadata": {"jdId": "doc-1", "company": "测试公司", "title": "工程师"}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode root = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String workspaceId = root.path("data").path("workspaceId").asText();
        String pendingActionId = "PA-confirmed-test";

        mockMvc.perform(get("/api/workspace/{workspaceId}/generate-resume/stream", workspaceId)
                        .param("pendingActionId", pendingActionId)
                        .param("jdId", "doc-1"))
                .andExpect(status().isOk());

        verify(pendingActionService).validateAndConsumeConfirmed(
                eq(TestUsers.USER_A), eq(workspaceId), eq(pendingActionId), eq("doc-1")
        );
        verify(generateResumeFromJdWorkflow).generate(
                eq(TestUsers.USER_A), eq(workspaceId), eq("doc-1"), any(SseEmitterService.class)
        );
    }
}
