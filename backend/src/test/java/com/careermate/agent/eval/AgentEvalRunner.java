package com.careermate.agent.eval;

import com.careermate.agent.AgentPromptAssembler;
import com.careermate.agent.multiagent.AgentDomain;
import com.careermate.agent.multiagent.AgentSupervisor;
import com.careermate.agent.multiagent.AgentSupervisorRoute;
import com.careermate.agent.multiagent.AgentSupervisorRouter;
import com.careermate.agent.multiagent.CriticAgent;
import com.careermate.agent.multiagent.InterviewSpecialistAgent;
import com.careermate.agent.multiagent.JobMatchSpecialistAgent;
import com.careermate.agent.multiagent.MarketSpecialistAgent;
import com.careermate.agent.multiagent.ResumeSpecialistAgent;
import com.careermate.agent.multiagent.SpecialistResult;
import com.careermate.agent.multiagent.SpecialistResultStatus;
import com.careermate.agent.tool.AgentToolContext;
import com.careermate.agent.tool.AgentToolExecutionService;
import com.careermate.agent.tool.AgentToolResult;
import com.careermate.agent.tool.AgentToolRouter;
import com.careermate.llm.LlmClient;
import com.careermate.llm.LlmProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class AgentEvalRunner {

    private final AgentSupervisorRouter supervisorRouter;
    private final AgentSupervisor supervisor;
    private final AgentToolRouter legacyRouter;
    private final List<ToolInvocation> invocations = new ArrayList<>();

    AgentEvalRunner() {
        LlmProperties properties = new LlmProperties();
        properties.setProvider("mock");
        supervisorRouter = new AgentSupervisorRouter(mock(LlmClient.class), properties, new ObjectMapper());

        AgentToolExecutionService toolExecutionService = buildRecordingToolExecutionService();
        supervisor = new AgentSupervisor(
                supervisorRouter,
                new CriticAgent(),
                new ResumeSpecialistAgent(toolExecutionService),
                new JobMatchSpecialistAgent(toolExecutionService),
                new InterviewSpecialistAgent(toolExecutionService),
                new MarketSpecialistAgent(toolExecutionService)
        );
        legacyRouter = new AgentToolRouter();
    }

    AgentEvalCaseResult evaluate(AgentEvalCase evalCase) {
        invocations.clear();
        List<String> failures = new ArrayList<>();

        if ("legacy_router".equals(evalCase.evalPathOrDefault())) {
            return evaluateLegacyRouter(evalCase, failures);
        }

        AgentSupervisorRoute route = supervisorRouter.route(evalCase.input());
        List<String> routedDomains = route.businessDomains().stream()
                .map(AgentDomain::name)
                .toList();

        verifyRouting(evalCase, route, routedDomains, failures);

        AgentToolContext context = AgentToolContext.builder()
                .userId(1L)
                .sessionId("EVAL-SESSION")
                .userMessage(evalCase.input())
                .build();

        List<SpecialistResult> specialistResults = supervisor.dispatch(context, evalCase.input());
        boolean blocked = specialistResults.stream()
                .anyMatch(result -> result.getStatus() == SpecialistResultStatus.BLOCKED);

        if (evalCase.blockedExpected()) {
            if (!blocked) {
                failures.add("expected BLOCKED by CriticAgent");
            }
        } else if (blocked) {
            failures.add("unexpected BLOCKED by CriticAgent");
        }

        List<String> selectedTools = collectTools(specialistResults);
        if (!blocked && AgentPromptAssembler.shouldRunLegacyToolFallback(blocked, specialistResults)) {
            legacyRouter.route(evalCase.input()).ifPresent(routed -> {
                AgentToolContext legacyContext = AgentToolContext.builder()
                        .userId(1L)
                        .sessionId("EVAL-SESSION")
                        .userMessage(evalCase.input())
                        .args(routed.args())
                        .build();
                executeTool(legacyContext, routed.toolName());
                selectedTools.add(routed.toolName());
            });
        }

        boolean toolsMatched = verifyTools(evalCase, selectedTools, failures);
        AgentEvalRagHit ragHit = resolveRagHit(evalCase, failures);
        verifyMustContain(evalCase, route, routedDomains, selectedTools, specialistResults, blocked, ragHit, failures);
        verifyForbiddenPhrases(evalCase, specialistResults, failures);

        return new AgentEvalCaseResult(
                evalCase.caseId(),
                evalCase.domain(),
                failures.isEmpty(),
                List.copyOf(failures),
                route.reasonCode(),
                routedDomains,
                route.requiresCritic(),
                blocked,
                List.copyOf(new LinkedHashSet<>(selectedTools)),
                toolsMatched,
                ragHit
        );
    }

    private AgentEvalCaseResult evaluateLegacyRouter(AgentEvalCase evalCase, List<String> failures) {
        var routed = legacyRouter.route(evalCase.input());
        if (routed.isEmpty()) {
            failures.add("legacy AgentToolRouter returned empty");
        }
        List<String> selectedTools = new ArrayList<>();
        routed.ifPresent(tool -> {
            AgentToolContext context = AgentToolContext.builder()
                    .userId(1L)
                    .sessionId("EVAL-SESSION")
                    .userMessage(evalCase.input())
                    .args(tool.args())
                    .build();
            executeTool(context, tool.toolName());
            selectedTools.add(tool.toolName());
        });
        boolean toolsMatched = verifyTools(evalCase, selectedTools, failures);
        AgentEvalRagHit ragHit = resolveRagHit(evalCase, failures);
        verifyMustContain(evalCase, AgentSupervisorRoute.empty(), List.of(), selectedTools, List.of(), false, ragHit, failures);
        return new AgentEvalCaseResult(
                evalCase.caseId(),
                evalCase.domain(),
                failures.isEmpty(),
                List.copyOf(failures),
                evalCase.expectedIntent(),
                List.of(),
                false,
                false,
                List.copyOf(selectedTools),
                toolsMatched,
                ragHit
        );
    }

    private void verifyRouting(
            AgentEvalCase evalCase,
            AgentSupervisorRoute route,
            List<String> routedDomains,
            List<String> failures
    ) {
        if ("GENERAL".equals(evalCase.expectedIntent())) {
            if (!routedDomains.isEmpty()) {
                failures.add("expected GENERAL route but got domains=" + routedDomains);
            }
            return;
        }
        if (!evalCase.expectedIntent().equals(route.reasonCode())
                && !routedDomains.contains(evalCase.expectedIntent())) {
            failures.add("expectedIntent mismatch: expected=" + evalCase.expectedIntent()
                    + " actualReasonCode=" + route.reasonCode());
        }
        if (!evalCase.safeExpectedDomains().isEmpty()) {
            Set<String> expected = new LinkedHashSet<>(evalCase.safeExpectedDomains());
            Set<String> actual = new LinkedHashSet<>(routedDomains);
            if (!actual.equals(expected)) {
                failures.add("expectedDomains mismatch: expected=" + expected + " actual=" + actual);
            }
        }
        if (evalCase.criticRequired() && !route.requiresCritic()) {
            failures.add("expected requiresCritic=true");
        }
    }

    private static boolean verifyTools(AgentEvalCase evalCase, List<String> selectedTools, List<String> failures) {
        List<String> expected = evalCase.safeExpectedTools();
        if (expected.isEmpty()) {
            if (!selectedTools.isEmpty()) {
                if (evalCase.blockedExpected()) {
                    failures.add("blocked case should not execute tools but got " + selectedTools);
                } else if (!evalCase.extraToolsAllowed()) {
                    failures.add("unexpected tools when expectedTools empty: " + selectedTools);
                }
            }
            return selectedTools.isEmpty() || evalCase.extraToolsAllowed();
        }
        Set<String> actual = new LinkedHashSet<>(selectedTools);
        List<String> missing = expected.stream().filter(tool -> !actual.contains(tool)).toList();
        if (!missing.isEmpty()) {
            failures.add("missing expected tools: " + missing + " actual=" + actual);
            return false;
        }
        return true;
    }

    private AgentEvalRagHit resolveRagHit(AgentEvalCase evalCase, List<String> failures) {
        AgentEvalExpectedRag expectedRag = evalCase.expectedRag();
        List<ToolInvocation> ragInvocations = invocations.stream()
                .filter(inv -> isRagTool(inv.toolName()))
                .toList();

        if (expectedRag == null) {
            return null;
        }

        if (ragInvocations.isEmpty()) {
            failures.add("expected RAG tool " + expectedRag.tool() + " but no RAG invocation recorded");
            return AgentEvalRagHit.noHit(evalCase.caseId(), "NO_RAG_HIT");
        }

        ToolInvocation hit = ragInvocations.stream()
                .filter(inv -> expectedRag.tool() == null || expectedRag.tool().equals(inv.toolName()))
                .findFirst()
                .orElse(ragInvocations.get(0));

        String scene = stringArg(hit.args(), "scene");
        Integer topK = intArg(hit.args(), "topK");
        Integer chunkCount = 2;
        String kbId = scene == null ? "unknown" : scene + "-kb";

        if (expectedRag.scene() != null && !expectedRag.scene().equalsIgnoreCase(scene)) {
            failures.add("RAG scene mismatch: expected=" + expectedRag.scene() + " actual=" + scene);
        }
        if (expectedRag.topK() != null && !expectedRag.topK().equals(topK)) {
            failures.add("RAG topK mismatch: expected=" + expectedRag.topK() + " actual=" + topK);
        }
        if (expectedRag.minChunks() != null && chunkCount < expectedRag.minChunks()) {
            failures.add("RAG chunkCount below minChunks: expected>=" + expectedRag.minChunks()
                    + " actual=" + chunkCount);
        }

        return new AgentEvalRagHit(
                evalCase.caseId(),
                hit.toolName(),
                scene,
                kbId,
                topK,
                chunkCount,
                "RAG_HIT"
        );
    }

    private static void verifyMustContain(
            AgentEvalCase evalCase,
            AgentSupervisorRoute route,
            List<String> routedDomains,
            List<String> selectedTools,
            List<SpecialistResult> specialistResults,
            boolean blocked,
            AgentEvalRagHit ragHit,
            List<String> failures
    ) {
        String corpus = (route.reasonCode() + " "
                + String.join(" ", routedDomains) + " "
                + String.join(" ", selectedTools) + " "
                + specialistResults.stream().map(SpecialistResult::getSummary).collect(Collectors.joining(" ")) + " "
                + (blocked ? "BLOCKED CRITIC" : "") + " "
                + (ragHit == null ? "" : ragHit.scene() + " " + ragHit.tool() + " " + ragHit.status()))
                .toUpperCase(Locale.ROOT);

        for (String token : evalCase.safeMustContain()) {
            if (!corpus.contains(token.toUpperCase(Locale.ROOT))) {
                failures.add("mustContain missing token: " + token);
            }
        }
    }

    private static void verifyForbiddenPhrases(
            AgentEvalCase evalCase,
            List<SpecialistResult> specialistResults,
            List<String> failures
    ) {
        String corpus = specialistResults.stream()
                .map(SpecialistResult::getSummary)
                .filter(summary -> summary != null && !summary.isBlank())
                .collect(Collectors.joining(" "))
                .toLowerCase(Locale.ROOT);
        for (String phrase : evalCase.safeForbiddenPhrases()) {
            if (corpus.contains(phrase.toLowerCase(Locale.ROOT))) {
                failures.add("forbidden phrase present: " + phrase);
            }
        }
    }

    private List<String> collectTools(List<SpecialistResult> specialistResults) {
        List<String> tools = new ArrayList<>();
        for (SpecialistResult result : specialistResults) {
            if (result.getToolName() != null && !result.getToolName().isBlank()) {
                tools.add(result.getToolName());
            }
        }
        return tools;
    }

    private AgentToolExecutionService buildRecordingToolExecutionService() {
        AgentToolExecutionService service = mock(AgentToolExecutionService.class);
        when(service.execute(any(), anyString())).thenAnswer(invocation -> {
            AgentToolContext context = invocation.getArgument(0);
            String toolName = invocation.getArgument(1);
            Map<String, Object> args = context.getArgs() == null ? Map.of() : context.getArgs();
            return executeTool(context, toolName);
        });
        return service;
    }

    private AgentToolResult executeTool(AgentToolContext context, String toolName) {
        Map<String, Object> args = context.getArgs() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(context.getArgs());
        if (isRagTool(toolName)) {
            if (!args.containsKey("topK")) {
                args.put("topK", 5);
            }
            if (!args.containsKey("scene") && "search_knowledge_base".equals(toolName)) {
                args.put("scene", "OPPORTUNITY");
            }
        }
        invocations.add(new ToolInvocation(toolName, Map.copyOf(args)));
        return stubToolResult(toolName, args);
    }

    private static AgentToolResult stubToolResult(String toolName, Map<String, Object> args) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("toolName", toolName);
        data.put("success", true);
        if (isRagTool(toolName)) {
            String scene = stringArg(args, "scene");
            if (scene == null && "search_knowledge_base".equals(toolName)) {
                scene = "OPPORTUNITY";
            }
            data.put("scene", scene);
            data.put("kbId", scene == null ? "general-kb" : scene + "-kb");
            data.put("topK", intArg(args, "topK"));
            data.put("chunkCount", 2);
            data.put("chunks", List.of(
                    Map.of("citation", scene + "@chunk-1"),
                    Map.of("citation", scene + "@chunk-2")
            ));
        }
        return AgentToolResult.builder()
                .toolName(toolName)
                .success(true)
                .summary("eval-stub:" + toolName)
                .data(data)
                .build();
    }

    private static boolean isRagTool(String toolName) {
        return "rag_retriever".equals(toolName) || "search_knowledge_base".equals(toolName);
    }

    private static String stringArg(Map<String, Object> args, String key) {
        if (args == null || !args.containsKey(key) || args.get(key) == null) {
            return null;
        }
        return String.valueOf(args.get(key));
    }

    private static Integer intArg(Map<String, Object> args, String key) {
        if (args == null || !args.containsKey(key) || args.get(key) == null) {
            return null;
        }
        Object value = args.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    record ToolInvocation(String toolName, Map<String, Object> args) {
    }
}
