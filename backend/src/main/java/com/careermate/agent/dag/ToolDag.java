package com.careermate.agent.dag;

import java.util.List;

/**
 * B2：编译后的工具依赖图——按拓扑层级组织，同一层内可并发执行，层间串行。
 */
public record ToolDag(List<List<ToolCallSpec>> levels) {

    public int levelCount() {
        return levels.size();
    }

    public int totalNodes() {
        return levels.stream().mapToInt(List::size).sum();
    }
}
