package com.careermate.mcp;

import com.careermate.mapper.CareerTaskMapper;
import com.careermate.mapper.UserMapper;
import com.careermate.mapper.UserProfileMapper;
import com.careermate.model.entity.CareerTaskEntity;
import com.careermate.resume.dto.ResumeCreateRequest;
import com.careermate.resume.service.ResumeService;
import com.careermate.resume.version.service.ResumeVersionService;
import com.careermate.security.CurrentUser;
import com.careermate.security.CurrentUserContext;
import com.careermate.task.CareerTaskConstants;
import com.careermate.testsupport.TestUserSupport;
import com.careermate.testsupport.TestUsers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "careermate.mcp.enabled=true")
class McpControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ResumeVersionService resumeVersionService;

    @Autowired
    private ResumeService resumeService;

    @Autowired
    private CareerTaskMapper careerTaskMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserProfileMapper userProfileMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String userBVersionId;
    private long userBResumeId;

    @BeforeEach
    void setUp() {
        TestUserSupport.ensureTestUsers(userMapper, userProfileMapper, passwordEncoder);

        loginAs(TestUsers.USER_B);
        ResumeCreateRequest resumeRequest = new ResumeCreateRequest();
        resumeRequest.setTitle("MCP User B Resume");
        resumeRequest.setContent("# User B Base Resume");
        userBResumeId = resumeService.createResume(resumeRequest).getId();

        loginAs(TestUsers.USER_A);
        var saved = resumeVersionService.createVersion(
                TestUsers.USER_B,
                "WS-mcp-b",
                userBResumeId,
                "doc-mcp-b",
                "MCP B JD",
                "MCP User B Version",
                "# User B Resume",
                List.of()
        );
        userBVersionId = saved.versionId();
    }

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    @Test
    void toolsListContainsRequiredTools() throws Exception {
        mockMvc.perform(post("/api/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rpc("tools/list", objectMapper.createObjectNode())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.tools[?(@.name=='search_jd')]").exists())
                .andExpect(jsonPath("$.result.tools[?(@.name=='get_resume')]").exists())
                .andExpect(jsonPath("$.result.tools[?(@.name=='create_task')]").exists())
                .andExpect(jsonPath("$.result.tools[?(@.name=='search_jd')].inputSchema.type").value("object"));
    }

    @Test
    void resourcesListContainsRequiredResources() throws Exception {
        mockMvc.perform(post("/api/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rpc("resources/list", objectMapper.createObjectNode())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.resources[?(@.uri=='careermate://resume/versions')]").exists())
                .andExpect(jsonPath("$.result.resources[?(@.uri=='careermate://workspace/jd')]").exists())
                .andExpect(jsonPath("$.result.resources[?(@.uri=='careermate://tasks/todo')]").exists());
    }

    @Test
    void resourcesReadReturnsCurrentUserTodoTasks() throws Exception {
        createTaskViaMcp("MCP Todo Task", null);

        ObjectNode params = objectMapper.createObjectNode();
        params.put("uri", McpConstants.URI_TASKS_TODO);

        String response = mockMvc.perform(post("/api/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rpc("resources/read", params)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(response);
        String text = root.path("result").path("contents").get(0).path("text").asText();
        assertTrue(text.contains("MCP Todo Task"));
        assertFalse(text.contains("password"));
    }

    @Test
    void getResumeCannotReadOtherUsersVersion() throws Exception {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", McpConstants.TOOL_GET_RESUME);
        ObjectNode arguments = params.putObject("arguments");
        arguments.put("versionId", userBVersionId);

        mockMvc.perform(post("/api/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rpc("tools/call", params)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.isError").value(true))
                .andExpect(jsonPath("$.result.content[0].text", containsString("forbidden")));
    }

    @Test
    void getResumeCannotReadOtherUsersResumeId() throws Exception {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", McpConstants.TOOL_GET_RESUME);
        ObjectNode arguments = params.putObject("arguments");
        arguments.put("resumeId", userBResumeId);

        mockMvc.perform(post("/api/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rpc("tools/call", params)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.isError").value(true))
                .andExpect(jsonPath("$.result.content[0].text", containsString("forbidden")));
    }

    @Test
    void createTaskRejectsInvalidDueDate() throws Exception {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", McpConstants.TOOL_CREATE_TASK);
        ObjectNode arguments = params.putObject("arguments");
        arguments.put("title", "bad due date task");
        arguments.put("dueDate", "2026-99-99");

        mockMvc.perform(post("/api/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rpc("tools/call", params)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(McpConstants.ERROR_INVALID_PARAMS));
    }

    @Test
    void createTaskRejectsNonStringTitle() throws Exception {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", McpConstants.TOOL_CREATE_TASK);
        ObjectNode arguments = params.putObject("arguments");
        arguments.set("title", objectMapper.createObjectNode());

        mockMvc.perform(post("/api/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rpc("tools/call", params)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(McpConstants.ERROR_INVALID_PARAMS));
    }

    @Test
    void createTaskUsesCurrentLoggedInUser() throws Exception {
        long taskId = createTaskViaMcp("MCP Created Task", Map.of("category", CareerTaskConstants.CATEGORY_GENERAL));

        CareerTaskEntity entity = careerTaskMapper.selectById(taskId);
        assertEquals(TestUsers.USER_A, entity.getUserId());
        assertEquals("MCP Created Task", entity.getTitle());
        assertEquals(CareerTaskConstants.SOURCE_AGENT, entity.getSource());
    }

    @Test
    void argumentsWithUserIdAreRejected() throws Exception {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", McpConstants.TOOL_CREATE_TASK);
        ObjectNode arguments = params.putObject("arguments");
        arguments.put("title", "bad task");
        arguments.put("userId", TestUsers.USER_B);

        mockMvc.perform(post("/api/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rpc("tools/call", params)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(McpConstants.ERROR_INVALID_PARAMS));
    }

    @Test
    void unauthenticatedRequestReturnsError() throws Exception {
        CurrentUserContext.clear();

        mockMvc.perform(post("/api/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rpc("tools/list", objectMapper.createObjectNode())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value(McpConstants.ERROR_UNAUTHORIZED));
    }

    @Test
    void unknownMethodReturnsMethodNotFound() throws Exception {
        mockMvc.perform(post("/api/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rpc("unknown/method", objectMapper.createObjectNode())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error.code").value(McpConstants.ERROR_METHOD_NOT_FOUND));
    }

    private long createTaskViaMcp(String title, Map<String, String> extraArgs) throws Exception {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", McpConstants.TOOL_CREATE_TASK);
        ObjectNode arguments = params.putObject("arguments");
        arguments.put("title", title);
        if (extraArgs != null) {
            extraArgs.forEach(arguments::put);
        }

        String response = mockMvc.perform(post("/api/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rpc("tools/call", params)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.isError").value(false))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode textNode = objectMapper.readTree(response)
                .path("result").path("content").get(0).path("text");
        JsonNode payload = objectMapper.readTree(textNode.asText());
        return payload.path("taskId").asLong();
    }

    private String rpc(String method, ObjectNode params) throws Exception {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("jsonrpc", McpConstants.JSONRPC_VERSION);
        request.put("id", 1);
        request.put("method", method);
        request.set("params", params);
        return objectMapper.writeValueAsString(request);
    }

    private void loginAs(long userId) {
        CurrentUserContext.set(CurrentUser.builder()
                .userId(userId)
                .username(userId == TestUsers.USER_A ? TestUsers.USER_A_NAME : TestUsers.USER_B_NAME)
                .role("USER")
                .authenticated(true)
                .build());
    }
}
