package com.careermate.agent.tool.springai;

import com.careermate.agent.tool.AgentToolContext;
import com.careermate.agent.tool.AgentToolRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class RagRetrieverSpringAiToolCallbackTest {

    @Autowired
    private AgentToolRegistry registry;

    @Autowired
    private SpringAiToolCallbackFactory callbackFactory;

    @Test
    void springAiCallbacksIncludeRagRetriever() {
        assertTrue(registry.knownToolNames().contains("rag_retriever"));

        AgentToolContext context = AgentToolContext.builder()
                .userId(900001L)
                .sessionId("rag-springai-session")
                .userMessage("广州 Java 后端行情怎么样")
                .build();

        List<ToolCallback> callbacks = callbackFactory.createCallbacks(context);
        assertTrue(callbacks.stream()
                .anyMatch(callback -> "rag_retriever".equals(callback.getToolDefinition().name())));
    }
}
