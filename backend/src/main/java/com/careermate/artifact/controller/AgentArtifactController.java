package com.careermate.artifact.controller;

import com.careermate.artifact.dto.AgentArtifactVO;
import com.careermate.artifact.service.AgentArtifactService;
import com.careermate.common.api.ApiResponse;
import com.careermate.security.CurrentUserContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/artifacts")
public class AgentArtifactController {

    private final AgentArtifactService agentArtifactService;

    public AgentArtifactController(AgentArtifactService agentArtifactService) {
        this.agentArtifactService = agentArtifactService;
    }

    @GetMapping("/recent")
    public ApiResponse<List<AgentArtifactVO>> recent(
            @RequestParam(defaultValue = "10") int limit
    ) {
        Long userId = CurrentUserContext.getUserId();
        return ApiResponse.success(agentArtifactService.listRecent(userId, limit));
    }

    @GetMapping
    public ApiResponse<List<AgentArtifactVO>> list(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String sessionId,
            @RequestParam(defaultValue = "20") int limit
    ) {
        Long userId = CurrentUserContext.getUserId();
        return ApiResponse.success(agentArtifactService.list(userId, type, sessionId, limit));
    }
}
