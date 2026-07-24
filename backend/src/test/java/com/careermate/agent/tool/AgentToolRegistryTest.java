package com.careermate.agent.tool;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class AgentToolRegistryTest {

    private static final Set<String> EXPECTED_TOOLS = Set.of(
            "get_default_resume",
            "get_latest_job_match",
            "create_job_match",
            "create_interview_session",
            "get_dashboard_overview",
            "get_career_tasks",
            "create_career_task",
            "mark_career_task_done",
            "search_knowledge_base",
            "rag_retriever",
            "generate_resume_from_jd",
            "modify_resume",
            // 围绕 JD 的六项能力新增工具（M1/M2/M3）
            "get_company_atmosphere",
            "generate_jd_aware_questions",
            "get_salary_guidance",
            "get_pipeline_board",
            "advance_application_stage",
            "propose_stage_confirmation",
            // 设计缺口 P1 对话化补齐工具
            "update_career_profile",
            "filter_opportunities",
            "list_resume_versions"
    );

    @Autowired
    private AgentToolRegistry registry;

    @Test
    void listDefinitionsContainsAllTools() {
        List<AgentToolDefinition> definitions = registry.listDefinitions();
        assertEquals(21, definitions.size());
        Set<String> names = definitions.stream()
                .map(AgentToolDefinition::getName)
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(EXPECTED_TOOLS, names);
    }

    @Test
    void knownToolNamesContainsAllRegisteredTools() {
        assertEquals(EXPECTED_TOOLS, registry.knownToolNames());
    }

    @Test
    void definitionOfReturnsMatchingDefinition() {
        AgentToolDefinition definition = registry.definitionOf("create_job_match").orElseThrow();
        assertEquals("create_job_match", definition.getName());
        assertEquals(AgentToolDomain.JOB_MATCH, definition.getDomain());
        assertEquals(AgentToolPermission.WRITE_USER_DATA, definition.getPermission());
        assertEquals(AgentToolRiskLevel.MEDIUM, definition.getRiskLevel());
        assertTrue(definition.getParameters().stream()
                .anyMatch(p -> "jdContent".equals(p.getName()) && p.isRequired()));
    }

    @Test
    void ragRetrieverDefinitionContainsSchemaFields() {
        AgentToolDefinition definition = registry.definitionOf("rag_retriever").orElseThrow();
        assertEquals("rag_retriever", definition.getName());
        assertEquals(AgentToolDomain.KNOWLEDGE, definition.getDomain());
        assertTrue(definition.getParameters().stream().anyMatch(p -> "query".equals(p.getName())));
        assertTrue(definition.getParameters().stream().anyMatch(p -> "scene".equals(p.getName())));
        assertTrue(definition.getParameters().stream().anyMatch(p -> "topK".equals(p.getName())));
    }

    @Test
    void definitionOfUnknownToolIsEmpty() {
        assertFalse(registry.definitionOf("not_a_tool").isPresent());
    }

    @Test
    void duplicateToolNameFailsOnStartup() {
        AgentTool first = stubTool("duplicate_tool", "first");
        AgentTool second = stubTool("duplicate_tool", "second");
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> new AgentToolRegistry(List.of(first, second))
        );
        assertTrue(ex.getMessage().contains("duplicate_tool"));
    }

    private static AgentTool stubTool(String name, String description) {
        return new AgentTool() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return description;
            }

            @Override
            public boolean supports(AgentToolContext context) {
                return true;
            }

            @Override
            public AgentToolResult execute(AgentToolContext context) {
                return AgentToolResult.success(name, "ok", null);
            }
        };
    }
}
