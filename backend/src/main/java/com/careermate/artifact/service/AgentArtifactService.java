package com.careermate.artifact.service;

import com.careermate.artifact.dto.AgentArtifactVO;
import com.careermate.artifact.dto.CreateAgentArtifactCommand;

import java.util.List;

public interface AgentArtifactService {

    AgentArtifactVO create(CreateAgentArtifactCommand command);

    List<AgentArtifactVO> listRecent(Long userId, int limit);

    List<AgentArtifactVO> list(Long userId, String artifactType, String sessionId, int limit);
}
