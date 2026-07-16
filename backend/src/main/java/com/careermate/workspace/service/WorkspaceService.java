package com.careermate.workspace.service;

import com.careermate.model.entity.AgentSessionEntity;
import com.careermate.workspace.dto.ActionAckResponse;
import com.careermate.workspace.dto.MessageVO;
import com.careermate.workspace.dto.RecentLineVO;
import com.careermate.workspace.dto.WorkspaceCreateRequest;
import com.careermate.workspace.dto.WorkspaceCreateResponse;
import com.careermate.workspace.dto.WorkspaceVO;

import java.util.List;

public interface WorkspaceService {

    WorkspaceVO getWorkspace(Long userId, String sessionId);

    /** 最近在推进的 JD 会话线，供"继续会话线"一键返回。 */
    List<RecentLineVO> listRecentLines(Long userId, int limit);

    List<MessageVO> getMessages(Long userId, String sessionId, Integer after, int limit);

    ActionAckResponse handleAction(Long userId, String sessionId, String action, String payload);

    AgentSessionEntity requireOwnedSession(Long userId, String sessionId);

    WorkspaceCreateResponse createWorkspace(Long userId, WorkspaceCreateRequest request);
}
