package com.careermate.agent.eval;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record AgentEvalRagHit(
        String caseId,
        String tool,
        String scene,
        String kbId,
        Integer topK,
        Integer chunkCount,
        String status
) {

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("caseId", caseId);
        map.put("tool", tool);
        map.put("scene", scene);
        map.put("kbId", kbId);
        map.put("topK", topK);
        map.put("chunkCount", chunkCount);
        map.put("status", status);
        return map;
    }

    public static AgentEvalRagHit noHit(String caseId, String reason) {
        return new AgentEvalRagHit(caseId, null, null, null, null, 0, reason);
    }
}
