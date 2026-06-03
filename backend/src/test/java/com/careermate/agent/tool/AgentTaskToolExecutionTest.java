package com.careermate.agent.tool;

import com.careermate.mapper.CareerTaskMapper;
import com.careermate.mapper.InterviewQuestionMapper;
import com.careermate.mapper.InterviewSessionMapper;
import com.careermate.mapper.JobMatchMapper;
import com.careermate.mapper.ResumeMapper;
import com.careermate.mapper.UserMapper;
import com.careermate.mapper.UserProfileMapper;
import com.careermate.model.entity.CareerTaskEntity;
import com.careermate.security.CurrentUser;
import com.careermate.security.CurrentUserContext;
import com.careermate.testsupport.TestUserSupport;
import com.careermate.testsupport.TestUsers;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class AgentTaskToolExecutionTest {

    @Autowired
    private AgentToolExecutionService agentToolExecutionService;

    @Autowired
    private CareerTaskMapper careerTaskMapper;

    @Autowired
    private ResumeMapper resumeMapper;

    @Autowired
    private JobMatchMapper jobMatchMapper;

    @Autowired
    private InterviewSessionMapper interviewSessionMapper;

    @Autowired
    private InterviewQuestionMapper interviewQuestionMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserProfileMapper userProfileMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        TestUserSupport.ensureTestUsers(userMapper, userProfileMapper, passwordEncoder);
        cleanupTasks();
        TestUserSupport.cleanupUserBusinessData(
                resumeMapper,
                jobMatchMapper,
                interviewSessionMapper,
                interviewQuestionMapper
        );
    }

    @AfterEach
    void tearDown() {
        cleanupTasks();
        CurrentUserContext.clear();
    }

    @Test
    void getCareerTasksReturnsOnlyCurrentUserTodo() {
        createTodoTask(TestUsers.USER_A, "用户A待办");
        createTodoTask(TestUsers.USER_B, "用户B待办");

        AgentToolResult result = executeForUser(
                TestUsers.USER_A,
                "我还有哪些任务",
                "get_career_tasks",
                Map.of()
        );
        assertTrue(result.isSuccess());
        assertEquals("当前有 1 条未完成任务", result.getSummary());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tasks = (List<Map<String, Object>>) result.getData().get("tasks");
        assertEquals(1, tasks.size());
        assertEquals("用户A待办", tasks.get(0).get("title"));
    }

    @Test
    void createCareerTaskUsesAgentDefaults() {
        AgentToolResult result = executeForUser(
                TestUsers.USER_A,
                "帮我创建一个任务：补充 Java 后端项目指标",
                "create_career_task",
                Map.of("title", "补充 Java 后端项目指标")
        );
        assertTrue(result.isSuccess());
        assertEquals("已创建任务：补充 Java 后端项目指标", result.getSummary());

        CareerTaskEntity entity = careerTaskMapper.selectList(
                new LambdaQueryWrapper<CareerTaskEntity>()
                        .eq(CareerTaskEntity::getUserId, TestUsers.USER_A)
                        .isNull(CareerTaskEntity::getDeletedAt)
        ).get(0);
        assertEquals("agent", entity.getSource());
        assertEquals("GENERAL", entity.getCategory());
        assertEquals("MEDIUM", entity.getPriority());
    }

    @Test
    void markDoneByKeywordDoesNotThrow() {
        createTodoTask(TestUsers.USER_A, "补充 Java 后端项目指标");

        AgentToolResult ok = executeForUser(
                TestUsers.USER_A,
                "补充 Java 后端项目指标已经做完了",
                "mark_career_task_done",
                Map.of("titleKeyword", "补充 Java 后端项目指标")
        );
        assertTrue(ok.isSuccess());
        assertEquals("已完成任务：补充 Java 后端项目指标", ok.getSummary());

        AgentToolResult miss = executeForUser(
                TestUsers.USER_A,
                "不存在任务已经做完了",
                "mark_career_task_done",
                Map.of("titleKeyword", "不存在任务")
        );
        assertFalse(miss.isSuccess());
        assertTrue(miss.getSummary().contains("未找到"));
    }

    @Test
    void markDoneCannotCompleteOtherUserTask() {
        createTodoTask(TestUsers.USER_B, "用户B任务");

        AgentToolResult result = executeForUser(
                TestUsers.USER_A,
                "用户B任务已经做完了",
                "mark_career_task_done",
                Map.of("titleKeyword", "用户B任务")
        );
        assertFalse(result.isSuccess());
    }

    private AgentToolResult executeForUser(long userId, String message, String toolName, Map<String, Object> args) {
        CurrentUserContext.set(CurrentUser.builder()
                .userId(userId)
                .username("u" + userId)
                .role("USER")
                .authenticated(true)
                .build());
        AgentToolContext context = AgentToolContext.builder()
                .userId(userId)
                .sessionId("s_test")
                .userMessage(message)
                .args(args)
                .build();
        return agentToolExecutionService.execute(context, toolName);
    }

    private void createTodoTask(long userId, String title) {
        CareerTaskEntity entity = new CareerTaskEntity();
        entity.setUserId(userId);
        entity.setTitle(title);
        entity.setCategory("GENERAL");
        entity.setPriority("MEDIUM");
        entity.setStatus("TODO");
        entity.setSource("manual");
        careerTaskMapper.insert(entity);
    }

    private void cleanupTasks() {
        careerTaskMapper.delete(new LambdaQueryWrapper<CareerTaskEntity>()
                .in(CareerTaskEntity::getUserId, List.of(TestUsers.USER_A, TestUsers.USER_B)));
    }
}
