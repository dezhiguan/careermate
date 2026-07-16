package com.careermate.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "careermate.agent")
public class AgentProperties {

    private Long sseTimeoutMs = 300000L;
    private Integer executorCorePoolSize = 4;
    private Integer executorMaxPoolSize = 20;
    private Integer executorQueueCapacity = 200;
    private Integer maxConcurrentSessions = 100;
    private Long streamTaskTimeoutMs = 300000L;
    private Integer conversationContextMaxMessages = 10;
    private Integer conversationContextMaxChars = 6000;

    /**
     * M4 不失忆：Spring AI 对话记忆窗口的消息条数上限。
     *
     * <p>原为硬编码 20，导致一条 JD 主线聊过 20 条就丢上文。提高到「对真实会话近似全量」的水平，
     * 保证机会内连续不失忆；超长会话由 {@code conversationSummary} 滚动摘要兜底（已由 AgentMemoryService 注入）。
     * 仍保留上限以防病态超长会话拖爆 prompt。
     */
    private Integer conversationMemoryMaxMessages = 100;
}
