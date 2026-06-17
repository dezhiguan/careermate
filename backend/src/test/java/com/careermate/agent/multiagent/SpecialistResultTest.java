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

        assertEquals(SpecialistResultStatus.NO_ACTION, noTool.getStatus());
        assertNull(noTool.toolName());
        assertEquals("tool", withTool.toolName());
        assertEquals("summary", withTool.toolSummary());
        assertTrue(withTool.success());
        assertFalse(failed.success());
        assertEquals(SpecialistResultStatus.FAILED, failed.getStatus());
        assertEquals("reason", failed.toolSummary());
    }

    @Test
    void builderSupportsStructuredFields() {
        SpecialistResult blocked = SpecialistResult.builder()
                .domain(AgentDomain.CRITIC)
                .agentName("CriticAgent")
                .status(SpecialistResultStatus.BLOCKED)
                .riskLevel(SpecialistRiskLevel.HIGH)
                .summary("blocked")
                .warnings(java.util.List.of("no fabrication"))
                .structuredData(java.util.Map.of("reasonCode", "FABRICATION_REQUEST"))
                .build();

        assertEquals(SpecialistRiskLevel.HIGH, blocked.getRiskLevel());
        assertEquals("FABRICATION_REQUEST", blocked.getStructuredData().get("reasonCode"));
    }
}
