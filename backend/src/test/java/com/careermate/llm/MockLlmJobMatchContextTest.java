package com.careermate.llm;

import com.careermate.llm.dto.ChatMessage;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.provider.MockLlmClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MockLlmJobMatchContextTest {

    private final MockLlmClient client = new MockLlmClient(new LlmProperties());

    @Test
    void mockReplyMentionsLatestJobMatchWhenPresent() {
        String system = """
                你是 CareerMate 求职 Agent。

                最近岗位匹配结果：
                岗位：e2e_job_agent_context
                公司：test_co
                匹配分数：65
                匹配等级：中等匹配
                命中技能：Java、Spring Boot、Redis
                缺失技能：Elasticsearch、Docker
                优势：
                - 优势1
                风险：
                - 风险1
                建议：
                - 建议1
                总结：
                总结内容
                """;
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(
                        ChatMessage.builder().role("system").content(system).build(),
                        ChatMessage.builder().role("user").content("根据刚才那个岗位帮我优化简历").build()
                ))
                .build();

        String content = client.chat(request).getContent();
        assertTrue(content.contains("我已读取你最近的岗位匹配结果"), content);
        assertTrue(content.contains("e2e_job_agent_context"), content);
        assertTrue(content.contains("Elasticsearch"), content);
    }

    @Test
    void mockReplyPromptsJobMatchWhenMissing() {
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(
                        ChatMessage.builder().role("system").content("当前用户暂无岗位匹配记录。").build(),
                        ChatMessage.builder().role("user").content("我和这个岗位差距在哪里").build()
                ))
                .build();

        String content = client.chat(request).getContent();
        assertTrue(content.contains("还没有读取到岗位匹配记录"), content);
    }

    @Test
    void resumeOnlyIntentStillUsesResumeReply() {
        String system = """
                用户默认简历：
                标题：test_resume_only
                正文：
                Java
                """;
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(
                        ChatMessage.builder().role("system").content(system).build(),
                        ChatMessage.builder().role("user").content("帮我分析简历").build()
                ))
                .build();

        String content = client.chat(request).getContent();
        assertTrue(content.contains("我已读取你的默认简历"), content);
        assertTrue(content.contains("test_resume_only"), content);
    }
}
