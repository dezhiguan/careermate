package com.careermate.agent.dag;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * B2：按 {@link ToolDag} 层级并发执行工具——同层 CompletableFuture 并发、层间串行；
 * 每工具指数退避重试（仅 TransientException）、失败降级 fallback、输出轻量早校验（缺字段视同脏输出重试）、
 * partial-success 透明返回。占位符 {@code $id[idx].field} 从上游结果解析。熔断/滑动窗口本版不做。
 */
@Slf4j
public class ToolDagExecutor {

    private static final int MAX_CONCURRENCY_PER_LEVEL = 8;

    private final long backoffBaseMs;

    public ToolDagExecutor() {
        this(100L);
    }

    /** backoffBaseMs=0 便于测试（不真正 sleep）。 */
    public ToolDagExecutor(long backoffBaseMs) {
        this.backoffBaseMs = backoffBaseMs;
    }

    public ToolBatchResult execute(ToolDag dag, ToolInvoker invoker, Map<String, ToolDef> defs) {
        Map<String, Object> successes = new ConcurrentHashMap<>();
        List<ToolFailure> failures = new ArrayList<>();
        if (dag == null || dag.levels().isEmpty()) {
            return new ToolBatchResult(successes, failures);
        }

        for (List<ToolCallSpec> level : dag.levels()) {
            ExecutorService pool = Executors.newFixedThreadPool(Math.min(MAX_CONCURRENCY_PER_LEVEL, Math.max(1, level.size())));
            try {
                List<CompletableFuture<Void>> futures = new ArrayList<>();
                List<ToolFailure> levelFailures = new ArrayList<>();
                for (ToolCallSpec spec : level) {
                    futures.add(CompletableFuture.runAsync(() -> {
                        try {
                            Object out = runWithPolicy(spec, invoker, defs, successes);
                            successes.put(spec.id(), out);
                        } catch (Exception e) {
                            synchronized (levelFailures) {
                                levelFailures.add(new ToolFailure(spec.id(), spec.tool(),
                                        e instanceof PermanentException ? "PERMANENT" : "FAILED", e.getMessage()));
                            }
                        }
                    }, pool));
                }
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                failures.addAll(levelFailures);
            } finally {
                pool.shutdownNow();
            }
        }
        return new ToolBatchResult(successes, failures);
    }

    private Object runWithPolicy(ToolCallSpec spec, ToolInvoker invoker, Map<String, ToolDef> defs,
                                 Map<String, Object> resolved) {
        ToolDef def = defs == null ? null : defs.get(spec.tool());
        int maxRetry = def == null ? 2 : Math.max(0, def.maxRetry());
        Map<String, Object> args = ToolArgResolver.resolve(spec.args(), resolved);

        RuntimeException last = null;
        for (int attempt = 0; attempt <= maxRetry; attempt++) {
            try {
                Object out = invoker.invoke(spec.tool(), args);
                validateOutput(out, def);
                return out;
            } catch (PermanentException e) {
                throw e; // 不重试
            } catch (TransientException e) {
                last = e;
                if (attempt < maxRetry) {
                    sleepBackoff(attempt);
                }
            } catch (RuntimeException e) {
                // 未分类异常按瞬时处理（可重试）
                last = new TransientException(e.getMessage());
                if (attempt < maxRetry) {
                    sleepBackoff(attempt);
                }
            }
        }
        // 重试耗尽 → 尝试 fallback
        if (def != null && def.fallbackTool() != null && !def.fallbackTool().isBlank()) {
            try {
                Object out = invoker.invoke(def.fallbackTool(), args);
                validateOutput(out, def);
                return out;
            } catch (RuntimeException e) {
                throw new TransientException("fallback 也失败：" + e.getMessage());
            }
        }
        throw last != null ? last : new TransientException("工具执行失败");
    }

    private void validateOutput(Object out, ToolDef def) {
        if (out == null) {
            throw new TransientException("工具输出为空");
        }
        if (def == null || def.requiredFieldsOrEmpty().isEmpty()) {
            return;
        }
        if (!(out instanceof Map<?, ?> map)) {
            throw new TransientException("输出非结构化，无法校验必填字段");
        }
        for (String field : def.requiredFieldsOrEmpty()) {
            Object v = map.get(field);
            if (v == null || (v instanceof String s && s.isBlank())) {
                throw new TransientException("输出缺少必填字段：" + field);
            }
        }
    }

    private void sleepBackoff(int attempt) {
        if (backoffBaseMs <= 0) {
            return;
        }
        try {
            Thread.sleep(backoffBaseMs << attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
