package com.careermate.agent.path;

/**
 * A2：Agent 执行路径分层。
 *
 * <ul>
 *   <li>{@link #FAST}：单 Agent 直答，覆盖绝大多数交互，秒级响应，不触发反思/debate 重管线。</li>
 *   <li>{@link #DEEP}：仅"深度定制简历 / 模拟面试复盘"等显式重任务触发，允许后续接入反思/debate。</li>
 * </ul>
 */
public enum AgentPathMode {
    FAST,
    DEEP
}
