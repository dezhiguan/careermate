package com.careermate.agent.runtime;

import com.careermate.agent.AgentPromptAssembler;
import com.careermate.agent.tool.AgentToolResult;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.mapper.UserMapper;
import com.careermate.mapper.UserProfileMapper;
import com.careermate.profile.dto.CareerProfileUpsertRequest;
import com.careermate.profile.service.CareerProfileService;
import com.careermate.resume.dto.ResumeCreateRequest;
import com.careermate.resume.service.ResumeService;
import com.careermate.security.CurrentUser;
import com.careermate.security.CurrentUserContext;
import com.careermate.testsupport.TestUserSupport;
import com.careermate.testsupport.TestUsers;
import com.careermate.workspace.support.WorkspaceSessionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class AgentKernelServiceTest {

    @Autowired
    private AgentKernelService agentKernelService;

    @Autowired
    private ResumeService resumeService;

    @Autowired
    private CareerProfileService careerProfileService;

    @Autowired
    private WorkspaceSessionRepository workspaceSessionRepository;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserProfileMapper userProfileMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        TestUserSupport.ensureTestUsers(userMapper, userProfileMapper, passwordEncoder);
        loginAs(TestUsers.USER_A);
    }

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    @Test
    void prepareRunBuildsChatRequest() {
        AgentRunResult result = agentKernelService.prepareRun(AgentRunRequest.builder()
                .userId(TestUsers.USER_A)
                .sessionId("kernel-test-session")
                .userMessage("请帮我分析当前求职进展")
                .build());

        ChatRequest chatRequest = result.getChatRequest();
        assertNotNull(chatRequest);
        assertEquals(2, chatRequest.getMessages().size());
        assertEquals("system", chatRequest.getMessages().get(0).getRole());
        assertEquals("user", chatRequest.getMessages().get(1).getRole());
        assertEquals("请帮我分析当前求职进展", chatRequest.getMessages().get(1).getContent());
        assertTrue(result.getEvents().stream().anyMatch(e -> AgentKernelEventTypes.PLAN.equals(e.getType())));
    }

    @Test
    void systemPromptContainsLoadedContexts() {
        ResumeCreateRequest resumeRequest = new ResumeCreateRequest();
        resumeRequest.setTitle("Kernel 测试简历");
        resumeRequest.setContent("# Kernel\n- Java\n- Spring Boot");
        resumeService.createResume(resumeRequest);

        AgentRunResult result = agentKernelService.prepareRun(AgentRunRequest.builder()
                .userId(TestUsers.USER_A)
                .sessionId("kernel-context-session")
                .userMessage("请结合我的默认简历分析求职方向")
                .build());

        String prompt = result.getSystemPrompt();
        assertTrue(prompt.contains("CareerMate") || prompt.contains("小职"));
        assertTrue(result.getEvents().stream().anyMatch(e -> traceNamed(e, AgentKernelService.TRACE_RESUME_CONTEXT)));
        assertTrue(result.getEvents().stream().anyMatch(e -> traceNamed(e, AgentKernelService.TRACE_CONVERSATION_CONTEXT)));
    }

    @Test
    void toolResultAppendedWhenRouted() {
        AgentRunResult result = agentKernelService.prepareRun(AgentRunRequest.builder()
                .userId(TestUsers.USER_A)
                .sessionId("kernel-tool-session")
                .userMessage("请帮我查看当前求职进展和看板统计数据详情")
                .build());

        assertFalse(result.getToolResults().isEmpty());
        AgentToolResult toolResult = result.getToolResults().get(0);
        assertEquals("get_dashboard_overview", toolResult.getToolName());
        assertTrue(result.getSystemPrompt().contains("工具调用结果"));
        assertTrue(result.getEvents().stream().anyMatch(e -> AgentKernelEventTypes.TOOL_START.equals(e.getType())));
        assertTrue(result.getEvents().stream().anyMatch(e -> AgentKernelEventTypes.TOOL_RESULT.equals(e.getType())));
        assertTrue(result.getEvents().stream().anyMatch(e -> traceNamed(e, "get_dashboard_overview")));
    }

    @Test
    void reactTraceCanAppendToPrompt() {
        AgentRunResult result = agentKernelService.prepareRun(AgentRunRequest.builder()
                .userId(TestUsers.USER_A)
                .sessionId("kernel-react-session")
                .userMessage("请帮我详细分析岗位匹配差距和技能缺口")
                .build());

        if (result.getReactTrace() != null && result.getReactTrace().hasSteps()) {
            assertTrue(result.getSystemPrompt().contains("工具执行摘要")
                    || result.getSystemPrompt().contains(AgentPromptAssembler.buildBaseSystemPrompt()));
        }
    }

    @Test
    void workspaceContextAppendedToPrompt() {
        var wsSession = workspaceSessionRepository.createJdPrepSession(
                TestUsers.USER_A,
                "kernel-jd-1",
                "{\"company\":\"腾讯\",\"title\":\"Java 后端\"}",
                "腾讯 Java 后端"
        );

        AgentRunResult result = agentKernelService.prepareRun(AgentRunRequest.builder()
                .userId(TestUsers.USER_A)
                .sessionId(wsSession.getSessionId())
                .userMessage("请根据当前 JD 空间帮我生成定制简历")
                .build());

        assertTrue(result.getSystemPrompt().contains("JD 准备空间")
                || result.getSystemPrompt().contains("kernel-jd-1"));
        assertEquals("JD_PREP", result.getDebugMetadata().get("workspaceType"));
    }

    @Test
    void preservesUserIdAndSessionIdOwnership() {
        AgentRunResult result = agentKernelService.prepareRun(AgentRunRequest.builder()
                .userId(TestUsers.USER_A)
                .sessionId("kernel-ownership-session")
                .userMessage("请帮我查看当前求职进展和看板统计数据详情")
                .build());

        assertEquals(TestUsers.USER_A, result.getDebugMetadata().get("userId"));
        assertEquals("kernel-ownership-session", result.getDebugMetadata().get("sessionId"));
    }

    @Test
    void systemPromptContainsCareerProfileAndMemoryTrace() {
        CareerProfileUpsertRequest request = new CareerProfileUpsertRequest();
        request.setTargetRole("Java 后端开发工程师");
        request.setTargetCity("广州");
        request.setSkillKeywords(List.of("Java", "Spring"));
        careerProfileService.upsertProfile(TestUsers.USER_A, request, "manual");

        AgentRunResult result = agentKernelService.prepareRun(AgentRunRequest.builder()
                .userId(TestUsers.USER_A)
                .sessionId("kernel-memory-session")
                .userMessage("请结合我的目标岗位给建议")
                .build());

        assertTrue(result.getSystemPrompt().contains("目标岗位：Java 后端开发工程师"));
        assertTrue(result.getEvents().stream().anyMatch(e -> traceNamed(e, AgentKernelService.TRACE_MEMORY_CONTEXT_LOADED)));
    }

    private boolean traceNamed(AgentEvent event, String traceName) {
        if (!AgentKernelEventTypes.TRACE.equals(event.getType())) {
            return false;
        }
        return traceName.equals(String.valueOf(event.getPayload().get("traceName")));
    }

    private void loginAs(long userId) {
        CurrentUserContext.set(CurrentUser.builder()
                .userId(userId)
                .username(TestUsers.USER_A_NAME)
                .role("USER")
                .authenticated(true)
                .build());
    }
}
