package com.careermate.agent.multiagent;

import com.careermate.llm.LlmClient;
import com.careermate.llm.LlmProperties;
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
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentSupervisorRouter {

    private static final Pattern JSON_BLOCK = Pattern.compile("\\{[\\s\\S]*\\}");

    private static final String SYSTEM_PROMPT = """
            你是 CareerMate 多专家任务路由器。根据用户消息选择需要参与的业务专家领域。
            只输出 JSON，不要解释，不要 markdown 围栏，不要输出思考过程。

            可选领域：
            - RESUME：简历读取、优化、按 JD 生成简历
            - JOB_MATCH：岗位/JD 匹配、差距分析
            - INTERVIEW：面试准备、模拟面试、面试题
            - MARKET：市场行情、薪资、谈薪、技能趋势、公司情报
            - GENERAL：普通问答，不需要专家

            规则：
            - domains 最多 3 个业务领域
            - 若用户要求编造/伪造未发生经历、学历、证书、项目，requiresCritic=true
            - reasonCode 只能是短枚举，如 RESUME_OPTIMIZE / JOB_MATCH_AND_INTERVIEW / MARKET_SALARY

            输出格式：
            {"domains":["RESUME"],"primaryDomain":"RESUME","confidence":0.82,"requiresCritic":false,"reasonCode":"RESUME_OPTIMIZE"}
            """;

    private final LlmClient llmClient;
    private final LlmProperties llmProperties;
    private final ObjectMapper objectMapper;

    public AgentSupervisorRoute route(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return AgentSupervisorRoute.empty();
        }
        if (isMockProvider()) {
            return fallbackRoute(userMessage);
        }
        try {
            return routeByLlm(userMessage);
        } catch (Exception e) {
            log.warn("Supervisor LLM router failed, fallback to keywords: {}", e.getMessage());
            return fallbackRoute(userMessage);
        }
    }

    AgentSupervisorRoute fallbackRoute(String userMessage) {
        String lower = userMessage.toLowerCase(Locale.ROOT);
        boolean requiresCritic = FabricationRiskDetector.detect(userMessage);
        List<AgentDomain> domains = new ArrayList<>();

        if (isComplexMultiExpertRequest(lower)) {
            domains.add(AgentDomain.JOB_MATCH);
            domains.add(AgentDomain.RESUME);
            domains.add(AgentDomain.INTERVIEW);
            return new AgentSupervisorRoute(
                    domains,
                    AgentDomain.RESUME,
                    0.75D,
                    "RESUME_AND_JD_AND_INTERVIEW",
                    requiresCritic
            );
        }

        if (containsMarketIntent(lower)) {
            domains.add(AgentDomain.MARKET);
        }
        if (containsInterviewIntent(lower)) {
            domains.add(AgentDomain.INTERVIEW);
        }
        if (containsJobMatchIntent(lower)) {
            domains.add(AgentDomain.JOB_MATCH);
        }
        if (containsResumeIntent(lower)) {
            domains.add(AgentDomain.RESUME);
        }

        if (domains.isEmpty()) {
            return AgentSupervisorRoute.empty();
        }

        String reasonCode = switch (domains.get(0)) {
            case MARKET -> "MARKET_QUERY";
            case INTERVIEW -> "INTERVIEW_PREP";
            case JOB_MATCH -> "JOB_MATCH";
            case RESUME -> requiresCritic ? "RESUME_WITH_RISK" : "RESUME_OPTIMIZE";
            default -> "KEYWORD_FALLBACK";
        };
        return new AgentSupervisorRoute(domains, domains.get(0), 0.7D, reasonCode, requiresCritic);
    }

    private AgentSupervisorRoute routeByLlm(String userMessage) throws Exception {
        String text = userMessage.length() > 800 ? userMessage.substring(0, 800) : userMessage;
        ChatResponse response = llmClient.chat(ChatRequest.builder()
                .messages(List.of(
                        ChatMessage.builder().role("system").content(SYSTEM_PROMPT).build(),
                        ChatMessage.builder().role("user").content(text).build()
                ))
                .temperature(0.1)
                .build());
        if (response == null || response.getContent() == null || response.getContent().isBlank()) {
            return fallbackRoute(userMessage);
        }
        Matcher matcher = JSON_BLOCK.matcher(response.getContent().trim());
        if (!matcher.find()) {
            return fallbackRoute(userMessage);
        }
        JsonNode root = objectMapper.readTree(matcher.group());
        List<AgentDomain> domains = parseDomains(root.path("domains"));
        if (domains.isEmpty()) {
            return AgentSupervisorRoute.empty();
        }
        AgentDomain primary = parseDomain(root.path("primaryDomain").asText(null));
        if (primary == null || primary == AgentDomain.GENERAL || !domains.contains(primary)) {
            primary = domains.get(0);
        }
        double confidence = root.path("confidence").asDouble(0.75D);
        boolean requiresCritic = root.path("requiresCritic").asBoolean(false)
                || FabricationRiskDetector.detect(userMessage);
        String reasonCode = root.path("reasonCode").asText("LLM_ROUTER");
        return new AgentSupervisorRoute(domains, primary, confidence, reasonCode, requiresCritic);
    }

    private List<AgentDomain> parseDomains(JsonNode domainsNode) {
        if (domainsNode == null || !domainsNode.isArray()) {
            return List.of();
        }
        List<AgentDomain> domains = new ArrayList<>();
        for (JsonNode node : domainsNode) {
            AgentDomain domain = parseDomain(node.asText(null));
            if (domain != null && domain != AgentDomain.GENERAL && domain != AgentDomain.CRITIC) {
                domains.add(domain);
            }
        }
        return domains;
    }

    private AgentDomain parseDomain(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return AgentDomain.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean isMockProvider() {
        String provider = llmProperties.getProvider();
        return provider == null || provider.isBlank() || "mock".equalsIgnoreCase(provider.trim());
    }

    private static boolean isComplexMultiExpertRequest(String lower) {
        boolean hasJd = lower.contains("jd") || lower.contains("岗位") || lower.contains("职位");
        boolean hasResume = lower.contains("简历");
        boolean hasInterview = lower.contains("面试");
        boolean hasGap = lower.contains("差距") || lower.contains("匹配");
        return hasJd && hasResume && hasInterview
                || (hasJd && hasGap && hasInterview && hasResume);
    }

    private static boolean containsMarketIntent(String lower) {
        return containsAny(lower, "市场", "行情", "薪资", "谈薪", "招聘趋势", "就业市场", "公司情报", "技术栈", "规模");
    }

    private static boolean containsInterviewIntent(String lower) {
        return lower.contains("面试") || lower.contains("interview") || lower.contains("模拟") || lower.contains("练习");
    }

    private static boolean containsJobMatchIntent(String lower) {
        return lower.contains("jd") || lower.contains("岗位") || lower.contains("匹配") || lower.contains("职位")
                || lower.contains("招聘") || lower.contains("技能缺口") || lower.contains("差距");
    }

    private static boolean containsResumeIntent(String lower) {
        return lower.contains("简历") || lower.contains("resume") || lower.contains("经历") || lower.contains("项目经验")
                || (lower.contains("pdf") && containsAny(lower, "生成", "下载", "导出"));
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
