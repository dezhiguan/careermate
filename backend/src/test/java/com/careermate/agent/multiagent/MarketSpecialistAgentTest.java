package com.careermate.agent.multiagent;

import com.careermate.agent.tool.AgentToolContext;
import com.careermate.agent.tool.AgentToolExecutionService;
import com.careermate.agent.tool.AgentToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketSpecialistAgentTest {

    @Mock
    private AgentToolExecutionService toolExecutionService;

    private MarketSpecialistAgent agent;
    private AgentToolContext context;
    private AgentSupervisorRoute route;

    @BeforeEach
    void setUp() {
        agent = new MarketSpecialistAgent(toolExecutionService);
        context = AgentToolContext.builder().userId(1L).sessionId("S-1").build();
        route = new AgentSupervisorRoute(List.of(AgentDomain.MARKET), AgentDomain.MARKET, 0.8D, "MARKET", false);
    }

    @Test
    void marketQuestionUsesMarketScene() {
        when(toolExecutionService.execute(org.mockito.ArgumentMatchers.any(), eq("rag_retriever")))
                .thenReturn(AgentToolResult.builder()
                        .toolName("rag_retriever")
                        .success(true)
                        .summary("检索到 2 条")
                        .data(Map.of("scene", "MARKET", "chunkCount", 2))
                        .build());

        SpecialistResult result = agent.process(context, "广州 Java 后端行情怎么样", route);

        assertEquals(SpecialistResultStatus.SUCCESS, result.getStatus());
        assertEquals("MARKET", result.getStructuredData().get("scene"));
        assertEquals("rag_retriever", result.toolName());
    }

    @Test
    void companyQuestionUsesCompanyScene() {
        when(toolExecutionService.execute(org.mockito.ArgumentMatchers.any(), eq("rag_retriever")))
                .thenReturn(AgentToolResult.builder()
                        .toolName("rag_retriever")
                        .success(true)
                        .summary("检索到公司情报")
                        .data(Map.of("scene", "COMPANY", "chunkCount", 1,
                                "chunks", List.of(Map.of("citation", "COMPANY@tencent.md"))))
                        .build());

        SpecialistResult result = agent.process(context, "腾讯公司技术栈和规模怎么样", route);

        assertEquals("COMPANY", MarketSpecialistAgent.resolveScene("腾讯公司技术栈和规模怎么样"));
        assertEquals("COMPANY", result.getStructuredData().get("scene"));
        assertFalse(result.getCitations().isEmpty());
    }

    @Test
    void failureDoesNotThrow() {
        when(toolExecutionService.execute(org.mockito.ArgumentMatchers.any(), eq("rag_retriever")))
                .thenReturn(AgentToolResult.builder()
                        .toolName("rag_retriever")
                        .success(false)
                        .summary("知识库暂无相关内容")
                        .build());

        SpecialistResult result = agent.process(context, "薪资行情", route);

        assertEquals(SpecialistResultStatus.NO_ACTION, result.getStatus());
    }
}
