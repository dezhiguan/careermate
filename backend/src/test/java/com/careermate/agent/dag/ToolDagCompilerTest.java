package com.careermate.agent.dag;

import com.careermate.common.exception.BizException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolDagCompilerTest {

    private ToolCallSpec spec(String id, String... deps) {
        return new ToolCallSpec(id, "tool_" + id, Map.of(), List.of(deps));
    }

    @Test
    void empty_returnsEmptyDag() {
        assertThat(ToolDagCompiler.compile(List.of()).levelCount()).isZero();
        assertThat(ToolDagCompiler.compile(null).levelCount()).isZero();
    }

    @Test
    void independentSpecs_singleLevel() {
        ToolDag dag = ToolDagCompiler.compile(List.of(spec("a"), spec("b"), spec("c")));
        assertThat(dag.levelCount()).isEqualTo(1);
        assertThat(dag.levels().get(0)).hasSize(3);
    }

    @Test
    void dependency_producesTwoLevels() {
        // c 依赖 a,b → level0=[a,b], level1=[c]
        ToolDag dag = ToolDagCompiler.compile(List.of(spec("a"), spec("b"), spec("c", "a", "b")));
        assertThat(dag.levelCount()).isEqualTo(2);
        assertThat(dag.levels().get(0)).extracting(ToolCallSpec::id).containsExactlyInAnyOrder("a", "b");
        assertThat(dag.levels().get(1)).extracting(ToolCallSpec::id).containsExactly("c");
    }

    @Test
    void chain_producesThreeLevels() {
        ToolDag dag = ToolDagCompiler.compile(List.of(spec("a"), spec("b", "a"), spec("c", "b")));
        assertThat(dag.levelCount()).isEqualTo(3);
        assertThat(dag.totalNodes()).isEqualTo(3);
    }

    @Test
    void cycle_throws() {
        // a→b→a
        assertThatThrownBy(() -> ToolDagCompiler.compile(List.of(spec("a", "b"), spec("b", "a"))))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("环");
    }

    @Test
    void selfLoop_throws() {
        assertThatThrownBy(() -> ToolDagCompiler.compile(List.of(spec("a", "a"))))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("自身");
    }

    @Test
    void danglingDependency_throws() {
        assertThatThrownBy(() -> ToolDagCompiler.compile(List.of(spec("a", "ghost"))))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不存在");
    }

    @Test
    void duplicateId_throws() {
        assertThatThrownBy(() -> ToolDagCompiler.compile(List.of(spec("a"), spec("a"))))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("重复");
    }
}
