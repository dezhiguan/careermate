package com.careermate.resume.service;

import com.careermate.llm.LlmClient;
import com.careermate.llm.dto.ChatMessage;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.dto.ChatResponse;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从简历文本中抽取职业画像字段（评审 P0-1）。
 *
 * <p>纯抽取，无副作用：调用 LLM 把简历解析成 {@link ExtractedProfile}，供
 * {@link ResumeProfileAutoFillService} 回填到画像。失败/无法解析时返回 empty，绝不抛出。
 */
@Slf4j
@Service
public class ResumeProfileExtractor {

    private static final Pattern JSON_BLOCK = Pattern.compile("\\{[\\s\\S]*\\}");
    private static final int MAX_RESUME_CHARS = 6000;

    private static final String PROMPT = """
            你是简历分析助手。从以下简历文本中提取求职者的职业画像字段，用于岗位匹配。
            要求：
            1. targetRole：最贴合的目标岗位名（如「Java后端工程师」），无法判断则留空字符串
            2. targetCity：意向或现居城市，无法判断则留空字符串
            3. seniority：经验年限档，只能取其一：1-3年 / 3-5年 / 5-10年 / 10年以上；无法判断留空
            4. workMode：工作方式，只能取其一：全职 / 实习 / 远程 / 兼职；无法判断留空
            5. skillKeywords：核心技能关键词数组（最多 8 个，如 ["Java","Spring","MySQL"]）
            只依据简历内容判断，不要臆造。只输出以下 JSON，不要任何其他文字：
            {"targetRole":"","targetCity":"","seniority":"","workMode":"","skillKeywords":[]}

            简历内容：
            %s
            """;

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public ResumeProfileExtractor(LlmClient llmClient, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    public Optional<ExtractedProfile> extract(String resumeContent) {
        if (resumeContent == null || resumeContent.isBlank()) {
            return Optional.empty();
        }
        try {
            String content = resumeContent.length() > MAX_RESUME_CHARS
                    ? resumeContent.substring(0, MAX_RESUME_CHARS)
                    : resumeContent;
            ChatResponse response = llmClient.chat(ChatRequest.builder()
                    .messages(List.of(
                            ChatMessage.builder().role("system")
                                    .content("你只输出合法 JSON，不输出任何其他文字或解释。").build(),
                            ChatMessage.builder().role("user")
                                    .content(String.format(PROMPT, content)).build()
                    ))
                    .temperature(0.2)
                    .build());
            if (response == null || response.getContent() == null || response.getContent().isBlank()) {
                return Optional.empty();
            }
            Matcher matcher = JSON_BLOCK.matcher(response.getContent().trim());
            if (!matcher.find()) {
                return Optional.empty();
            }
            return Optional.ofNullable(objectMapper.readValue(matcher.group(), ExtractedProfile.class));
        } catch (Exception e) {
            log.warn("resume profile extract failed: err={}", e.getMessage());
            return Optional.empty();
        }
    }

    /** 简历抽取出的画像字段，字段缺失时为 null。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExtractedProfile(
            String targetRole,
            String targetCity,
            String seniority,
            String workMode,
            List<String> skillKeywords
    ) {
    }
}
