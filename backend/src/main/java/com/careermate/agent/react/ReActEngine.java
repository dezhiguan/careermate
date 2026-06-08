package com.careermate.agent.react;

import com.careermate.agent.tool.AgentToolContext;
import com.careermate.agent.tool.AgentToolExecutionService;
import com.careermate.agent.tool.AgentToolResult;
import com.careermate.llm.LlmClient;
import com.careermate.llm.dto.ChatMessage;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.dto.ChatResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReActEngine {

    private static final int MAX_ROUNDS = 3;
    private static final Pattern JSON_BLOCK = Pattern.compile("\\{[\\s\\S]*\\}");
    private static final Set<String> KNOWN_TOOLS = Set.of(
        "get_default_resume", "get_latest_job_match", "create_job_match",
        "create_interview_session", "get_dashboard_overview", "get_career_tasks",
        "create_career_task", "mark_career_task_done", "search_knowledge_base"
    );

    private static final String REACT_SYSTEM_PROMPT = """
        你是 CareerMate 求职 Agent，使用 ReAct 推理链解决用户问题。

        可用工具：
        - get_default_resume: 读取用户默认简历
        - get_latest_job_match: 读取最近岗位匹配结果
        - create_job_match: 创建新的岗位匹配（用户提供了 JD 时使用）
        - create_interview_session: 创建面试训练会话
        - get_dashboard_overview: 读取求职看板概览
        - search_knowledge_base: 搜索知识库

        请严格输出 JSON，不要 markdown 围栏：
        {"thought": "你的推理过程", "action": "工具名或final_answer"}

        当不需要再调用工具时，输出：
        {"thought": "已有足够信息", "action": "final_answer"}
        """;

    private final LlmClient llmClient;
    private final AgentToolExecutionService toolExecutionService;
    private final ObjectMapper objectMapper;

    /**
     * 针对复杂查询运行 ReAct 推理链，返回推理轨迹。
     * 简单问候 / GENERAL 域直接返回空 trace，不消耗额外 LLM 调用。
     */
    public ReActTrace run(AgentToolContext toolContext, String userMessage, String baseSystemPrompt) {
        if (!needsReAct(userMessage)) {
            return new ReActTrace(List.of(), false, 0);
        }

        List<ReActStep> steps = new ArrayList<>();
        List<ChatMessage> history = new ArrayList<>();
        history.add(ChatMessage.builder().role("system").content(REACT_SYSTEM_PROMPT).build());
        history.add(ChatMessage.builder().role("user").content(userMessage).build());

        for (int round = 1; round <= MAX_ROUNDS; round++) {
            ChatResponse response;
            try {
                ChatRequest req = ChatRequest.builder()
                    .messages(List.copyOf(history))
                    .temperature(0.1)
                    .build();
                response = llmClient.chat(req);
            } catch (Exception e) {
                log.warn("ReAct LLM call failed at round {}: {}", round, e.getMessage());
                break;
            }

            if (response == null || response.getContent() == null || response.getContent().isBlank()) {
                break;
            }

            String raw = response.getContent().trim();
            Matcher m = JSON_BLOCK.matcher(raw);
            if (!m.find()) {
                log.warn("ReAct round {} output not JSON, stopping", round);
                break;
            }

            String thought = "";
            String action = "final_answer";
            try {
                JsonNode node = objectMapper.readTree(m.group());
                thought = node.path("thought").asText("");
                action = node.path("action").asText("final_answer");
            } catch (Exception e) {
                log.warn("ReAct JSON parse failed at round {}: {}", round, e.getMessage());
                break;
            }

            if ("final_answer".equals(action) || !KNOWN_TOOLS.contains(action)) {
                steps.add(new ReActStep(round, thought, "final_answer", null));
                return new ReActTrace(steps, true, round);
            }

            AgentToolResult toolResult = toolExecutionService.execute(toolContext, action);
            String observation = toolResult.isSuccess()
                ? toolResult.getSummary()
                : "工具执行失败: " + toolResult.getErrorMessage();

            steps.add(new ReActStep(round, thought, action, observation));

            history.add(ChatMessage.builder().role("assistant").content(raw).build());
            history.add(ChatMessage.builder().role("user")
                .content("Observation: " + observation + "\n请继续推理。").build());

            log.info("ReAct round={} action={} observationLen={}", round, action,
                observation == null ? 0 : observation.length());
        }

        return new ReActTrace(steps, false, steps.size());
    }

    /** 简单启发：短消息或纯问候不走 ReAct，节省 LLM 调用 */
    private boolean needsReAct(String message) {
        if (message == null || message.trim().length() < 10) return false;
        String m = message.trim();
        if (m.equals("你好") || m.equals("hi") || m.equals("hello") || m.length() < 8) return false;
        return true;
    }
}
