package com.careermate.knowledge;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 记录 T10 统一检索层迁移边界，避免误以为所有 RagForge 调用已收敛。
 */
class KnowledgeRetrievalMigrationStatusTest {

    @Test
    void documentsRemainingDirectSearchJdCallers() throws Exception {
        Path runner = Path.of(System.getProperty("user.dir"))
                .resolve("src/main/java/com/careermate/resume/version/workflow/GenerateResumeWorkflowRunner.java");
        String source = Files.readString(runner);
        assertTrue(source.contains("ragForgeClient.searchJd"),
                "GenerateResumeWorkflowRunner 仍直连 searchJd，待后续任务迁移");
    }

    @Test
    void migratedModulesDoNotImportDirectSearchJdInMarketAndJobMatch() throws Exception {
        Path root = Path.of(System.getProperty("user.dir"), "src/main/java/com/careermate");
        List<String> migrated = List.of(
                "market/service/MarketIntelligenceService.java",
                "jobmatch/JobMatchAnalyzer.java",
                "jobmatch/JobMatchLlmAnalyzer.java",
                "jobmatch/service/JobMatchService.java",
                "opportunity/service/impl/OpportunityServiceImpl.java"
        );
        for (String relative : migrated) {
            String source = Files.readString(root.resolve(relative));
            assertFalse(source.contains("ragForgeClient.searchJd"),
                    relative + " 不应再直连 searchJd");
        }
    }
}
