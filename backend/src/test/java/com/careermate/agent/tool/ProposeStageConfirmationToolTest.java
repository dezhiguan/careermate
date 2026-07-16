package com.careermate.agent.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProposeStageConfirmationToolTest {

    private ProposeStageConfirmationTool tool;

    @BeforeEach
    void setUp() {
        tool = new ProposeStageConfirmationTool();
    }

    @Test
    void metadataIsWellFormed() {
        assertEquals("propose_stage_confirmation", tool.name());
        assertEquals(AgentToolDomain.DASHBOARD, tool.definition().getDomain());
        assertEquals(4, tool.definition().getParameters().size());
        assertTrue(tool.supports(AgentToolContext.builder().build()));
    }

    @Test
    void buildsConfirmCardDataWithAutoQuestion() {
        AgentToolResult r = tool.execute(AgentToolContext.builder()
                .userId(1L)
                .args(Map.of("jdDocId", "doc-89840", "targetStage", "INTERVIEWING", "company", "字节跳动"))
                .build());

        assertTrue(r.isSuccess());
        assertEquals(89840L, r.getData().get("jdDocId"));
        assertEquals("INTERVIEWING", r.getData().get("targetStage"));
        assertEquals("面试中", r.getData().get("stageLabel"));
        assertEquals("字节跳动", r.getData().get("company"));
        assertTrue(String.valueOf(r.getData().get("question")).contains("字节跳动"));
        assertTrue(String.valueOf(r.getData().get("question")).contains("面试中"));
    }

    @Test
    void usesProvidedQuestion() {
        AgentToolResult r = tool.execute(AgentToolContext.builder()
                .userId(1L)
                .args(Map.of("jdDocId", "5", "targetStage", "OFFER", "question", "字节二面过了吗？"))
                .build());

        assertTrue(r.isSuccess());
        assertEquals("字节二面过了吗？", r.getData().get("question"));
    }

    @Test
    void missingJdDocIdFails() {
        AgentToolResult r = tool.execute(AgentToolContext.builder()
                .userId(1L)
                .args(Map.of("targetStage", "OFFER"))
                .build());
        assertFalse(r.isSuccess());
    }

    @Test
    void invalidStageFails() {
        AgentToolResult r = tool.execute(AgentToolContext.builder()
                .userId(1L)
                .args(Map.of("jdDocId", "5", "targetStage", "HIRED"))
                .build());
        assertFalse(r.isSuccess());
    }
}
