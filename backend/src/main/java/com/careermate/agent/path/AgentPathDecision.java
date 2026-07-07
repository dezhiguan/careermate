package com.careermate.agent.path;

/**
 * A2：路径分层判定结果。
 *
 * @param mode   FAST / DEEP
 * @param reason 判定依据（埋点/展示用，如 user_explicit / deep_resume / deep_interview / default_fast）
 */
public record AgentPathDecision(AgentPathMode mode, String reason) {

    public boolean isDeep() {
        return mode == AgentPathMode.DEEP;
    }
}
