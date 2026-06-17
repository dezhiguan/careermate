package com.careermate.agent.eval;

import java.util.ArrayList;
import java.util.List;

final class AgentEvalCaseValidator {

    private AgentEvalCaseValidator() {
    }

    static void validateAll(List<AgentEvalCase> cases) {
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < cases.size(); i++) {
            AgentEvalCase evalCase = cases.get(i);
            String prefix = "case[" + i + "]"
                    + (isBlank(evalCase.caseId()) ? "" : " " + evalCase.caseId());
            validateCase(evalCase, prefix, errors);
        }
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Invalid agent-eval cases:\n- " + String.join("\n- ", errors));
        }
    }

    private static void validateCase(AgentEvalCase evalCase, String prefix, List<String> errors) {
        requireNonBlank(evalCase.caseId(), prefix, "caseId", errors);
        requireNonBlank(evalCase.domain(), prefix, "domain", errors);
        requireNonBlank(evalCase.input(), prefix, "input", errors);
        requireNonBlank(evalCase.expectedIntent(), prefix, "expectedIntent", errors);
        requirePresent(evalCase.expectedTools(), prefix, "expectedTools", errors);
        requirePresent(evalCase.forbiddenPhrases(), prefix, "forbiddenPhrases", errors);
        requirePresent(evalCase.mustContain(), prefix, "mustContain", errors);
    }

    private static void requireNonBlank(String value, String prefix, String field, List<String> errors) {
        if (isBlank(value)) {
            errors.add(prefix + ": " + field + " must be non-empty");
        }
    }

    private static void requirePresent(List<?> value, String prefix, String field, List<String> errors) {
        if (value == null) {
            errors.add(prefix + ": " + field + " must be present (use [] when empty)");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
