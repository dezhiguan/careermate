package com.careermate.mcp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.careermate.agent.tool.rag.RagRetrieveRequest;
import com.careermate.agent.tool.rag.RagRetrieveResult;
import com.careermate.agent.tool.rag.RagRetrieveScene;
import com.careermate.agent.tool.rag.RagRetrievedChunk;
import com.careermate.agent.tool.rag.RagRetrieverChunkType;
import com.careermate.common.exception.BizException;
import com.careermate.knowledge.KnowledgeRetrievalService;
import com.careermate.mapper.ResumeMapper;
import com.careermate.mapper.ResumeVersionMapper;
import com.careermate.model.entity.ResumeEntity;
import com.careermate.model.entity.ResumeVersionEntity;
import com.careermate.resume.service.ResumeService;
import com.careermate.resume.version.dto.ResumeVersionVO;
import com.careermate.resume.version.service.ResumeVersionService;
import com.careermate.task.dto.CareerTaskCreateRequest;
import com.careermate.task.dto.CareerTaskResponse;
import com.careermate.task.service.CareerTaskService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpToolServiceTest {

    @Mock
    private KnowledgeRetrievalService knowledgeRetrievalService;
    @Mock
    private ResumeVersionService resumeVersionService;
    @Mock
    private ResumeVersionMapper resumeVersionMapper;
    @Mock
    private ResumeMapper resumeMapper;
    @Mock
    private CareerTaskService careerTaskService;
    @Mock
    private McpAuditService mcpAuditService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private McpToolService service;

    @BeforeEach
    void setUp() {
        service = new McpToolService(
                knowledgeRetrievalService,
                resumeVersionService,
                resumeVersionMapper,
                resumeMapper,
                careerTaskService,
                mcpAuditService,
                objectMapper
        );
    }

    @Test
    void listToolsExposesSchemasAndAnnotations() {
        JsonNode tools = service.listTools().path("tools");

        assertEquals(3, tools.size());
        assertEquals(McpConstants.TOOL_SEARCH_JD, tools.get(0).path("name").asText());
        assertTrue(tools.get(0).path("annotations").path("readOnlyHint").asBoolean());
        assertEquals("query", tools.get(0).path("inputSchema").path("required").get(0).asText());
        assertEquals(McpConstants.TOOL_CREATE_TASK, tools.get(2).path("name").asText());
        assertTrue(tools.get(2).path("annotations").path("write").asBoolean());
        assertFalse(tools.get(2).path("annotations").path("destructiveHint").asBoolean());
    }

    @Test
    void searchJdReturnsStructuredHitsAndRequestDefaults() throws Exception {
        when(knowledgeRetrievalService.retrieve(any(RagRetrieveRequest.class))).thenReturn(RagRetrieveResult.builder()
                .success(true)
                .query("Java")
                .scene(RagRetrieveScene.OPPORTUNITY)
                .chunks(List.of(RagRetrievedChunk.builder()
                        .sourceTitle("jd.md")
                        .citation("JD@jd.md")
                        .contentPreview("Java 岗位")
                        .score(0.9)
                        .docId(88L)
                        .chunkType(RagRetrieverChunkType.JD)
                        .build()))
                .fallbackUsed(false)
                .latencyMs(1)
                .build());

        ObjectNode args = objectMapper.createObjectNode();
        args.put("query", " Java ");
        JsonNode result = service.callTool(7L, McpConstants.TOOL_SEARCH_JD, args);

        assertFalse(result.path("isError").asBoolean());
        JsonNode text = objectMapper.readTree(result.path("content").get(0).path("text").asText());
        assertEquals("Java", text.path("query").asText());
        assertEquals(McpConstants.DEFAULT_TOP_K, text.path("topK").asInt());
        assertEquals("JD@jd.md", text.path("hits").get(0).path("citation").asText());

        ArgumentCaptor<RagRetrieveRequest> captor = ArgumentCaptor.forClass(RagRetrieveRequest.class);
        verify(knowledgeRetrievalService).retrieve(captor.capture());
        assertEquals(RagRetrieveScene.OPPORTUNITY, captor.getValue().getScene());
        assertEquals(McpConstants.DEFAULT_TOP_K, captor.getValue().getTopK());
    }

    @Test
    void getResumeByResumeIdCanReturnContentOrLengthAndForbidsMissingOwner() throws Exception {
        ResumeEntity resume = new ResumeEntity();
        resume.setId(99L);
        resume.setTitle("主简历");
        resume.setContent("# 内容");
        resume.setIsDefault(true);
        when(resumeMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(resume);

        ObjectNode args = objectMapper.createObjectNode();
        args.put("resumeId", 99);
        JsonNode result = service.callTool(7L, McpConstants.TOOL_GET_RESUME, args);
        JsonNode payload = objectMapper.readTree(result.path("content").get(0).path("text").asText());
        assertEquals("resume", payload.path("type").asText());
        assertEquals(4, payload.path("contentLength").asInt());

        args.put("includeContent", true);
        result = service.callTool(7L, McpConstants.TOOL_GET_RESUME, args);
        payload = objectMapper.readTree(result.path("content").get(0).path("text").asText());
        assertEquals("# 内容", payload.path("content").asText());

        when(resumeMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        JsonNode forbidden = service.callTool(7L, McpConstants.TOOL_GET_RESUME, args);
        assertTrue(forbidden.path("isError").asBoolean());
        assertTrue(forbidden.path("content").get(0).path("text").asText().contains("forbidden"));
    }

    @Test
    void getResumeByVersionReturnsSummaryOrContent() throws Exception {
        when(resumeVersionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(new ResumeVersionEntity());
        when(resumeVersionService.getVersion(7L, "v-1")).thenReturn(new ResumeVersionVO(
                "v-1",
                "第一版",
                "WS-1",
                123L,
                "JD 标签",
                "公司",
                "岗位",
                1,
                "变化",
                "# Markdown",
                List.of(Map.of("type", "note")),
                BigDecimal.TEN,
                null
        ));

        ObjectNode args = objectMapper.createObjectNode();
        args.put("versionId", "v-1");
        JsonNode result = service.callTool(7L, McpConstants.TOOL_GET_RESUME, args);
        JsonNode payload = objectMapper.readTree(result.path("content").get(0).path("text").asText());
        assertEquals("resume_version", payload.path("type").asText());
        assertEquals(10, payload.path("contentLength").asInt());

        args.put("includeContent", true);
        result = service.callTool(7L, McpConstants.TOOL_GET_RESUME, args);
        payload = objectMapper.readTree(result.path("content").get(0).path("text").asText());
        assertEquals("# Markdown", payload.path("contentMarkdown").asText());
    }

    @Test
    void createTaskMapsArgumentsAuditsAndReportsErrors() throws Exception {
        when(careerTaskService.createTaskForAgent(eq(7L), any(CareerTaskCreateRequest.class)))
                .thenReturn(CareerTaskResponse.builder()
                        .id(5L)
                        .title("准备面试")
                        .category("interview")
                        .priority("HIGH")
                        .status("TODO")
                        .dueDate(LocalDate.of(2026, 7, 20))
                        .build());

        ObjectNode args = objectMapper.createObjectNode();
        args.put("title", " 准备面试 ");
        args.put("category", "interview");
        args.put("priority", "HIGH");
        args.put("dueDate", "2026-07-20");

        JsonNode result = service.callTool(7L, McpConstants.TOOL_CREATE_TASK, args);

        JsonNode payload = objectMapper.readTree(result.path("content").get(0).path("text").asText());
        assertEquals(5L, payload.path("taskId").asLong());
        assertEquals("2026-07-20", payload.path("dueDate").asText());
        verify(mcpAuditService).recordToolWrite(7L, McpConstants.TOOL_CREATE_TASK, "created taskId=5");

        ArgumentCaptor<CareerTaskCreateRequest> captor = ArgumentCaptor.forClass(CareerTaskCreateRequest.class);
        verify(careerTaskService).createTaskForAgent(eq(7L), captor.capture());
        assertEquals("准备面试", captor.getValue().getTitle());

        when(careerTaskService.createTaskForAgent(eq(7L), any(CareerTaskCreateRequest.class)))
                .thenThrow(new BizException(400, "x".repeat(250)));
        JsonNode error = service.callTool(7L, McpConstants.TOOL_CREATE_TASK, args);
        assertTrue(error.path("isError").asBoolean());
        JsonNode errorPayload = objectMapper.readTree(error.path("content").get(0).path("text").asText());
        assertEquals(200, errorPayload.path("error").asText().length());
    }

    @Test
    void invalidArgumentsAndUnknownToolThrowInvalidParams() {
        ObjectNode args = objectMapper.createObjectNode();
        args.put("resumeId", 1);
        args.put("versionId", "v");
        assertThrows(McpInvalidParamsException.class,
                () -> service.callTool(7L, McpConstants.TOOL_GET_RESUME, args));

        ObjectNode taskArgs = objectMapper.createObjectNode();
        taskArgs.put("title", "bad date");
        taskArgs.put("dueDate", "2026-99-99");
        assertThrows(McpInvalidParamsException.class,
                () -> service.callTool(7L, McpConstants.TOOL_CREATE_TASK, taskArgs));

        assertThrows(McpInvalidParamsException.class,
                () -> service.callTool(7L, "unknown", objectMapper.createObjectNode()));
    }
}
