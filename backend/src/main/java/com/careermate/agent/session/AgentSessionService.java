package com.careermate.agent.session;

import com.careermate.agent.dto.AgentSessionCreateResponse;
import com.careermate.agent.dto.AgentSessionListItemResponse;
import com.careermate.agent.dto.AgentSessionResponse;
import com.careermate.agent.dto.AgentTraceResponse;
import com.careermate.model.entity.AgentMessageEntity;

import java.util.List;

public interface AgentSessionService {

    AgentSessionCreateResponse createSession(Long userId);

    AgentSessionResponse getSession(Long userId, String sessionId);

    List<AgentSessionListItemResponse> listRecentSessions(Long userId, int limit);

    List<AgentTraceResponse> getTrace(Long userId, String sessionId);

    AgentMessageEntity appendMessage(Long userId, String sessionId, String role, String content, String messageType);

    void recordTrace(Long userId, String sessionId, String toolName, String requestSummary, String responseSummary,
                     String status, Long latencyMs, String errorCode);

    void markCompleted(Long userId, String sessionId, Long totalLatencyMs);

    void markError(Long userId, String sessionId, String errorCode);
}
