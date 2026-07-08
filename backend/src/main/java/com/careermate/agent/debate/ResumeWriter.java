package com.careermate.agent.debate;

import com.careermate.llm.LlmClient;
import com.careermate.llm.dto.ChatMessage;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.dto.ChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * B1：Writer——按 JD 产出/修订简历稿。修订只接收 critic 的 criticism/suggestion，不接收 satisfaction 分数
 * （避免迎合分数）；禁止编造经历（沿用平台造假红线的措辞约束）。
 */
@Slf4j
@Service
public class ResumeWriter {

    private static final String SYSTEM = """
            你是资深简历顾问。基于用户真实简历与目标 JD，产出贴合 JD 的定制简历。
            硬约束：只优化真实经历的表述与匹配度，禁止编造项目/公司/学历/证书；学习或练习项目须标注为“个人练习项目”。
            直接输出简历正文，不要解释。
            """;

    private final LlmClient llmClient;
    private final DebateProperties properties;

    public ResumeWriter(LlmClient llmClient, DebateProperties properties) {
        this.llmClient = llmClient;
        this.properties = properties;
    }

    public String write(String jd, String resume) {
        String user = "目标 JD：\n" + safe(jd) + "\n\n用户真实简历：\n" + safe(resume);
        return call(user);
    }

    public String revise(String jd, String resume, String currentDraft, String criticism, String suggestion) {
        String user = "目标 JD：\n" + safe(jd)
                + "\n\n用户真实简历：\n" + safe(resume)
                + "\n\n当前稿：\n" + safe(currentDraft)
                + "\n\n需改进（来自评审）：\n" + safe(criticism)
                + "\n建议：\n" + safe(suggestion)
                + "\n\n请据此改出更好的一稿，仍不得编造。";
        return call(user);
    }

    private String call(String user) {
        try {
            ChatResponse resp = llmClient.chat(ChatRequest.builder()
                    .temperature(properties.getWriterTemperature())
                    .messages(List.of(
                            ChatMessage.builder().role("system").content(SYSTEM).build(),
                            ChatMessage.builder().role("user").content(user).build()))
                    .build());
            return resp == null || resp.getContent() == null ? "" : resp.getContent();
        } catch (Exception e) {
            log.warn("Writer 生成失败: {}", e.getMessage());
            return "";
        }
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
