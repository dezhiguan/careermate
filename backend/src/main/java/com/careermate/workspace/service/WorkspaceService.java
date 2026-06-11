package com.careermate.workspace.service;

import com.careermate.model.entity.AgentSessionEntity;
import com.careermate.workspace.dto.ActionAckResponse;
import com.careermate.workspace.dto.MessageVO;
import com.careermate.workspace.dto.WorkspaceVO;

import java.util.List;

public interface WorkspaceService {

    WorkspaceVO getWorkspace(Long userId, String sessionId);

    List<MessageVO> getMessages(Long userId, String sessionId, Integer after, int limit);

    ActionAckResponse handleAction(Long userId, String sessionId, String action, String payload);

    AgentSessionEntity requireOwnedSession(Long userId, String sessionId);
}
