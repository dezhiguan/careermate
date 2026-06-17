package com.careermate.agent.controller;

import com.careermate.agent.dto.AgentSessionCreateResponse;
import com.careermate.agent.dto.AgentSessionListItemResponse;
import com.careermate.agent.dto.AgentSessionResponse;
import com.careermate.agent.dto.AgentTraceResponse;
import com.careermate.agent.session.AgentSessionService;
import com.careermate.common.api.ApiResponse;
import com.careermate.security.CurrentUserContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/agent")
public class AgentSessionController {

    private final AgentSessionService agentSessionService;

    public AgentSessionController(AgentSessionService agentSessionService) {
        this.agentSessionService = agentSessionService;
    }

    @PostMapping("/sessions")
    public ApiResponse<AgentSessionCreateResponse> createSession() {
        Long userId = CurrentUserContext.getUserId();
        return ApiResponse.success(agentSessionService.createSession(userId));
    }

    @GetMapping("/sessions")
    public ApiResponse<List<AgentSessionListItemResponse>> listSessions(
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false, defaultValue = "20") Integer limit
    ) {
        Long userId = CurrentUserContext.getUserId();
        return ApiResponse.success(agentSessionService.listRecentSessions(userId, limit == null ? 20 : limit, taskType));
    }

    @GetMapping("/sessions/{sessionId}")
    public ApiResponse<AgentSessionResponse> getSession(@PathVariable String sessionId) {
        Long userId = CurrentUserContext.getUserId();
        return ApiResponse.success(agentSessionService.getSession(userId, sessionId));
    }

    @GetMapping("/sessions/{sessionId}/trace")
    public ApiResponse<List<AgentTraceResponse>> getTrace(@PathVariable String sessionId) {
        Long userId = CurrentUserContext.getUserId();
        return ApiResponse.success(agentSessionService.getTrace(userId, sessionId));
    }
}
