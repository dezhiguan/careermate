package com.careermate.llm;

import com.careermate.llm.dto.ChatMessage;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.dto.ToolCallRequest;
import com.careermate.llm.provider.MockLlmClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockLlmToolReplyTest {

    private final LlmProperties properties = new LlmProperties();
    private final MockLlmClient client = new MockLlmClient(properties);

    @Test
    void repliesForToolResultsAndTaskLists() {
        assertReplyContains("""
                工具调用结果：
                工具：get_default_resume
                - title：后端简历
                """, "读取你的默认简历");
        assertReplyContains("""
                工具调用结果：
                工具：get_latest_job_match
                - jobTitle：Java 后端
                - matchScore：82
                - missingSkills：Kafka、Docker
                """, "匹配分数为 82 分");
        assertReplyContains("""
                工具调用结果：
                工具：create_job_match
                - jobTitle：平台后端
                - matchScore：76
                - matchedSkills：Java
                - missingSkills：Kubernetes
                """, "已为你生成岗位匹配结果");
        assertReplyContains("""
                工具调用结果：
                工具：create_interview_session
                - title：Redis 专项
                - totalQuestions：8
                """, "Redis 专项");
        assertReplyContains("""
                工具调用结果：
                工具：get_dashboard_overview
                - resumeCount：2
                - jobMatchCount：3
                - interviewSessionCount：4
                """, "求职看板");
        assertReplyContains("""
                工具调用结果：
                工具：get_career_tasks
                结果摘要：查询到任务
                chunks=[{title=完善项目指标, status=TODO}, {title=复盘面试题, status=TODO}]
                """, "完善项目指标、复盘面试题");
        assertReplyContains("""
                工具调用结果：
                工具：create_career_task
                结果摘要：已创建任务：补充 Kafka 项目
                """, "任务已创建：补充 Kafka 项目");
        assertReplyContains("""
                工具调用结果：
                工具：mark_career_task_done
                结果摘要：已完成任务：更新简历
                """, "任务已完成：更新简历");
    }

    @Test
    void streamChatSplitsTokensAndToolCallUsesFallbackModel() {
        properties.setModel("mock-default");
        AtomicReference<StringBuilder> tokens = new AtomicReference<>(new StringBuilder());
        AtomicReference<String> completed = new AtomicReference<>();

        client.streamChat(ChatRequest.builder()
                .messages(List.of(ChatMessage.builder().role("user").content("面试怎么准备").build()))
                .build(), new StreamCallback() {
            @Override
            public void onToken(String token) {
                tokens.get().append(token);
            }

            @Override
            public void onComplete(com.careermate.llm.dto.ChatResponse response) {
                completed.set(response.getContent());
            }

            @Override
            public void onError(Throwable error) {
                throw new AssertionError(error);
            }
        });

        assertEquals(completed.get(), tokens.get().toString());
        assertFalse(completed.get().isBlank());
        assertEquals("mock-default", client.toolCall(ToolCallRequest.builder().build()).getModel());
        assertTrue(client.toolCall(ToolCallRequest.builder()
                .messages(List.of(ChatMessage.builder().role("user").content("帮我查任务").build()))
                .model("tool-model")
                .build()).getContent().contains("帮我查任务"));
    }

    private void assertReplyContains(String system, String expected) {
        String content = client.chat(ChatRequest.builder()
                .messages(List.of(
                        ChatMessage.builder().role("system").content(system).build(),
                        ChatMessage.builder().role("user").content("继续").build()))
                .build()).getContent();
        assertTrue(content.contains(expected), content);
    }
}
