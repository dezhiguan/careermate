package com.careermate.agent.eval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentEvalCase(
        String caseId,
        String domain,
        String input,
        String expectedIntent,
        List<String> expectedDomains,
        List<String> expectedTools,
        List<String> forbiddenPhrases,
        List<String> mustContain,
        Boolean expectBlocked,
        Boolean requiresCritic,
        Boolean useLegacyFallback,
        Boolean allowExtraTools,
        String evalPath,
        AgentEvalExpectedRag expectedRag
) {

    public boolean extraToolsAllowed() {
        return Boolean.TRUE.equals(allowExtraTools);
    }

    public boolean blockedExpected() {
        return Boolean.TRUE.equals(expectBlocked);
    }

    public boolean criticRequired() {
        return Boolean.TRUE.equals(requiresCritic);
    }

    public boolean legacyFallbackExpected() {
        return Boolean.TRUE.equals(useLegacyFallback);
    }

    public String evalPathOrDefault() {
        return evalPath == null || evalPath.isBlank() ? "supervisor" : evalPath.trim();
    }

    public List<String> safeExpectedDomains() {
        return expectedDomains == null ? List.of() : expectedDomains;
    }

    public List<String> safeExpectedTools() {
        return expectedTools == null ? List.of() : expectedTools;
    }

    public List<String> safeForbiddenPhrases() {
        return forbiddenPhrases == null ? List.of() : forbiddenPhrases;
    }

    public List<String> safeMustContain() {
        return mustContain == null ? List.of() : mustContain;
    }
}
