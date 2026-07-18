package com.careermate.agent.runtime;

import com.careermate.agent.AgentPromptAssembler;
import com.careermate.agent.context.AgentConversationContextProvider;
import com.careermate.agent.context.CareerProfileContextProvider;
import com.careermate.agent.context.CareerProfileContextResult;
import com.careermate.agent.context.ConversationContextResult;
import com.careermate.agent.multiagent.AgentSupervisor;
import com.careermate.agent.multiagent.SpecialistResult;
import com.careermate.agent.react.ReActEngine;
import com.careermate.agent.react.ReActTrace;
import com.careermate.agent.tool.AgentLlmIntentRecognizer;
import com.careermate.agent.tool.AgentToolContext;
import com.careermate.agent.tool.AgentToolExecutionService;
import com.careermate.agent.tool.AgentToolResult;
import com.careermate.agent.tool.AgentToolTraceSupport;
import com.careermate.jobmatch.JobMatchContext;
import com.careermate.jobmatch.JobMatchContextProvider;
import com.careermate.llm.dto.ChatMessage;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.model.entity.AgentSessionEntity;
import com.careermate.observability.AgentTracing;
import com.careermate.profile.CareerProfileUpdateResult;
import com.careermate.profile.service.CareerProfileAutoUpdateService;
import com.careermate.resume.ResumeContext;
import com.careermate.resume.ResumeContextProvider;
import com.careermate.prompt.PromptRenderResult;
import com.careermate.prompt.PromptTemplateService;
import com.careermate.workspace.support.WorkspaceSessionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AgentKernelService {

    static final String TRACE_RESUME_CONTEXT = "resume_context";
    static final String TRACE_JOB_MATCH_CONTEXT = "job_match_context";
    static final String TRACE_CONVERSATION_CONTEXT = "conversation_context";
    static final String TRACE_CAREER_PROFILE_CONTEXT = "career_profile_context";
    static final String TRACE_MEMORY_CONTEXT_LOADED = "memory_context_loaded";
    static final String TRACE_CAREER_PROFILE_UPDATE = "career_profile_update";
    static final String TRACE_PROMPT_TEMPLATE = "prompt_template";

    private final ResumeContextProvider resumeContextProvider;
    private final com.careermate.resume.version.service.ResumeVersionService resumeVersionService;
    private final JobMatchContextProvider jobMatchContextProvider;
    private final ObjectMapper objectMapper;
    private final AgentLlmIntentRecognizer intentRecognizer;
    private final AgentToolExecutionService agentToolExecutionService;
    private final AgentConversationContextProvider conversationContextProvider;
    private final CareerProfileContextProvider careerProfileContextProvider;
    private final CareerProfileAutoUpdateService careerProfileAutoUpdateService;
    private final AgentTracing agentTracing;
    private final AgentSupervisor agentSupervisor;
    private final ReActEngine reactEngine;
    private final WorkspaceSessionRepository workspaceSessionRepository;
    private final PromptTemplateService promptTemplateService;

    public AgentKernelService(
            ResumeContextProvider resumeContextProvider,
            JobMatchContextProvider jobMatchContextProvider,
            ObjectMapper objectMapper,
            AgentLlmIntentRecognizer intentRecognizer,
            AgentToolExecutionService agentToolExecutionService,
            AgentConversationContextProvider conversationContextProvider,
            CareerProfileContextProvider careerProfileContextProvider,
            CareerProfileAutoUpdateService careerProfileAutoUpdateService,
            AgentTracing agentTracing,
            AgentSupervisor agentSupervisor,
            ReActEngine reactEngine,
            WorkspaceSessionRepository workspaceSessionRepository,
            PromptTemplateService promptTemplateService,
            com.careermate.resume.version.service.ResumeVersionService resumeVersionService
    ) {
        this.resumeVersionService = resumeVersionService;
        this.resumeContextProvider = resumeContextProvider;
        this.jobMatchContextProvider = jobMatchContextProvider;
        this.objectMapper = objectMapper;
        this.intentRecognizer = intentRecognizer;
        this.agentToolExecutionService = agentToolExecutionService;
        this.conversationContextProvider = conversationContextProvider;
        this.careerProfileContextProvider = careerProfileContextProvider;
        this.careerProfileAutoUpdateService = careerProfileAutoUpdateService;
        this.agentTracing = agentTracing;
        this.agentSupervisor = agentSupervisor;
        this.reactEngine = reactEngine;
        this.workspaceSessionRepository = workspaceSessionRepository;
        this.promptTemplateService = promptTemplateService;
    }

    public AgentRunResult prepareRun(AgentRunRequest request) {
        CollectingAgentEventSink sink = new CollectingAgentEventSink();
        AgentRunResult result = prepareRun(request, sink);
        return AgentRunResult.builder()
                .systemPrompt(result.getSystemPrompt())
                .chatRequest(result.getChatRequest())
                .events(sink.getEvents())
                .toolResults(result.getToolResults())
                .reactTrace(result.getReactTrace())
                .debugMetadata(result.getDebugMetadata())
                .build();
    }

    public AgentRunResult prepareRun(AgentRunRequest request, AgentEventSink sink) {
        Long userId = request.getUserId();
        String sessionId = request.getSessionId();
        String userMessage = request.getUserMessage();

        List<AgentToolResult> toolResults = new java.util.ArrayList<>();
        Map<String, Object> debugMetadata = new LinkedHashMap<>();
        debugMetadata.put("userId", userId);
        debugMetadata.put("sessionId", sessionId);

        Map<String, Object> planData = Map.of(
                "steps", List.of("接收用户输入", "调用 LLM", "生成回复"),
                "totalSteps", 3
        );
        emit(sink, AgentEvent.of(AgentKernelEventTypes.PLAN, planData));

        CareerProfileUpdateResult profileUpdate = careerProfileAutoUpdateService.tryAutoUpdateTargetRole(
                userId,
                userMessage
        );
        addCareerProfileUpdateTrace(sink, profileUpdate);

        CareerProfileContextResult careerProfileContext = agentTracing.call(
                "agent.load_profile_context",
                userId,
                sessionId,
                null,
                null,
                null,
                () -> careerProfileContextProvider.load(userId, sessionId)
        );
        addCareerProfileContextTrace(sink, careerProfileContext);
        addMemoryContextTrace(sink, careerProfileContext);

        ResumeContext resumeContext = agentTracing.call(
                "agent.load_resume_context",
                userId,
                sessionId,
                null,
                null,
                null,
                () -> resumeContextProvider.getResumeContext(userId)
        );
        addResumeContextTrace(sink, resumeContext);

        JobMatchContext jobMatchContext = jobMatchContextProvider.getLatestJobMatchContext(userId);
        addJobMatchContextTrace(sink, jobMatchContext);

        ConversationContextResult conversationContext = agentTracing.call(
                "agent.load_conversation_context",
                userId,
                sessionId,
                null,
                null,
                null,
                () -> loadConversationContextSafely(userId, sessionId, userMessage, sink)
        );
        addConversationContextTrace(sink, conversationContext);

        PromptRenderResult basePrompt = promptTemplateService.render("agent-base");
        String systemPrompt = AgentPromptAssembler.buildBaseSystemPrompt(basePrompt.content());
        systemPrompt = AgentPromptAssembler.appendCareerProfileContext(systemPrompt, careerProfileContext);
        systemPrompt = AgentPromptAssembler.appendResumeContext(systemPrompt, resumeContext);
        // #6 路由提示：本 JD 线已有简历版本时，明确要求微调走 modify_resume，重做才用 generate_resume_from_jd。
        try {
            int resumeVersionCount = resumeVersionService.listBySession(userId, sessionId).size();
            if (resumeVersionCount > 0) {
                systemPrompt = systemPrompt + "\n\n【当前 JD 线简历状态】本条 JD 线已有 " + resumeVersionCount
                        + " 个定制简历版本。用户若要求微调某处（改数字/加或删一段/调措辞/换说法），"
                        + "必须调用 modify_resume（以最新版本为基做最小改动、保留其余），不要重新整份生成；"
                        + "仅当用户明确要重做整份、或尚无任何版本时才用 generate_resume_from_jd。";
            }
        } catch (Exception ignored) {
            // 版本查询失败不影响主流程
        }
        systemPrompt = AgentPromptAssembler.appendJobMatchContext(systemPrompt, jobMatchContext);
        systemPrompt = AgentPromptAssembler.appendConversationContext(systemPrompt, conversationContext);

        try {
            AgentSessionEntity wsSession = workspaceSessionRepository.getSessionIfExists(userId, sessionId);
            if (wsSession != null) {
                systemPrompt = AgentPromptAssembler.appendWorkspaceContext(
                        systemPrompt,
                        wsSession.getWorkspaceType(),
                        wsSession.getJdId(),
                        wsSession.getJdSnapshot()
                );
                debugMetadata.put("workspaceType", wsSession.getWorkspaceType());
                debugMetadata.put("jdId", wsSession.getJdId());
            }
        } catch (Exception ignored) {
            // 工作空间上下文加载失败不中断主流程
        }

        AgentToolContext toolCtx = AgentToolContext.builder()
                .userId(userId)
                .sessionId(sessionId)
                .userMessage(userMessage)
                .build();
        List<SpecialistResult> specialistResults = agentSupervisor.dispatch(toolCtx, userMessage);
        boolean specialistBlocked = specialistResults.stream()
                .anyMatch(result -> result.getStatus() == com.careermate.agent.multiagent.SpecialistResultStatus.BLOCKED);
        for (SpecialistResult specialistResult : specialistResults) {
            if (AgentPromptAssembler.shouldAppendSpecialistResult(specialistResult)) {
                systemPrompt = AgentPromptAssembler.appendSpecialistResult(systemPrompt, specialistResult);
            }
        }
        if (AgentPromptAssembler.shouldRunLegacyToolFallback(specialistBlocked, specialistResults)) {
            AgentToolResult toolResult = executeRoutedToolIfAny(userId, sessionId, userMessage, sink);
            if (toolResult != null) {
                toolResults.add(toolResult);
                systemPrompt = AgentPromptAssembler.appendToolResult(systemPrompt, toolResult);
            }
        }

        ReActTrace reactTrace;
        if (AgentPromptAssembler.shouldRunReAct(specialistBlocked)) {
            AgentToolContext reactCtx = AgentToolContext.builder()
                    .userId(userId)
                    .sessionId(sessionId)
                    .userMessage(userMessage)
                    .build();
            reactTrace = reactEngine.run(reactCtx, userMessage, systemPrompt);
            if (reactTrace.hasSteps()) {
                systemPrompt = AgentPromptAssembler.appendReActTrace(systemPrompt, reactTrace);
                debugMetadata.put("reactRounds", reactTrace.rounds());
                debugMetadata.put("reactReachedFinalAnswer", reactTrace.reachedFinalAnswer());
            }
        } else {
            reactTrace = new ReActTrace(List.of(), false, 0);
        }

        addPromptTemplateTrace(sink, basePrompt.promptId(), basePrompt.version());
        debugMetadata.put("promptId", basePrompt.promptId());
        debugMetadata.put("promptVersion", basePrompt.version());

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(List.of(
                        ChatMessage.builder().role("system").content(systemPrompt).build(),
                        ChatMessage.builder().role("user").content(userMessage).build()
                ))
                .build();

        return AgentRunResult.builder()
                .systemPrompt(systemPrompt)
                .chatRequest(chatRequest)
                .toolResults(toolResults)
                .reactTrace(reactTrace)
                .debugMetadata(debugMetadata)
                .build();
    }

    private AgentToolResult executeRoutedToolIfAny(
            Long userId,
            String sessionId,
            String userMessage,
            AgentEventSink sink
    ) {
        return agentTracing.call(
                "agent.route_tool",
                userId,
                sessionId,
                null,
                null,
                null,
                () -> intentRecognizer.route(userMessage)
                        .map(routed -> {
                            String toolName = routed.toolName();
                            emit(sink, AgentEvent.of(AgentKernelEventTypes.TOOL_START, Map.of(
                                    "toolName", toolName,
                                    "summary", agentToolExecutionService.startSummary(toolName)
                            )));

                            long toolStart = System.currentTimeMillis();
                            AgentToolContext context = AgentToolContext.builder()
                                    .userId(userId)
                                    .sessionId(sessionId)
                                    .userMessage(userMessage)
                                    .args(routed.args())
                                    .build();
                            AgentToolResult result = agentTracing.call(
                                    "agent.execute_tool",
                                    userId,
                                    sessionId,
                                    toolName,
                                    null,
                                    null,
                                    () -> agentToolExecutionService.execute(context, toolName)
                            );
                            long latencyMs = System.currentTimeMillis() - toolStart;

                            emit(sink, AgentEvent.of(AgentKernelEventTypes.TOOL_RESULT, Map.of(
                                    "toolName", toolName,
                                    "success", result.isSuccess(),
                                    "summary", result.getSummary()
                            )));

                            String requestSummary = AgentToolTraceSupport.buildRequestSummary(
                                    toolName,
                                    routed.args(),
                                    userMessage
                            );
                            String responseSummary = AgentToolTraceSupport.buildResponseSummary(result, objectMapper);
                            emit(sink, traceEvent(
                                    toolName,
                                    requestSummary,
                                    responseSummary,
                                    result.isSuccess() ? "SUCCESS" : "FAILED",
                                    latencyMs,
                                    result.isSuccess() ? null : "TOOL_EXEC_FAILED"
                            ));
                            return result;
                        })
                        .orElse(null)
        );
    }

    private ConversationContextResult loadConversationContextSafely(
            Long userId,
            String sessionId,
            String currentUserMessage,
            AgentEventSink sink
    ) {
        try {
            ConversationContextResult result = conversationContextProvider.load(
                    userId,
                    sessionId,
                    currentUserMessage
            );
            if (result.isLoadFailed()) {
                addConversationContextFailedTrace(sink);
                return ConversationContextResult.empty();
            }
            return result;
        } catch (Exception e) {
            log.warn("conversation_context failed: userId={}, sessionId={}", userId, sessionId, e);
            addConversationContextFailedTrace(sink);
            return ConversationContextResult.empty();
        }
    }

    private void emit(AgentEventSink sink, AgentEvent event) {
        sink.emit(event);
    }

    private void addCareerProfileUpdateTrace(AgentEventSink sink, CareerProfileUpdateResult result) {
        if (result == null || !result.isUpdated()) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("updatedFields", result.getUpdatedFields());
        payload.put("targetRole", result.getTargetRole());
        emit(sink, traceEvent(
                TRACE_CAREER_PROFILE_UPDATE,
                "{}",
                writeJson(payload),
                "SUCCESS",
                null,
                null
        ));
    }

    private void addMemoryContextTrace(AgentEventSink sink, CareerProfileContextResult result) {
        if (result != null && result.isFailed()) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("available", false);
            payload.put("errorCode", result.getErrorCode());
            emit(sink, traceEvent(
                    TRACE_MEMORY_CONTEXT_LOADED,
                    "{}",
                    writeJson(payload),
                    "FAILED",
                    null,
                    result.getErrorCode()
            ));
            return;
        }
        String status = result != null && result.isAvailable() ? "SUCCESS" : "EMPTY";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("available", result != null && result.isAvailable());
        if (result != null && result.isAvailable()) {
            payload.put("weaknessCount", result.getWeaknessCount());
            payload.put("skillCount", result.getSkillCount());
            payload.put("hasSessionSummary", result.isHasSessionSummary());
        }
        emit(sink, traceEvent(
                TRACE_MEMORY_CONTEXT_LOADED,
                "{}",
                writeJson(payload),
                status,
                null,
                null
        ));
    }

    private void addCareerProfileContextTrace(AgentEventSink sink, CareerProfileContextResult result) {
        String status = result != null && result.isAvailable() ? "SUCCESS" : "EMPTY";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("available", result != null && result.isAvailable());
        if (result != null && result.isAvailable()) {
            payload.put("targetRole", result.getTargetRole());
            payload.put("skillCount", result.getSkillCount());
        }
        emit(sink, traceEvent(
                TRACE_CAREER_PROFILE_CONTEXT,
                "{}",
                writeJson(payload),
                status,
                null,
                null
        ));
    }

    private void addResumeContextTrace(AgentEventSink sink, ResumeContext resumeContext) {
        boolean available = resumeContext != null && resumeContext.isAvailable();
        String status = available ? "SUCCESS" : "EMPTY";
        String message = available
                ? "已加载默认简历：" + resumeContext.getTitle()
                : "当前用户暂无默认简历";
        int contentLength = 0;
        if (available && resumeContext.getContent() != null) {
            contentLength = resumeContext.getContent().length();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        if (available) {
            payload.put("resumeId", resumeContext.getResumeId());
            payload.put("title", resumeContext.getTitle());
        }
        payload.put("contentLength", contentLength);
        payload.put("message", message);
        emit(sink, traceEvent(
                TRACE_RESUME_CONTEXT,
                "{}",
                writeJson(payload),
                status,
                null,
                null
        ));
    }

    private void addJobMatchContextTrace(AgentEventSink sink, JobMatchContext jobMatchContext) {
        boolean available = jobMatchContext != null && jobMatchContext.isAvailable();
        String status = available ? "SUCCESS" : "EMPTY";
        String message = available
                ? "已加载最近岗位匹配：" + jobMatchContext.getJobTitle()
                : "当前用户暂无岗位匹配记录";

        Map<String, Object> payload = new LinkedHashMap<>();
        if (available) {
            payload.put("jobMatchId", jobMatchContext.getJobMatchId());
            payload.put("jobTitle", jobMatchContext.getJobTitle());
            payload.put("companyName", jobMatchContext.getCompanyName());
            payload.put("matchScore", jobMatchContext.getMatchScore());
            payload.put("matchLevel", jobMatchContext.getMatchLevel());
            payload.put("matchedSkillsCount", sizeOf(jobMatchContext.getMatchedSkills()));
            payload.put("missingSkillsCount", sizeOf(jobMatchContext.getMissingSkills()));
        }
        payload.put("message", message);
        emit(sink, traceEvent(
                TRACE_JOB_MATCH_CONTEXT,
                "{}",
                writeJson(payload),
                status,
                null,
                null
        ));
    }

    private void addConversationContextTrace(AgentEventSink sink, ConversationContextResult result) {
        if (result == null || result.isLoadFailed()) {
            return;
        }
        String status = result.isAvailable() ? "SUCCESS" : "EMPTY";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messageCount", result.getMessageCount());
        payload.put("charCount", result.getCharCount());
        emit(sink, traceEvent(
                TRACE_CONVERSATION_CONTEXT,
                "{}",
                writeJson(payload),
                status,
                null,
                null
        ));
    }

    private void addPromptTemplateTrace(AgentEventSink sink, String promptId, String version) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("promptId", promptId);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("promptId", promptId);
        response.put("version", version);
        emit(sink, traceEvent(
                TRACE_PROMPT_TEMPLATE,
                writeJson(request),
                writeJson(response),
                "SUCCESS",
                null,
                null
        ));
    }

    private void addConversationContextFailedTrace(AgentEventSink sink) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messageCount", 0);
        payload.put("charCount", 0);
        emit(sink, traceEvent(
                TRACE_CONVERSATION_CONTEXT,
                "{}",
                writeJson(payload),
                "FAILED",
                null,
                "CONVERSATION_CONTEXT_FAILED"
        ));
    }

    private AgentEvent traceEvent(
            String traceName,
            String requestSummary,
            String responseSummary,
            String status,
            Long latencyMs,
            String errorCode
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("traceName", traceName);
        payload.put("requestSummary", requestSummary);
        payload.put("responseSummary", responseSummary);
        payload.put("status", status);
        if (latencyMs != null) {
            payload.put("latencyMs", latencyMs);
        }
        if (errorCode != null) {
            payload.put("errorCode", errorCode);
        }
        return AgentEvent.of(AgentKernelEventTypes.TRACE, payload);
    }

    private int sizeOf(List<String> items) {
        return items == null ? 0 : items.size();
    }

    private String writeJson(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
