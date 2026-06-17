package com.careermate.agent.eval;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record AgentEvalCaseResult(
        String caseId,
        String domain,
        boolean passed,
        List<String> failures,
        String routeReasonCode,
        List<String> routedDomains,
        boolean criticRequired,
        boolean blocked,
        List<String> selectedTools,
        boolean toolsMatched,
        AgentEvalRagHit ragHit
) {

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("caseId", caseId);
        map.put("domain", domain);
        map.put("passed", passed);
        map.put("failures", failures);
        map.put("routeReasonCode", routeReasonCode);
        map.put("routedDomains", routedDomains);
        map.put("criticRequired", criticRequired);
        map.put("blocked", blocked);
        map.put("selectedTools", selectedTools);
        map.put("toolsMatched", toolsMatched);
        if (ragHit != null) {
            map.put("ragHit", ragHit.toMap());
        }
        return map;
    }
}
