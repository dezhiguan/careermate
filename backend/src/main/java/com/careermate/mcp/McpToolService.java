package com.careermate.mcp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.careermate.agent.tool.rag.RagRetrieveRequest;
import com.careermate.agent.tool.rag.RagRetrieveResult;
import com.careermate.agent.tool.rag.RagRetrieveScene;
import com.careermate.agent.tool.rag.RagRetrievedChunk;
import com.careermate.common.exception.BizException;
import com.careermate.knowledge.KnowledgeRetrievalService;
import com.careermate.mapper.ResumeMapper;
import com.careermate.mapper.ResumeVersionMapper;
import com.careermate.model.entity.ResumeEntity;
import com.careermate.model.entity.ResumeVersionEntity;
import com.careermate.resume.service.ResumeService;
import com.careermate.resume.version.dto.ResumeVersionVO;
import com.careermate.resume.version.service.ResumeVersionService;
import com.careermate.task.CareerTaskConstants;
import com.careermate.task.dto.CareerTaskCreateRequest;
import com.careermate.task.dto.CareerTaskResponse;
import com.careermate.task.service.CareerTaskService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class McpToolService {

    private final KnowledgeRetrievalService knowledgeRetrievalService;
    private final ResumeVersionService resumeVersionService;
    private final ResumeVersionMapper resumeVersionMapper;
    private final ResumeMapper resumeMapper;
    private final CareerTaskService careerTaskService;
    private final McpAuditService mcpAuditService;
    private final ObjectMapper objectMapper;

    public McpToolService(
            KnowledgeRetrievalService knowledgeRetrievalService,
            ResumeVersionService resumeVersionService,
            ResumeVersionMapper resumeVersionMapper,
            ResumeMapper resumeMapper,
            CareerTaskService careerTaskService,
            McpAuditService mcpAuditService,
            ObjectMapper objectMapper
    ) {
        this.knowledgeRetrievalService = knowledgeRetrievalService;
        this.resumeVersionService = resumeVersionService;
        this.resumeVersionMapper = resumeVersionMapper;
        this.resumeMapper = resumeMapper;
        this.careerTaskService = careerTaskService;
        this.mcpAuditService = mcpAuditService;
        this.objectMapper = objectMapper;
    }

    public JsonNode listTools() {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode tools = root.putArray("tools");
        tools.add(toolDescriptor(
                McpConstants.TOOL_SEARCH_JD,
                "Search JD knowledge base and return structured summaries",
                searchJdSchema(),
                readOnlyAnnotations()
        ));
        tools.add(toolDescriptor(
                McpConstants.TOOL_GET_RESUME,
                "Get current user's resume or resume version summary; set includeContent=true for full body",
                getResumeSchema(),
                readOnlyAnnotations()
        ));
        tools.add(toolDescriptor(
                McpConstants.TOOL_CREATE_TASK,
                "Create a career task for the current user (write=true, destructive=false)",
                createTaskSchema(),
                writeAnnotations()
        ));
        return root;
    }

    public JsonNode callTool(Long userId, String name, JsonNode arguments) {
        McpArgumentSupport.rejectClientUserId(arguments);
        try {
            return switch (name) {
                case McpConstants.TOOL_SEARCH_JD -> toolResult(searchJd(userId, arguments), false);
                case McpConstants.TOOL_GET_RESUME -> toolResult(getResume(userId, arguments), false);
                case McpConstants.TOOL_CREATE_TASK -> callCreateTask(userId, arguments);
                default -> throw new McpInvalidParamsException("Unknown tool: " + name);
            };
        } catch (McpForbiddenException e) {
            return toolResult(Map.of("error", "forbidden"), true);
        } catch (BizException e) {
            return toolResult(Map.of("error", safeMessage(e.getMessage())), true);
        }
    }

    private JsonNode callCreateTask(Long userId, JsonNode arguments) {
        Map<String, Object> payload = createTask(userId, arguments);
        mcpAuditService.recordToolWrite(
                userId,
                McpConstants.TOOL_CREATE_TASK,
                "created taskId=" + payload.get("taskId")
        );
        return toolResult(payload, false);
    }

    private Map<String, Object> searchJd(Long userId, JsonNode arguments) {
        String query = McpArgumentSupport.requiredText(arguments, "query", McpConstants.MAX_QUERY_LENGTH);
        int topK = McpArgumentSupport.optionalTopK(arguments);

        RagRetrieveResult result = knowledgeRetrievalService.retrieve(
                RagRetrieveRequest.builder()
                        .query(query)
                        .scene(RagRetrieveScene.OPPORTUNITY)
                        .topK(topK)
                        .build()
        );

        List<Map<String, Object>> items = result.getChunks().stream()
                .map(this::toJdHitSummary)
                .toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("query", query);
        payload.put("topK", topK);
        payload.put("success", result.isSuccess());
        payload.put("fallbackUsed", result.isFallbackUsed());
        if (result.getErrorCode() != null) {
            payload.put("errorCode", result.getErrorCode());
        }
        payload.put("hits", items);
        return payload;
    }

    private Map<String, Object> toJdHitSummary(RagRetrievedChunk chunk) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("sourceTitle", chunk.getSourceTitle());
        item.put("citation", chunk.getCitation());
        item.put("contentPreview", chunk.getContentPreview());
        item.put("score", chunk.getScore());
        item.put("docId", chunk.getDocId());
        return item;
    }

    private Map<String, Object> getResume(Long userId, JsonNode arguments) {
        Long resumeId = McpArgumentSupport.optionalLong(arguments, "resumeId");
        String versionId = McpArgumentSupport.optionalText(arguments, "versionId", 64);
        boolean includeContent = McpArgumentSupport.optionalBoolean(arguments, "includeContent", false);

        if ((resumeId == null) == (versionId == null || versionId.isBlank())) {
            throw new McpInvalidParamsException("Exactly one of resumeId or versionId is required");
        }

        if (versionId != null && !versionId.isBlank()) {
            return getResumeByVersion(userId, versionId, includeContent);
        }
        return getResumeByResumeId(userId, resumeId, includeContent);
    }

    private Map<String, Object> getResumeByVersion(Long userId, String versionId, boolean includeContent) {
        ResumeVersionEntity owned = resumeVersionMapper.selectOne(
                new LambdaQueryWrapper<ResumeVersionEntity>()
                        .eq(ResumeVersionEntity::getVersionId, versionId)
                        .eq(ResumeVersionEntity::getUserId, userId)
                        .last("LIMIT 1")
        );
        if (owned == null) {
            throw new McpForbiddenException();
        }

        ResumeVersionVO version = resumeVersionService.getVersion(userId, versionId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "resume_version");
        payload.put("versionId", version.versionId());
        payload.put("versionName", version.versionName());
        payload.put("targetJdId", version.targetJdId());
        payload.put("targetJdLabel", version.targetJdLabel());
        payload.put("sessionId", version.sessionId());
        if (includeContent) {
            payload.put("contentMarkdown", version.contentMarkdown());
        } else if (version.contentMarkdown() != null) {
            payload.put("contentLength", version.contentMarkdown().length());
        }
        return payload;
    }

    private Map<String, Object> getResumeByResumeId(Long userId, Long resumeId, boolean includeContent) {
        ResumeEntity owned = resumeMapper.selectOne(
                new LambdaQueryWrapper<ResumeEntity>()
                        .eq(ResumeEntity::getId, resumeId)
                        .eq(ResumeEntity::getUserId, userId)
                        .eq(ResumeEntity::getStatus, ResumeService.STATUS_ACTIVE)
                        .last("LIMIT 1")
        );
        if (owned == null) {
            throw new McpForbiddenException();
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "resume");
        payload.put("resumeId", owned.getId());
        payload.put("title", owned.getTitle());
        payload.put("isDefault", owned.getIsDefault());
        if (includeContent) {
            payload.put("content", owned.getContent());
        } else if (owned.getContent() != null) {
            payload.put("contentLength", owned.getContent().length());
        }
        return payload;
    }

    private Map<String, Object> createTask(Long userId, JsonNode arguments) {
        CareerTaskCreateRequest request = new CareerTaskCreateRequest();
        request.setTitle(McpArgumentSupport.requiredText(arguments, "title", 200));
        request.setCategory(McpArgumentSupport.optionalText(arguments, "category", 32));
        request.setPriority(McpArgumentSupport.optionalText(arguments, "priority", 16));
        request.setDueDate(parseDueDate(McpArgumentSupport.optionalText(arguments, "dueDate", 16)));

        CareerTaskResponse created = careerTaskService.createTaskForAgent(userId, request);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", created.getId());
        payload.put("title", created.getTitle());
        payload.put("category", created.getCategory());
        payload.put("priority", created.getPriority());
        payload.put("status", created.getStatus());
        if (created.getDueDate() != null) {
            payload.put("dueDate", created.getDueDate().toString());
        }
        return payload;
    }

    private LocalDate parseDueDate(String dueDate) {
        if (dueDate == null || dueDate.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(dueDate);
        } catch (DateTimeParseException e) {
            throw new McpInvalidParamsException("dueDate must be yyyy-MM-dd");
        }
    }

    private JsonNode toolResult(Object payload, boolean isError) {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode content = root.putArray("content");
        ObjectNode textItem = content.addObject();
        textItem.put("type", "text");
        try {
            textItem.put("text", objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            textItem.put("text", "{\"error\":\"serialization_failed\"}");
            isError = true;
        }
        root.put("isError", isError);
        return root;
    }

    private ObjectNode toolDescriptor(
            String name,
            String description,
            ObjectNode inputSchema,
            ObjectNode annotations
    ) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("name", name);
        node.put("description", description);
        node.set("inputSchema", inputSchema);
        node.set("annotations", annotations);
        return node;
    }

    private ObjectNode readOnlyAnnotations() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("readOnlyHint", true);
        node.put("destructiveHint", false);
        node.put("openWorldHint", false);
        return node;
    }

    private ObjectNode writeAnnotations() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("readOnlyHint", false);
        node.put("destructiveHint", false);
        node.put("openWorldHint", false);
        node.put("write", true);
        return node;
    }

    private ObjectNode searchJdSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("query").put("type", "string");
        properties.putObject("topK").put("type", "integer");
        ArrayNode required = schema.putArray("required");
        required.add("query");
        return schema;
    }

    private ObjectNode getResumeSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("resumeId").put("type", "integer");
        properties.putObject("versionId").put("type", "string");
        properties.putObject("includeContent").put("type", "boolean");
        return schema;
    }

    private ObjectNode createTaskSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        properties.putObject("title").put("type", "string");
        properties.putObject("category").put("type", "string");
        properties.putObject("priority").put("type", "string");
        properties.putObject("dueDate").put("type", "string");
        ArrayNode required = schema.putArray("required");
        required.add("title");
        return schema;
    }

    private String safeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "operation failed";
        }
        return message.length() > 200 ? message.substring(0, 200) : message;
    }
}
