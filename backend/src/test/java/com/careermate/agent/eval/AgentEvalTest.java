package com.careermate.agent.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class AgentEvalTest {

    private static final Logger log = LoggerFactory.getLogger(AgentEvalTest.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Path REPORT_JSON = Path.of("target", "agent-eval-report.json");
    private static final Path REPORT_MD = Path.of("target", "agent-eval-report.md");

    @Test
    void runAgentEvalSuite() throws IOException {
        List<AgentEvalCase> cases = loadCases();
        AgentEvalRunner runner = new AgentEvalRunner();
        List<AgentEvalCaseResult> results = new ArrayList<>();
        for (AgentEvalCase evalCase : cases) {
            results.add(runner.evaluate(evalCase));
        }

        AgentEvalReport report = AgentEvalReport.fromResults(cases, results);
        writeReports(report);
        printSummary(report);

        if (!report.failedCaseIds().isEmpty()) {
            fail("Agent eval failed cases: " + report.failedCaseIds());
        }
        assertEquals(0, report.failed());
        assertEquals(report.totalCases(), report.passed());
    }

    private static List<AgentEvalCase> loadCases() throws IOException {
        try (InputStream in = AgentEvalTest.class.getClassLoader()
                .getResourceAsStream("agent-eval/cases.json")) {
            if (in == null) {
                throw new IllegalStateException("agent-eval/cases.json not found on classpath");
            }
            AgentEvalCase[] loaded = OBJECT_MAPPER.readValue(in, AgentEvalCase[].class);
            if (loaded.length < 15) {
                throw new IllegalStateException("agent eval requires at least 15 cases, found " + loaded.length);
            }
            List<AgentEvalCase> cases = List.of(loaded);
            AgentEvalCaseValidator.validateAll(cases);
            return cases;
        }
    }

    private static void writeReports(AgentEvalReport report) throws IOException {
        Files.createDirectories(REPORT_JSON.getParent());
        OBJECT_MAPPER.writerWithDefaultPrettyPrinter()
                .writeValue(REPORT_JSON.toFile(), report.toMap());
        Files.writeString(REPORT_MD, renderMarkdown(report), StandardCharsets.UTF_8);
    }

    private static void printSummary(AgentEvalReport report) {
        long ragHitCount = report.ragHits().stream()
                .filter(hit -> "RAG_HIT".equals(hit.status()))
                .count();
        log.info("===== Agent Eval Summary =====");
        log.info("totalCases={}", report.totalCases());
        log.info("passed={} failed={}", report.passed(), report.failed());
        log.info("tool_accuracy={}", String.format("%.4f", report.toolAccuracy()));
        log.info("tool_accuracy_by_domain={}", report.toolAccuracyByDomain());
        log.info("rag_hit_cases={}", ragHitCount);
        log.info("failedCaseIds={}", report.failedCaseIds());
        log.info("reportJson={}", REPORT_JSON.toAbsolutePath());
        log.info("reportMd={}", REPORT_MD.toAbsolutePath());
        log.info("==============================");
    }

    private static String renderMarkdown(AgentEvalReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Agent Eval Report\n\n");
        sb.append("- totalCases: ").append(report.totalCases()).append('\n');
        sb.append("- passed: ").append(report.passed()).append('\n');
        sb.append("- failed: ").append(report.failed()).append('\n');
        sb.append("- tool_accuracy: ").append(String.format("%.4f", report.toolAccuracy())).append('\n');
        sb.append("- failedCaseIds: ").append(report.failedCaseIds()).append("\n\n");

        sb.append("## Tool Accuracy By Domain\n\n");
        for (Map.Entry<String, Double> entry : report.toolAccuracyByDomain().entrySet()) {
            sb.append("- ").append(entry.getKey()).append(": ")
                    .append(String.format("%.4f", entry.getValue())).append('\n');
        }

        sb.append("\n## RAG Hits\n\n");
        for (AgentEvalRagHit hit : report.ragHits()) {
            sb.append("- ").append(hit.caseId()).append(": ")
                    .append(hit.status()).append(" tool=").append(hit.tool())
                    .append(" scene=").append(hit.scene())
                    .append(" kbId=").append(hit.kbId())
                    .append(" topK=").append(hit.topK())
                    .append(" chunks=").append(hit.chunkCount())
                    .append('\n');
        }

        sb.append("\n## Failed Cases\n\n");
        if (report.failedCaseIds().isEmpty()) {
            sb.append("- none\n");
        } else {
            for (AgentEvalCaseResult result : report.caseResults()) {
                if (!result.passed()) {
                    sb.append("- ").append(result.caseId())
                            .append(": ").append(result.failures()).append('\n');
                }
            }
        }
        return sb.toString();
    }
}
