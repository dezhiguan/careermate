package com.careermate.agent.multiagent;

import com.careermate.agent.tool.AgentToolExecutionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class SpecialistAgentSpecTest {

    @Mock
    private AgentToolExecutionService toolExecutionService;

    @Test
    void allSpecialistsExposeNonEmptySpec() {
        List<SpecialistAgent> agents = List.of(
                new ResumeSpecialistAgent(toolExecutionService, org.mockito.Mockito.mock(com.careermate.resume.version.service.ResumeVersionService.class)),
                new JobMatchSpecialistAgent(toolExecutionService),
                new InterviewSpecialistAgent(toolExecutionService),
                new MarketSpecialistAgent(toolExecutionService),
                new CriticAgent()
        );

        for (SpecialistAgent agent : agents) {
            SpecialistAgentSpec spec = agent.spec();
            assertNotNull(spec);
            assertEquals(agent.domain(), spec.domain());
            assertEquals(agent.agentName(), spec.agentName());
            assertFalse(spec.systemInstruction().isBlank());
            assertFalse(spec.inputSchemaName().isBlank());
            assertEquals(SpecialistAgentSpec.OUTPUT_SCHEMA_V1, spec.outputSchemaName());
            assertNotNull(spec.allowedTools());
        }
    }

    @Test
    void resumeSpecDeclaresAllowedTools() {
        SpecialistAgentSpec spec = new ResumeSpecialistAgent(toolExecutionService, org.mockito.Mockito.mock(com.careermate.resume.version.service.ResumeVersionService.class)).spec();

        assertEquals(AgentDomain.RESUME, spec.domain());
        assertEquals(List.of(
                "get_default_resume",
                "generate_resume_from_jd",
                "modify_resume",
                "search_knowledge_base"
        ), spec.allowedTools());
    }

    @Test
    void jobMatchSpecDeclaresAllowedTools() {
        SpecialistAgentSpec spec = new JobMatchSpecialistAgent(toolExecutionService).spec();

        assertEquals(List.of("get_latest_job_match", "create_job_match"), spec.allowedTools());
    }

    @Test
    void interviewSpecDeclaresAllowedTools() {
        SpecialistAgentSpec spec = new InterviewSpecialistAgent(toolExecutionService).spec();

        assertEquals(List.of("create_interview_session"), spec.allowedTools());
    }

    @Test
    void marketSpecOnlyAllowsRagRetriever() {
        SpecialistAgentSpec spec = new MarketSpecialistAgent(toolExecutionService).spec();

        assertEquals(List.of("rag_retriever"), spec.allowedTools());
        assertTrue(spec.systemInstruction().contains("市场"));
    }

    @Test
    void criticSpecHasNoAllowedTools() {
        SpecialistAgentSpec spec = new CriticAgent().spec();

        assertEquals(AgentDomain.CRITIC, spec.domain());
        assertTrue(spec.allowedTools().isEmpty());
        assertTrue(spec.systemInstruction().contains("造假"));
    }
}
