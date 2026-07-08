package com.careermate.agent.debate;

/**
 * B1：debate 结果。仅暴露最终稿与收敛信息，不含 critic 评分细节（内部）。
 *
 * @param sessionId    协作会话 id
 * @param finalDraft   最终稿
 * @param rounds       评审轮数
 * @param status       CONSENSUS / MAX_ROUND / ABORTED
 * @param lastSuggestion 末轮 critic 建议（用于 change_summary 等展示）
 */
public record DebateResult(
        String sessionId,
        String finalDraft,
        int rounds,
        String status,
        String lastSuggestion
) {
    public boolean reachedConsensus() {
        return "CONSENSUS".equals(status);
    }
}
