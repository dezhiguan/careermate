package com.careermate.agent.eval;

import com.careermate.agent.reflect.ReflectionJsonSupport;
import com.careermate.llm.LlmClient;
import com.careermate.llm.dto.ChatMessage;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.dto.ChatResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * A5：LLM-as-judge。对答案按 facet 打分，重点含"引用忠实度/语料支撑度"（防编造）。
 * 跨家避偏：judge 应与被评 model 不同家，通过 {@code judgeModel} 配置切换（当前单 provider 以独立 prompt 近似）。
 * 解析失败返回中性分，不抛异常。
 */
@Slf4j
@Component
public class AnswerJudge {

    private static final String SYSTEM_PROMPT = """
            你是严格的答案评审。对"答案"按 0-1 打分，facet：
            relevance（是否切题）、correctness（是否正确）、citation_faithfulness（结论是否真被给定参考资料支撑、有无编造）。
            没有参考资料支撑却下断言应重罚 citation_faithfulness。只输出 JSON：
            {"relevance":0-1,"correctness":0-1,"citation_faithfulness":0-1,"overall":0-1}
            """;

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public AnswerJudge(LlmClient llmClient, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    public EvalScore judge(String question, String answer, List<String> criteria, String judgeModel) {
        try {
            String user = "问题：" + question
                    + "\n评分标准：" + (criteria == null ? List.of() : criteria)
                    + "\n答案：" + (answer == null ? "" : answer);
            ChatRequest.ChatRequestBuilder req = ChatRequest.builder()
                    .temperature(0.1)
                    .messages(List.of(
                            ChatMessage.builder().role("system").content(SYSTEM_PROMPT).build(),
                            ChatMessage.builder().role("user").content(user).build()));
            if (StringUtils.hasText(judgeModel)) {
                req.model(judgeModel.trim());
            }
            ChatResponse resp = llmClient.chat(req.build());
            JsonNode json = ReflectionJsonSupport.extractJson(objectMapper, resp == null ? null : resp.getContent());
            if (json == null) {
                return EvalScore.neutral();
            }
            double relevance = clamp(ReflectionJsonSupport.doubleField(json, "relevance", 0.5));
            double correctness = clamp(ReflectionJsonSupport.doubleField(json, "correctness", 0.5));
            double citation = clamp(ReflectionJsonSupport.doubleField(json, "citation_faithfulness", 0.5));
            double overall = json.get("overall") != null
                    ? clamp(ReflectionJsonSupport.doubleField(json, "overall", 0.5))
                    : (relevance + correctness + citation) / 3.0;
            return new EvalScore(relevance, correctness, citation, overall);
        } catch (Exception e) {
            log.warn("judge 打分失败，返回中性分: {}", e.getMessage());
            return EvalScore.neutral();
        }
    }

    private double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
