package com.careermate.agent.multiagent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpecialistResultTest {

    @Test
    void factoryMethodsBuildExpectedResults() {
        SpecialistResult noTool = SpecialistResult.noTool(AgentDomain.GENERAL);
        SpecialistResult withTool = SpecialistResult.withTool(AgentDomain.RESUME, "tool", "summary");
        SpecialistResult failed = SpecialistResult.failed(AgentDomain.INTERVIEW, "reason");

        assertTrue(noTool.success());
        assertNull(noTool.toolName());
        assertEquals("tool", withTool.toolName());
        assertEquals("summary", withTool.toolSummary());
        assertFalse(failed.success());
        assertEquals("reason", failed.toolSummary());
    }
}
