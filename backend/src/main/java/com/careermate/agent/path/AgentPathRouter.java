package com.careermate.agent.path;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * A2：fast / deep 分层路由——护住可用性的关键闸门。
 *
 * <p>默认走 FAST（单 Agent 直答）；只有用户显式勾选深度模式，或消息命中"深度定制简历 /
 * 模拟面试复盘"这类重任务意图，才进 DEEP。判定用轻量关键词规则（毫秒级），
 * 不额外发起强模型调用，避免 fast-path 成本上升。
 */
@Component
public class AgentPathRouter {

    private static final List<String> DEEP_RESUME_KEYWORDS = List.of(
            "定制简历", "简历定制", "改简历", "优化简历", "润色简历", "针对性简历", "简历改写"
    );
    private static final List<String> DEEP_INTERVIEW_KEYWORDS = List.of(
            "模拟面试", "面试复盘", "面试演练", "模拟一场面试", "模拟一轮面试"
    );

    /**
     * 判定执行路径。
     *
     * @param userMessage  用户消息
     * @param explicitDeep 前端是否显式勾选深度模式
     */
    public AgentPathDecision decide(String userMessage, boolean explicitDeep) {
        if (explicitDeep) {
            return new AgentPathDecision(AgentPathMode.DEEP, "user_explicit");
        }
        if (!StringUtils.hasText(userMessage)) {
            return new AgentPathDecision(AgentPathMode.FAST, "default_fast");
        }
        String text = userMessage.trim();
        if (containsAny(text, DEEP_RESUME_KEYWORDS)) {
            return new AgentPathDecision(AgentPathMode.DEEP, "deep_resume");
        }
        if (containsAny(text, DEEP_INTERVIEW_KEYWORDS)) {
            return new AgentPathDecision(AgentPathMode.DEEP, "deep_interview");
        }
        return new AgentPathDecision(AgentPathMode.FAST, "default_fast");
    }

    private boolean containsAny(String text, List<String> keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
