package com.careermate.agent.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentToolRouterTest {

    private AgentToolRouter router;

    @BeforeEach
    void setUp() {
        router = new AgentToolRouter();
    }

    @Test
    void routesAnalyzeResumeToGetDefaultResume() {
        Optional<AgentToolRouter.RoutedTool> routed = router.route("帮我分析简历");
        assertTrue(routed.isPresent());
        assertEquals("get_default_resume", routed.get().toolName());
    }

    @Test
    void routesDefaultResumePhrase() {
        Optional<AgentToolRouter.RoutedTool> routed = router.route("帮我分析默认简历");
        assertTrue(routed.isPresent());
        assertEquals("get_default_resume", routed.get().toolName());
    }

    @Test
    void routesJobGapToGetLatestJobMatch() {
        Optional<AgentToolRouter.RoutedTool> routed = router.route("我和最近岗位差距在哪里");
        assertTrue(routed.isPresent());
        assertEquals("get_latest_job_match", routed.get().toolName());
    }

    @Test
    void routesLongJdToCreateJobMatch() {
        String jd = "岗位：Java 后端工程师\n公司：e2e_company\n招聘要求："
                + "Java Spring Boot Redis Docker Elasticsearch Kubernetes MySQL PostgreSQL ";
        Optional<AgentToolRouter.RoutedTool> routed = router.route(jd);
        assertTrue(routed.isPresent());
        assertEquals("create_job_match", routed.get().toolName());
        assertEquals("Java 后端工程师", routed.get().args().get("jobTitle"));
        assertEquals("e2e_company", routed.get().args().get("companyName"));
    }

    @Test
    void routesPrepareInterviewToCreateInterviewSession() {
        Optional<AgentToolRouter.RoutedTool> routed = router.route("帮我准备面试");
        assertTrue(routed.isPresent());
        assertEquals("create_interview_session", routed.get().toolName());
    }

    @Test
    void routesDashboardProgressToGetDashboardOverview() {
        Optional<AgentToolRouter.RoutedTool> routed = router.route("看一下求职进展");
        assertTrue(routed.isPresent());
        assertEquals("get_dashboard_overview", routed.get().toolName());
    }

    @Test
    void routesCasualChatToNoTool() {
        assertTrue(router.route("今天天气不错").isEmpty());
        assertTrue(router.route("你好").isEmpty());
    }

    @Test
    void routesInterviewQuestionToRagRetriever() {
        Optional<AgentToolRouter.RoutedTool> routed =
                router.route("帮我查一下 Redis 缓存一致性面试题");
        assertTrue(routed.isPresent());
        assertEquals("rag_retriever", routed.get().toolName());
        assertEquals("INTERVIEW", routed.get().args().get("scene"));
    }

    @Test
    void routesMarketQuestionToRagRetriever() {
        Optional<AgentToolRouter.RoutedTool> routed =
                router.route("广州 Java 后端行情怎么样");
        assertTrue(routed.isPresent());
        assertEquals("rag_retriever", routed.get().toolName());
        assertEquals("MARKET", routed.get().args().get("scene"));
    }

    @Test
    void routesJdCapabilityQuestionToRagRetriever() {
        Optional<AgentToolRouter.RoutedTool> routed =
                router.route("这份 JD 要求里哪些能力最关键");
        assertTrue(routed.isPresent());
        assertEquals("rag_retriever", routed.get().toolName());
        assertEquals("OPPORTUNITY", routed.get().args().get("scene"));
    }

    @Test
    void routesCompanyQuestionToRagRetriever() {
        Optional<AgentToolRouter.RoutedTool> routed =
                router.route("腾讯公司技术栈怎么样");
        assertTrue(routed.isPresent());
        assertEquals("rag_retriever", routed.get().toolName());
        assertEquals("COMPANY", routed.get().args().get("scene"));
    }

    @Test
    void shortJdDoesNotRouteCreateJobMatch() {
        String shortJd = "岗位：Java 后端工程师\n公司：e2e_company\n招聘要求：Java Spring Boot";
        assertTrue(router.route(shortJd).isEmpty());
    }

   
}
