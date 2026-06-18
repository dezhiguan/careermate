package com.careermate.agent.controller;

import com.careermate.agent.dto.AgentMessageRequest;
import com.careermate.agent.service.AgentStreamService;
import com.careermate.agent.sse.AgentTaskRegistry;
import com.careermate.common.api.ApiResponse;
import com.careermate.security.CurrentUserContext;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
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
    private final AgentTaskRegistry taskRegistry;

    public AgentStreamController(AgentStreamService agentStreamService, AgentTaskRegistry taskRegistry) {
        this.agentStreamService = agentStreamService;
        this.taskRegistry = taskRegistry;
    }

    @PostMapping("/sessions/{sessionId}/messages/stream")
    public SseEmitter stream(
            @PathVariable String sessionId,
            @Valid @RequestBody AgentMessageRequest request
    ) {
        Long userId = CurrentUserContext.getUserId();
        return agentStreamService.stream(userId, sessionId, request);
    }

    /**
     * U3 断点续传：客户端切走 tab / 网断 / 刷新页面后，回来通过此接口判断当前 session 是否还有
     * server-side 任务在跑；若 running=true，前端轮询 {@code GET /api/agent/sessions/{id}} 直到
     * 新的 agent 消息落库。
     */
    @GetMapping("/sessions/{sessionId}/stream/status")
    public ApiResponse<Map<String, Object>> streamStatus(@PathVariable String sessionId) {
        boolean running = taskRegistry.isRunning(sessionId);
        return ApiResponse.success(Map.of("running", running));
    }
}
