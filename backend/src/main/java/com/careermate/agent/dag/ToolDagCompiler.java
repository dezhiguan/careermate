package com.careermate.agent.dag;

import com.careermate.common.exception.BizException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * B2：把一组 {@link ToolCallSpec} 编译为分层可并发的 {@link ToolDag}。
 * Kahn 拓扑排序按层输出；检测 self-loop、悬空依赖、环，均抛友好错误。
 */
public final class ToolDagCompiler {

    private ToolDagCompiler() {
    }

    public static ToolDag compile(List<ToolCallSpec> specs) {
        if (specs == null || specs.isEmpty()) {
            return new ToolDag(List.of());
        }
        Map<String, ToolCallSpec> byId = new LinkedHashMap<>();
        for (ToolCallSpec spec : specs) {
            if (spec.id() == null || spec.id().isBlank()) {
                throw new BizException(400, "工具调用缺少 id，无法编排依赖");
            }
            if (byId.put(spec.id(), spec) != null) {
                throw new BizException(400, "工具调用 id 重复：" + spec.id());
            }
        }

        // 校验依赖 + 入度
        Map<String, Integer> indegree = new LinkedHashMap<>();
        Map<String, List<String>> dependents = new LinkedHashMap<>();
        for (ToolCallSpec spec : specs) {
            indegree.putIfAbsent(spec.id(), 0);
            for (String dep : spec.dependsOnOrEmpty()) {
                if (dep.equals(spec.id())) {
                    throw new BizException(400, "工具调用不能依赖自身：" + spec.id());
                }
                if (!byId.containsKey(dep)) {
                    throw new BizException(400, "依赖了不存在的工具调用：" + dep);
                }
                indegree.merge(spec.id(), 1, Integer::sum);
                dependents.computeIfAbsent(dep, k -> new ArrayList<>()).add(spec.id());
            }
        }

        List<List<ToolCallSpec>> levels = new ArrayList<>();
        List<String> currentLevel = new ArrayList<>();
        for (ToolCallSpec spec : specs) {
            if (indegree.get(spec.id()) == 0) {
                currentLevel.add(spec.id());
            }
        }
        int placed = 0;
        while (!currentLevel.isEmpty()) {
            List<ToolCallSpec> levelSpecs = new ArrayList<>();
            List<String> nextLevel = new ArrayList<>();
            for (String id : currentLevel) {
                levelSpecs.add(byId.get(id));
                placed++;
                for (String dependent : dependents.getOrDefault(id, List.of())) {
                    int d = indegree.merge(dependent, -1, Integer::sum);
                    if (d == 0) {
                        nextLevel.add(dependent);
                    }
                }
            }
            levels.add(levelSpecs);
            currentLevel = nextLevel;
        }

        if (placed != specs.size()) {
            throw new BizException(400, "工具依赖存在环，无法编排");
        }
        return new ToolDag(levels);
    }
}
