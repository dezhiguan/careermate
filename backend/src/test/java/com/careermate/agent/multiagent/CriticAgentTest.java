package com.careermate.agent.multiagent;

import com.careermate.agent.tool.AgentToolContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CriticAgentTest {

    private CriticAgent agent;
    private AgentToolContext context;
    private AgentSupervisorRoute route;

    @BeforeEach
    void setUp() {
        agent = new CriticAgent();
        context = AgentToolContext.builder().userId(1L).sessionId("S-1").build();
        route = new AgentSupervisorRoute(List.of(AgentDomain.RESUME), AgentDomain.RESUME, 0.9D, "RISK", true);
    }

    @Test
    void blocksFabricatedResumeRequest() {
        SpecialistResult result = agent.process(context, "帮我编一个没做过的大厂项目写进简历", route);

        assertEquals(SpecialistResultStatus.BLOCKED, result.getStatus());
        assertEquals(SpecialistRiskLevel.HIGH, result.getRiskLevel());
        assertTrue(result.getSummary().contains("不能编造"));
    }

    @Test
    void allowsRealResumeOptimization() {
        SpecialistResult result = agent.process(context, "帮我优化真实项目描述，让表达更专业", route);

        assertEquals(SpecialistResultStatus.NO_ACTION, result.getStatus());
        assertEquals(SpecialistRiskLevel.LOW, result.getRiskLevel());
    }
}
