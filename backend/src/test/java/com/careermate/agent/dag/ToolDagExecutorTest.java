package com.careermate.agent.dag;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ToolDagExecutorTest {

    private final ToolDagExecutor executor = new ToolDagExecutor(0L); // 不 sleep

    private ToolCallSpec spec(String id, String tool, Map<String, Object> args, String... deps) {
        return new ToolCallSpec(id, tool, args, List.of(deps));
    }

    @Test
    void concurrentLevel_allSucceed() {
        ToolDag dag = ToolDagCompiler.compile(List.of(
                spec("a", "ta", Map.of()), spec("b", "tb", Map.of())));
        ToolBatchResult r = executor.execute(dag, (tool, args) -> "out_" + tool, Map.of());
        assertThat(r.allSucceeded()).isTrue();
        assertThat(r.successes()).containsEntry("a", "out_ta").containsEntry("b", "out_tb");
    }

    @Test
    void retriesTransientThenSucceeds() {
        AtomicInteger calls = new AtomicInteger();
        ToolDag dag = ToolDagCompiler.compile(List.of(spec("a", "flaky", Map.of())));
        ToolInvoker invoker = (tool, args) -> {
            if (calls.incrementAndGet() < 3) {
                throw new TransientException("timeout");
            }
            return "ok";
        };
        ToolBatchResult r = executor.execute(dag, invoker, Map.of("flaky", new ToolDef("flaky", null, 3, List.of())));
        assertThat(r.successes()).containsEntry("a", "ok");
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void permanentException_notRetried_recordedAsFailure() {
        AtomicInteger calls = new AtomicInteger();
        ToolDag dag = ToolDagCompiler.compile(List.of(spec("a", "bad", Map.of())));
        ToolInvoker invoker = (tool, args) -> {
            calls.incrementAndGet();
            throw new PermanentException("参数错误");
        };
        ToolBatchResult r = executor.execute(dag, invoker, Map.of("bad", new ToolDef("bad", null, 3, List.of())));
        assertThat(r.allFailed()).isTrue();
        assertThat(r.failures().get(0).errorCode()).isEqualTo("PERMANENT");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void fallbackUsedWhenRetriesExhausted() {
        ToolDag dag = ToolDagCompiler.compile(List.of(spec("a", "main", Map.of())));
        ToolInvoker invoker = (tool, args) -> {
            if ("main".equals(tool)) {
                throw new TransientException("down");
            }
            return "fallback_out";
        };
        ToolBatchResult r = executor.execute(dag, invoker,
                Map.of("main", new ToolDef("main", "backup", 1, List.of())));
        assertThat(r.successes()).containsEntry("a", "fallback_out");
    }

    @Test
    void missingRequiredField_treatedAsTransient_retries() {
        AtomicInteger calls = new AtomicInteger();
        ToolDag dag = ToolDagCompiler.compile(List.of(spec("a", "t", Map.of())));
        ToolInvoker invoker = (tool, args) -> calls.incrementAndGet() == 1
                ? Map.of("other", "x")             // 缺 company → 脏输出重试
                : Map.of("company", "字节");
        ToolBatchResult r = executor.execute(dag, invoker,
                Map.of("t", new ToolDef("t", null, 2, List.of("company"))));
        assertThat(r.allSucceeded()).isTrue();
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void partialSuccess_successesAndFailuresCoexist() {
        ToolDag dag = ToolDagCompiler.compile(List.of(spec("ok", "good", Map.of()), spec("bad", "bad", Map.of())));
        ToolInvoker invoker = (tool, args) -> {
            if ("bad".equals(tool)) {
                throw new PermanentException("boom");
            }
            return "fine";
        };
        ToolBatchResult r = executor.execute(dag, invoker, Map.of("bad", new ToolDef("bad", null, 0, List.of())));
        assertThat(r.successes()).containsEntry("ok", "fine");
        assertThat(r.failures()).extracting(ToolFailure::id).containsExactly("bad");
    }

    @Test
    void dependentLevel_resolvesPlaceholderFromUpstream() {
        ToolDag dag = ToolDagCompiler.compile(List.of(
                spec("a", "search", Map.of()),
                spec("b", "analyze", Map.of("id", "$a.docId"), "a")));
        ToolInvoker invoker = (tool, args) -> {
            if ("search".equals(tool)) {
                return Map.of("docId", 42);
            }
            // b 应拿到解析后的 docId=42
            assertThat(args.get("id")).isEqualTo(42);
            return "analyzed";
        };
        ToolBatchResult r = executor.execute(dag, invoker, Map.of());
        assertThat(r.successes()).containsEntry("b", "analyzed");
    }

    // ---- ToolArgResolver ----

    @Test
    void resolver_indexAndField() {
        Map<String, Object> up = Map.of("s", List.of(Map.of("id", 7)));
        Map<String, Object> out = ToolArgResolver.resolve(Map.of("x", "$s[0].id", "y", "literal"), up);
        assertThat(out).containsEntry("x", 7).containsEntry("y", "literal");
    }

    @Test
    void resolver_unresolvedKeepsPlaceholder() {
        Map<String, Object> out = ToolArgResolver.resolve(Map.of("x", "$ghost.id"), Map.of());
        assertThat(out).containsEntry("x", "$ghost.id");
    }
}
