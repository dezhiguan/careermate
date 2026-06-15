package com.careermate.workspace.service.impl;

import com.careermate.model.entity.AgentMessageEntity;
import com.careermate.model.entity.AgentSessionEntity;
import com.careermate.resume.version.dto.ResumeVersionListItemVO;
import com.careermate.resume.version.service.ResumeVersionService;
import com.careermate.workspace.dto.ActionAckResponse;
import com.careermate.workspace.dto.MessageVO;
import com.careermate.workspace.dto.WorkspaceVO;
import com.careermate.workspace.service.WorkspaceService;
import com.careermate.workspace.support.WorkspaceSessionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class WorkspaceServiceImpl implements WorkspaceService {

    private static final int DEFAULT_MESSAGE_LIMIT = 50;

    private final WorkspaceSessionRepository workspaceSessionRepository;
    private final ResumeVersionService resumeVersionService;
    private final ObjectMapper objectMapper;

    public WorkspaceServiceImpl(
            WorkspaceSessionRepository workspaceSessionRepository,
            ResumeVersionService resumeVersionService,
            ObjectMapper objectMapper
    ) {
        this.workspaceSessionRepository = workspaceSessionRepository;
        this.resumeVersionService = resumeVersionService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public WorkspaceVO getWorkspace(Long userId, String sessionId) {
        AgentSessionEntity session = workspaceSessionRepository.requireSession(userId, sessionId);
        Map<String, Object> snapshot = parseJsonMap(session.getJdSnapshot());
        Map<String, Object> workspaceMetadata = parseJsonMap(session.getWorkspaceMetadata());
        List<ResumeVersionListItemVO> versions = resumeVersionService.listBySession(userId, sessionId);
        List<WorkspaceVO.ResumeVersionBriefVO> briefs = versions.stream()
                .map(v -> new WorkspaceVO.ResumeVersionBriefVO(
                        v.versionId(),
                        v.versionName(),
                        v.createdAt()
                ))
                .toList();
        String workspaceType = WorkspaceSessionRepository.displayWorkspaceType(session.getWorkspaceType());
        String goalText = resolveGoalText(session);
        List<String> contextChips = buildContextChips(workspaceType, snapshot, versions);
        String contextSummary = buildContextSummary(contextChips);

        return new WorkspaceVO(
                session.getSessionId(),
                workspaceType,
                session.getTitle(),
                session.getJdId(),
                snapshot,
                session.getCreatedAt(),
                session.getUpdatedAt(),
                briefs,
                goalText,
                workspaceMetadata,
                contextSummary,
                contextChips
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<MessageVO> getMessages(Long userId, String sessionId, Integer after, int limit) {
        AgentSessionEntity session = workspaceSessionRepository.requireSession(userId, sessionId);
        int safeLimit = limit <= 0 ? DEFAULT_MESSAGE_LIMIT : limit;
        List<AgentMessageEntity> messages = workspaceSessionRepository.listMessages(
                session.getId(), userId, after, safeLimit
        );
        return messages.stream().map(this::toMessageVO).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ActionAckResponse handleAction(Long userId, String sessionId, String action, String payload) {
        workspaceSessionRepository.requireSession(userId, sessionId);
        if (action == null || action.isBlank()) {
            return ActionAckResponse.asNoop();
        }
        return switch (action) {
            case "GENERATE_RESUME", "RETRY" -> ActionAckResponse.withSse(
                    "/api/workspace/" + sessionId + "/generate-resume/stream"
                            + (payload != null && !payload.isBlank() ? "?jdId=" + payload : "")
            );
            case "VIEW_JD", "NAVIGATE", "VIEW_RESUME", "COPY_MARKDOWN" -> ActionAckResponse.asNoop();
            default -> ActionAckResponse.asNoop();
        };
    }

    @Override
    @Transactional(readOnly = true)
    public AgentSessionEntity requireOwnedSession(Long userId, String sessionId) {
        return workspaceSessionRepository.requireSession(userId, sessionId);
    }

    private MessageVO toMessageVO(AgentMessageEntity entity) {
        return new MessageVO(
                entity.getId(),
                entity.getSequenceNo(),
                entity.getRole(),
                entity.getContent(),
                entity.getMessageType(),
                parseJsonMap(entity.getMetadata()),
                entity.getCreatedAt()
        );
    }

    private String resolveGoalText(AgentSessionEntity session) {
        if (session.getGoalText() != null && !session.getGoalText().isBlank()) {
            return session.getGoalText().trim();
        }
        if (session.getTitle() != null && !session.getTitle().isBlank()) {
            return session.getTitle().trim();
        }
        return null;
    }

    private List<String> buildContextChips(
            String workspaceType,
            Map<String, Object> jdSnapshot,
            List<ResumeVersionListItemVO> versions
    ) {
        return switch (workspaceType) {
            case WorkspaceSessionRepository.WORKSPACE_JD_PREP -> {
                List<String> chips = new ArrayList<>();
                chips.add("JD 已加载");
                if (!versions.isEmpty()) {
                    chips.add("简历版本 " + versions.size());
                }
                yield chips;
            }
            case WorkspaceSessionRepository.WORKSPACE_INTERVIEW -> List.of("面试训练");
            case WorkspaceSessionRepository.WORKSPACE_MARKET -> List.of("市场行情");
            case WorkspaceSessionRepository.WORKSPACE_RESUME -> List.of("简历优化");
            default -> List.of("普通对话");
        };
    }

    private String buildContextSummary(List<String> contextChips) {
        if (contextChips == null || contextChips.isEmpty()) {
            return null;
        }
        return contextChips.stream()
                .filter(chip -> chip != null && !chip.isBlank())
                .collect(Collectors.joining(" · "));
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("parse workspace json failed: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }
}
