package com.careermate.mcp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.careermate.mapper.AgentSessionMapper;
import com.careermate.model.entity.AgentSessionEntity;
import com.careermate.resume.version.dto.ResumeVersionListItemVO;
import com.careermate.resume.version.service.ResumeVersionService;
import com.careermate.task.service.CareerTaskService;
import com.careermate.workspace.support.WorkspaceSessionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class McpResourceService {

    private final ResumeVersionService resumeVersionService;
    private final AgentSessionMapper agentSessionMapper;
    private final CareerTaskService careerTaskService;
    private final ObjectMapper objectMapper;

    public McpResourceService(
            ResumeVersionService resumeVersionService,
            AgentSessionMapper agentSessionMapper,
            CareerTaskService careerTaskService,
            ObjectMapper objectMapper
    ) {
        this.resumeVersionService = resumeVersionService;
        this.agentSessionMapper = agentSessionMapper;
        this.careerTaskService = careerTaskService;
        this.objectMapper = objectMapper;
    }

    public JsonNode listResources() {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode resources = root.putArray("resources");
        resources.add(resourceDescriptor(
                McpConstants.URI_RESUME_VERSIONS,
                "Resume Versions",
                "Current user's resume version summaries",
                "application/json"
        ));
        resources.add(resourceDescriptor(
                McpConstants.URI_WORKSPACE_JD,
                "JD Workspaces",
                "Current user's JD preparation workspace summaries",
                "application/json"
        ));
        resources.add(resourceDescriptor(
                McpConstants.URI_TASKS_TODO,
                "Todo Tasks",
                "Current user's pending career tasks",
                "application/json"
        ));
        return root;
    }

    public JsonNode readResource(Long userId, String uri) {
        if (uri == null || uri.isBlank()) {
            throw new McpInvalidParamsException("uri is required");
        }
        String text = switch (uri) {
            case McpConstants.URI_RESUME_VERSIONS -> writeJson(buildResumeVersions(userId));
            case McpConstants.URI_WORKSPACE_JD -> writeJson(buildJdWorkspaces(userId));
            case McpConstants.URI_TASKS_TODO -> writeJson(buildTodoTasks(userId));
            default -> throw new McpInvalidParamsException("Unknown resource uri: " + uri);
        };

        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode contents = root.putArray("contents");
        ObjectNode item = contents.addObject();
        item.put("uri", uri);
        item.put("mimeType", "application/json");
        item.put("text", text);
        return root;
    }

    private List<Map<String, Object>> buildResumeVersions(Long userId) {
        List<ResumeVersionListItemVO> versions = resumeVersionService.listBySession(userId, null);
        return versions.stream()
                .map(v -> Map.<String, Object>of(
                        "versionId", v.versionId(),
                        "versionName", v.versionName(),
                        "targetJdLabel", v.targetJdLabel() != null ? v.targetJdLabel() : "",
                        "createdAt", v.createdAt() != null ? v.createdAt().toString() : ""
                ))
                .toList();
    }

    private List<Map<String, Object>> buildJdWorkspaces(Long userId) {
        List<AgentSessionEntity> sessions = agentSessionMapper.selectList(
                new LambdaQueryWrapper<AgentSessionEntity>()
                        .eq(AgentSessionEntity::getUserId, userId)
                        .eq(AgentSessionEntity::getWorkspaceType, WorkspaceSessionRepository.WORKSPACE_JD_PREP)
                        .orderByDesc(AgentSessionEntity::getUpdatedAt)
                        .last("LIMIT " + McpConstants.MAX_JD_WORKSPACES)
        );
        return sessions.stream()
                .map(session -> {
                    Map<String, Object> item = new java.util.LinkedHashMap<>();
                    item.put("sessionId", session.getSessionId());
                    item.put("jdId", session.getJdId() != null ? session.getJdId() : "");
                    item.put("title", session.getTitle() != null ? session.getTitle() : "");
                    item.put("goalText", session.getGoalText() != null ? session.getGoalText() : "");
                    item.put("updatedAt", session.getUpdatedAt() != null ? session.getUpdatedAt().toString() : "");
                    item.put("jdSummary", parseJdSnapshotSummary(session.getJdSnapshot()));
                    return item;
                })
                .toList();
    }

    private List<Map<String, Object>> buildTodoTasks(Long userId) {
        return careerTaskService.listAgentTodoTasksForUser(userId);
    }

    private String parseJdSnapshotSummary(String jdSnapshot) {
        if (jdSnapshot == null || jdSnapshot.isBlank()) {
            return "";
        }
        try {
            JsonNode node = objectMapper.readTree(jdSnapshot);
            String company = textOrEmpty(node.get("company"));
            String title = textOrEmpty(node.get("title"));
            if (!company.isEmpty() && !title.isEmpty()) {
                return company + " · " + title;
            }
            return !title.isEmpty() ? title : company;
        } catch (Exception ignored) {
            return "";
        }
    }

    private String textOrEmpty(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        return node.asText("").trim();
    }

    private ObjectNode resourceDescriptor(String uri, String name, String description, String mimeType) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("uri", uri);
        node.put("name", name);
        node.put("description", description);
        node.put("mimeType", mimeType);
        return node;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize MCP resource", e);
        }
    }
}
