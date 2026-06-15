package com.careermate.workspace.service.impl;

import com.careermate.common.exception.BizException;
import com.careermate.model.entity.AgentMessageEntity;
import com.careermate.model.entity.AgentSessionEntity;
import com.careermate.resume.version.dto.ResumeVersionListItemVO;
import com.careermate.resume.version.service.ResumeVersionService;
import com.careermate.workspace.dto.ActionAckResponse;
import com.careermate.workspace.dto.MessageVO;
import com.careermate.workspace.dto.WorkspaceCreateRequest;
import com.careermate.workspace.dto.WorkspaceCreateResponse;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class WorkspaceServiceImpl implements WorkspaceService {

    private static final int DEFAULT_MESSAGE_LIMIT = 50;

    private static final Set<String> ALLOWED_WORKSPACE_TYPES = Set.of(
            WorkspaceSessionRepository.WORKSPACE_JD_PREP,
            WorkspaceSessionRepository.WORKSPACE_INTERVIEW,
            WorkspaceSessionRepository.WORKSPACE_MARKET,
            WorkspaceSessionRepository.WORKSPACE_RESUME,
            WorkspaceSessionRepository.WORKSPACE_GENERAL
    );

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
        List<String> contextChips = buildContextChips(workspaceType, snapshot, workspaceMetadata, versions);
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

    @Override
    @Transactional
    public WorkspaceCreateResponse createWorkspace(Long userId, WorkspaceCreateRequest request) {
        if (userId == null) {
            throw new BizException(401, "未认证");
        }
        if (request == null || request.workspaceType() == null || request.workspaceType().isBlank()) {
            throw new BizException(400, "workspaceType 不能为空");
        }
        String normalizedType = WorkspaceSessionRepository.normalizeWorkspaceType(request.workspaceType());
        if (!ALLOWED_WORKSPACE_TYPES.contains(normalizedType)) {
            throw new BizException(400, "不支持的工作空间类型: " + request.workspaceType());
        }

        String title = resolveCreateTitle(request, normalizedType);
        String goalText = request.goalText();
        String entryAction = request.entryAction() == null ? "" : request.entryAction().trim();
        Map<String, Object> contextMetadata = request.contextMetadata() == null
                ? Collections.emptyMap()
                : request.contextMetadata();

        AgentSessionEntity session = workspaceSessionRepository.createWorkspace(
                userId, normalizedType, title, goalText, contextMetadata
        );

        WelcomeCard welcomeCard = buildWelcomeCard(
                normalizedType, entryAction, title, contextMetadata
        );
        String welcomeMetadata = serializeWelcomeMetadata(welcomeCard);
        workspaceSessionRepository.appendMessage(
                userId,
                session,
                "assistant",
                welcomeCard.content(),
                "CARD",
                welcomeMetadata,
                1
        );

        String redirectPath = "/chat/" + session.getSessionId();
        return new WorkspaceCreateResponse(session.getSessionId(), redirectPath, normalizedType);
    }

    private String resolveCreateTitle(WorkspaceCreateRequest request, String normalizedType) {
        if (request.title() != null && !request.title().isBlank()) {
            return request.title().trim();
        }
        return switch (normalizedType) {
            case WorkspaceSessionRepository.WORKSPACE_JD_PREP -> "JD 准备空间";
            case WorkspaceSessionRepository.WORKSPACE_INTERVIEW -> "面试训练空间";
            case WorkspaceSessionRepository.WORKSPACE_MARKET -> "市场策略空间";
            case WorkspaceSessionRepository.WORKSPACE_RESUME -> "简历优化空间";
            default -> "通用对话";
        };
    }

    private WelcomeCard buildWelcomeCard(
            String workspaceType,
            String entryAction,
            String title,
            Map<String, Object> contextMetadata
    ) {
        String summary = buildWelcomeSummary(workspaceType, entryAction, contextMetadata);
        String content = buildWelcomeContent(workspaceType, entryAction, title, summary);
        List<Map<String, Object>> actions = buildWelcomeActions(workspaceType, entryAction, contextMetadata);
        return new WelcomeCard(workspaceType, entryAction, title, content, summary, actions);
    }

    private String buildWelcomeContent(
            String workspaceType,
            String entryAction,
            String title,
            String summary
    ) {
        String actionLabel = entryActionLabel(entryAction);
        if (!actionLabel.isBlank()) {
            return "已为你准备好「" + title + "」上下文。\n"
                    + "当前动作：" + actionLabel + "。\n"
                    + summary;
        }
        return "已为你准备好「" + title + "」上下文。\n" + summary;
    }

    private String buildWelcomeSummary(
            String workspaceType,
            String entryAction,
            Map<String, Object> contextMetadata
    ) {
        return switch (workspaceType) {
            case WorkspaceSessionRepository.WORKSPACE_JD_PREP -> {
                String company = stringMeta(contextMetadata, "company", "未知公司");
                String jobTitle = stringMeta(contextMetadata, "title", "岗位");
                yield "目标岗位：" + company + " · " + jobTitle;
            }
            case WorkspaceSessionRepository.WORKSPACE_INTERVIEW -> {
                String question = truncate(stringMeta(contextMetadata, "questionText", "面试题"), 60);
                yield "题目：" + question;
            }
            case WorkspaceSessionRepository.WORKSPACE_MARKET -> {
                String city = stringMeta(contextMetadata, "city", "");
                String role = stringMeta(contextMetadata, "role", "");
                String years = stringMeta(contextMetadata, "years", "");
                String parts = List.of(city, role, years).stream()
                        .filter(s -> !s.isBlank())
                        .collect(Collectors.joining(" · "));
                yield parts.isBlank() ? "已加载市场行情上下文" : "筛选：" + parts;
            }
            case WorkspaceSessionRepository.WORKSPACE_RESUME -> {
                String artifactTitle = stringMeta(contextMetadata, "title", titleFromMetadata(contextMetadata));
                yield "资产：" + artifactTitle;
            }
            default -> "随时可以开始对话";
        };
    }

    private List<Map<String, Object>> buildWelcomeActions(
            String workspaceType,
            String entryAction,
            Map<String, Object> contextMetadata
    ) {
        return switch (workspaceType) {
            case WorkspaceSessionRepository.WORKSPACE_JD_PREP -> {
                List<Map<String, Object>> actions = new ArrayList<>();
                String jdId = stringMeta(contextMetadata, "jdId", "");
                if ("GENERATE_RESUME".equals(entryAction) || entryAction.isBlank()) {
                    actions.add(Map.of(
                            "label", "生成定制简历",
                            "action", "GENERATE_RESUME",
                            "payload", jdId
                    ));
                }
                if (!jdId.isBlank()) {
                    actions.add(Map.of(
                            "label", "查看 JD",
                            "action", "VIEW_JD",
                            "payload", jdId
                    ));
                }
                yield actions;
            }
            case WorkspaceSessionRepository.WORKSPACE_INTERVIEW -> List.of(
                    Map.of("label", "让小职讲解", "action", "NAVIGATE", "payload", "EXPLAIN_QUESTION"),
                    Map.of("label", "生成追问", "action", "NAVIGATE", "payload", "FOLLOW_UP"),
                    Map.of("label", "生成补强任务", "action", "NAVIGATE", "payload", "CREATE_STRENGTHEN_TASK")
            );
            case WorkspaceSessionRepository.WORKSPACE_MARKET -> List.of(
                    Map.of("label", "解读行情", "action", "NAVIGATE", "payload", "EXPLAIN_MARKET"),
                    Map.of("label", "生成谈薪脚本", "action", "NAVIGATE", "payload", "NEGOTIATION_SCRIPT")
            );
            case WorkspaceSessionRepository.WORKSPACE_RESUME -> List.of(
                    Map.of("label", "用小职继续优化", "action", "NAVIGATE", "payload", "CONTINUE_WITH_ASSET")
            );
            default -> List.of();
        };
    }

    private String serializeWelcomeMetadata(WelcomeCard welcomeCard) {
        try {
            Map<String, Object> card = new LinkedHashMap<>();
            card.put("type", "WORKSPACE_CONTEXT_READY");
            card.put("workspaceType", welcomeCard.workspaceType());
            card.put("entryAction", welcomeCard.entryAction());
            card.put("title", welcomeCard.title());
            card.put("summary", welcomeCard.summary());
            card.put("actions", welcomeCard.actions());
            return objectMapper.writeValueAsString(Map.of("card", card));
        } catch (Exception e) {
            log.warn("serialize welcome metadata failed: {}", e.getMessage());
            return "{}";
        }
    }

    private String entryActionLabel(String entryAction) {
        if (entryAction == null || entryAction.isBlank()) {
            return "";
        }
        return switch (entryAction) {
            case "ANALYZE_JD" -> "分析 JD";
            case "GENERATE_RESUME" -> "定制简历";
            case "PREPARE_INTERVIEW" -> "准备面试";
            case "EXPLAIN_QUESTION" -> "讲解本题";
            case "FOLLOW_UP" -> "生成追问";
            case "CREATE_STRENGTHEN_TASK" -> "生成补强任务";
            case "EXPLAIN_MARKET" -> "解读行情";
            case "NEGOTIATION_SCRIPT" -> "生成谈薪脚本";
            case "CONTINUE_WITH_ASSET" -> "继续优化资产";
            default -> entryAction;
        };
    }

    private static String stringMeta(Map<String, Object> metadata, String key, String fallback) {
        if (metadata == null) {
            return fallback;
        }
        Object value = metadata.get(key);
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? fallback : text;
    }

    private static String titleFromMetadata(Map<String, Object> metadata) {
        String artifactType = stringMeta(metadata, "artifactType", "产物");
        return artifactType;
    }

    private static String truncate(String text, int maxLen) {
        if (text == null || text.length() <= maxLen) {
            return text == null ? "" : text;
        }
        return text.substring(0, maxLen) + "…";
    }

    private record WelcomeCard(
            String workspaceType,
            String entryAction,
            String title,
            String content,
            String summary,
            List<Map<String, Object>> actions
    ) {
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
            Map<String, Object> workspaceMetadata,
            List<ResumeVersionListItemVO> versions
    ) {
        return switch (workspaceType) {
            case WorkspaceSessionRepository.WORKSPACE_JD_PREP -> {
                List<String> chips = new ArrayList<>();
                String company = stringMeta(jdSnapshot, "company", "");
                String title = stringMeta(jdSnapshot, "title", "");
                if (!company.isBlank() || !title.isBlank()) {
                    chips.add("📋 " + String.join(" ", List.of(company, title).stream()
                            .filter(s -> !s.isBlank()).toList()));
                } else {
                    chips.add("JD 已加载");
                }
                if (!versions.isEmpty()) {
                    chips.add("简历版本 " + versions.size());
                }
                yield chips;
            }
            case WorkspaceSessionRepository.WORKSPACE_INTERVIEW -> {
                List<String> chips = new ArrayList<>();
                chips.add("面试训练");
                String question = truncate(stringMeta(workspaceMetadata, "questionText", ""), 24);
                if (!question.isBlank()) {
                    chips.add("题目已加载");
                }
                yield chips;
            }
            case WorkspaceSessionRepository.WORKSPACE_MARKET -> {
                List<String> chips = new ArrayList<>();
                chips.add("市场行情");
                String city = stringMeta(workspaceMetadata, "city", "");
                String role = stringMeta(workspaceMetadata, "role", "");
                if (!city.isBlank()) {
                    chips.add(city);
                }
                if (!role.isBlank()) {
                    chips.add(role);
                }
                yield chips;
            }
            case WorkspaceSessionRepository.WORKSPACE_RESUME -> {
                List<String> chips = new ArrayList<>();
                chips.add("简历优化");
                String artifactTitle = stringMeta(workspaceMetadata, "title", "");
                if (!artifactTitle.isBlank()) {
                    chips.add(artifactTitle);
                }
                yield chips;
            }
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
