package com.careermate.agent.eval;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record AgentEvalReport(
        int totalCases,
        int passed,
        int failed,
        double toolAccuracy,
        Map<String, Double> toolAccuracyByDomain,
        List<AgentEvalCaseResult> caseResults,
        List<AgentEvalRagHit> ragHits,
        List<String> failedCaseIds
) {

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("totalCases", totalCases);
        map.put("passed", passed);
        map.put("failed", failed);
        map.put("tool_accuracy", toolAccuracy);
        map.put("tool_accuracy_by_domain", toolAccuracyByDomain);
        map.put("failedCaseIds", failedCaseIds);
        map.put("ragHits", ragHits.stream().map(AgentEvalRagHit::toMap).toList());
        map.put("cases", caseResults.stream().map(AgentEvalCaseResult::toMap).toList());
        return map;
    }

    public static AgentEvalReport fromResults(List<AgentEvalCase> cases, List<AgentEvalCaseResult> results) {
        int passed = (int) results.stream().filter(AgentEvalCaseResult::passed).count();
        int failed = results.size() - passed;

        int toolDenom = 0;
        int toolHits = 0;
        Map<String, int[]> domainStats = new LinkedHashMap<>();

        for (int i = 0; i < cases.size(); i++) {
            AgentEvalCase evalCase = cases.get(i);
            AgentEvalCaseResult result = results.get(i);
            if (!evalCase.safeExpectedTools().isEmpty()) {
                toolDenom++;
                if (result.toolsMatched()) {
                    toolHits++;
                }
                domainStats.computeIfAbsent(evalCase.domain(), key -> new int[2]);
                domainStats.get(evalCase.domain())[1]++;
                if (result.toolsMatched()) {
                    domainStats.get(evalCase.domain())[0]++;
                }
            }
        }

        double toolAccuracy = toolDenom == 0 ? 1D : (double) toolHits / toolDenom;
        Map<String, Double> toolAccuracyByDomain = new LinkedHashMap<>();
        for (Map.Entry<String, int[]> entry : domainStats.entrySet()) {
            int hits = entry.getValue()[0];
            int denom = entry.getValue()[1];
            toolAccuracyByDomain.put(entry.getKey(), denom == 0 ? 1D : (double) hits / denom);
        }

        List<AgentEvalRagHit> ragHits = results.stream()
                .map(AgentEvalCaseResult::ragHit)
                .filter(hit -> hit != null)
                .toList();

        List<String> failedCaseIds = results.stream()
                .filter(result -> !result.passed())
                .map(AgentEvalCaseResult::caseId)
                .toList();

        return new AgentEvalReport(
                results.size(),
                passed,
                failed,
                toolAccuracy,
                toolAccuracyByDomain,
                results,
                ragHits,
                failedCaseIds
        );
    }
}
