package com.careermate.agent.controller;

import com.careermate.agent.dto.AgentMessageRequest;
import com.careermate.agent.service.AgentStreamService;
import com.careermate.security.CurrentUserContext;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/agent")
public class AgentStreamController {

    private final AgentStreamService agentStreamService;

    public AgentStreamController(AgentStreamService agentStreamService) {
        this.agentStreamService = agentStreamService;
    }

    @PostMapping("/sessions/{sessionId}/messages/stream")
    public SseEmitter stream(
            @PathVariable String sessionId,
            @Valid @RequestBody AgentMessageRequest request
    ) {
        Long userId = CurrentUserContext.getUserId();
        return agentStreamService.stream(userId, sessionId, request);
    }
}
