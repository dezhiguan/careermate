package com.careermate.agent.memory.ltm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LongTermMemoryAdvisorTest {

    private final LongTermMemoryService service = mock(LongTermMemoryService.class);
    private final LongTermMemoryAdvisor advisor = new LongTermMemoryAdvisor(service);

    private LtmMatch match(String type, String text) {
        LtmMatch m = new LtmMatch();
        m.setFactType(type);
        m.setFactText(text);
        return m;
    }

    @Test
    void noFacts_returnsEmpty() {
        when(service.recall(eq(7L), anyString())).thenReturn(List.of());
        assertThat(advisor.buildMemoryBlock(7L, "帮我改简历")).isEmpty();
    }

    @Test
    void nullUserOrBlank_returnsEmpty() {
        assertThat(advisor.buildMemoryBlock(null, "x")).isEmpty();
        assertThat(advisor.buildMemoryBlock(7L, " ")).isEmpty();
    }

    @Test
    void facts_renderedAsBlock() {
        when(service.recall(eq(7L), anyString())).thenReturn(List.of(
                match("PREFERENCE", "只想远程"),
                match("SKILL", "精通 Java")));

        String block = advisor.buildMemoryBlock(7L, "推荐岗位");

        assertThat(block).contains("已知用户事实");
        assertThat(block).contains("[PREFERENCE] 只想远程");
        assertThat(block).contains("[SKILL] 精通 Java");
    }
}
