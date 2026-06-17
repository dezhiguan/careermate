package com.careermate.agent.multiagent;

import com.careermate.agent.tool.AgentToolContext;
import com.careermate.agent.tool.AgentToolExecutionService;
import com.careermate.agent.tool.AgentToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketSpecialistAgent implements SpecialistAgent {

    private final AgentToolExecutionService toolExecutionService;

    @Override
    public AgentDomain domain() {
        return AgentDomain.MARKET;
    }

    @Override
    public String agentName() {
        return "MarketSpecialistAgent";
    }

    @Override
    public SpecialistResult process(AgentToolContext context, String userMessage, AgentSupervisorRoute route) {
        try {
            String scene = resolveScene(userMessage);
            String queryPreview = SpecialistResultSupport.preview(userMessage);
            AgentToolContext executionContext = AgentToolContext.builder()
                    .userId(context.getUserId())
                    .sessionId(context.getSessionId())
                    .userMessage(userMessage)
                    .args(Map.of("query", queryPreview, "scene", scene, "topK", 5))
                    .build();
            AgentToolResult result = toolExecutionService.execute(executionContext, "rag_retriever");
            Map<String, Object> safeData = SpecialistResultSupport.safeToolData(result);
            safeData.put("scene", scene);
            safeData.put("queryPreview", queryPreview);
            List<String> citations = SpecialistResultSupport.extractCitations(result.getData());

            if (!result.isSuccess()) {
                return SpecialistResult.builder()
                        .domain(AgentDomain.MARKET)
                        .agentName(agentName())
                        .intent("MARKET_RETRIEVAL")
                        .toolName("rag_retriever")
                        .summary(result.getSummary())
                        .structuredData(safeData)
                        .citations(citations)
                        .status(SpecialistResultStatus.NO_ACTION)
                        .riskLevel(SpecialistRiskLevel.LOW)
                        .build();
            }

            return SpecialistResult.builder()
                    .domain(AgentDomain.MARKET)
                    .agentName(agentName())
                    .intent("MARKET_RETRIEVAL")
                    .toolName("rag_retriever")
                    .summary(result.getSummary())
                    .structuredData(safeData)
                    .citations(citations)
                    .status(SpecialistResultStatus.SUCCESS)
                    .riskLevel(SpecialistRiskLevel.LOW)
                    .build();
        } catch (Exception e) {
            log.warn("MarketSpecialistAgent failed: {}", e.getMessage());
            return SpecialistResult.failed(AgentDomain.MARKET, "市场知识检索失败");
        }
    }

    static String resolveScene(String userMessage) {
        if (userMessage == null) {
            return "MARKET";
        }
        String lower = userMessage.toLowerCase(Locale.ROOT);
        if (containsAny(lower, "公司", "企业", "大厂", "技术栈", "规模", "公司情报")) {
            return "COMPANY";
        }
        return "MARKET";
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
