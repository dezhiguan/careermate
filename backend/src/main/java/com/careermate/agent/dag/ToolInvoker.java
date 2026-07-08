package com.careermate.agent.dag;

import java.util.Map;

/** B2：实际执行一个工具（真实实现委托 AgentToolExecutionService）。抛 Transient/PermanentException 区分可否重试。 */
@FunctionalInterface
public interface ToolInvoker {
    Object invoke(String tool, Map<String, Object> args);
}
