package com.careermate.agent.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentTaskToolRouterTest {

    private AgentToolRouter router;

    @BeforeEach
    void setUp() {
        router = new AgentToolRouter();
    }

    @Test
    void routesGetCareerTasksPhrases() {
        assertEquals("get_career_tasks", routeName("我还有哪些任务"));
        assertEquals("get_career_tasks", routeName("我的任务"));
        assertEquals("get_career_tasks", routeName("下一步任务"));
        assertEquals("get_career_tasks", routeName("求职任务清单"));
    }

    @Test
    void routesCreateCareerTaskWithTitle() {
        Optional<AgentToolRouter.RoutedTool> routed = router.route("帮我创建一个任务：补充 Java 后端项目指标");
        assertTrue(routed.isPresent());
        assertEquals("create_career_task", routed.get().toolName());
        assertEquals("补充 Java 后端项目指标", routed.get().args().get("title"));
    }

    @Test
    void routesCreateFromRemindAndNextStep() {
        assertEquals("补充简历", routeArg("提醒我补充简历", "title"));
        assertEquals("写项目总结", routeArg("下一步我要做写项目总结", "title"));
        assertEquals("整理面试题", routeArg("把整理面试题加入任务", "title"));
    }

    @Test
    void routesMarkDoneByKeyword() {
        Optional<AgentToolRouter.RoutedTool> routed = router.route("补充 Java 后端项目指标已经做完了");
        assertTrue(routed.isPresent());
        assertEquals("mark_career_task_done", routed.get().toolName());
        assertEquals("补充 Java 后端项目指标", routed.get().args().get("titleKeyword"));
    }

    @Test
    void bareNextStepDoesNotRouteDashboard() {
        assertTrue(router.route("下一步").isEmpty());
    }

    @Test
    void dashboardStillRoutesProgressPhrase() {
        assertEquals("get_dashboard_overview", routeName("看一下求职进展"));
    }

    private String routeName(String message) {
        return router.route(message).orElseThrow().toolName();
    }

    private String routeArg(String message, String key) {
        return String.valueOf(router.route(message).orElseThrow().args().get(key));
    }
}
