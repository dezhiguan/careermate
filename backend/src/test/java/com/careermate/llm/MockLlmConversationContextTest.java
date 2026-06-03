package com.careermate.llm;

import com.careermate.llm.dto.ChatMessage;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.provider.MockLlmClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MockLlmConversationContextTest {

    private final MockLlmClient client = new MockLlmClient(new LlmProperties());

    @Test
    void mockReplySuggestsBasedOnGoalAfterRefreshStyleQuestion() {
        String system = """
                你是 CareerMate 求职 Agent。

                【当前会话历史】
                user: 我的目标是 Java 后端开发岗位
                assistant: 收到，我会围绕 Java 后端目标为你规划。
                """;
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(
                        ChatMessage.builder().role("system").content(system).build(),
                        ChatMessage.builder().role("user").content("围绕这个目标，请给我两条具体建议").build()
                ))
                .build();

        String content = client.chat(request).getContent();
        assertTrue(content.contains("Java 后端"), content);
    }

    @Test
    void mockReplyRecallsJavaBackendGoalFromHistory() {
        String system = """
                你是 CareerMate 求职 Agent。

                【当前会话历史】
                user: 我的目标是 Java 后端开发岗位
                assistant: 收到，我会围绕 Java 后端目标为你规划。
                """;
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(
                        ChatMessage.builder().role("system").content(system).build(),
                        ChatMessage.builder().role("user").content("你还记得我的目标吗").build()
                ))
                .build();

        String content = client.chat(request).getContent();
        assertTrue(content.contains("Java 后端"), content);
        assertTrue(content.contains("记得"), content);
    }
}
