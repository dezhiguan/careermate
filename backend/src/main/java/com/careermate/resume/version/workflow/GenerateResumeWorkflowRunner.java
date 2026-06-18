package com.careermate.resume.version.workflow;

import com.careermate.common.exception.BizException;
import com.careermate.llm.LlmClient;
import com.careermate.llm.StreamCallback;
import com.careermate.llm.dto.ChatMessage;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.dto.ChatResponse;
import com.careermate.model.entity.AgentSessionEntity;
import com.careermate.ragforge.RagForgeChunk;
import com.careermate.ragforge.RagForgeClient;
import com.careermate.prompt.PromptRenderResult;
import com.careermate.prompt.PromptTemplateService;
import com.careermate.resume.ResumeContext;
import com.careermate.resume.ResumeContextProvider;
import com.careermate.resume.version.dto.ResumeVersionVO;
import com.careermate.resume.version.export.MarkdownExportSupport;
import com.careermate.resume.version.service.ResumeVersionService;
import com.careermate.workspace.support.WorkspaceSessionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
class GenerateResumeWorkflowRunner {

    static final String RESUME_PROMPT_ID = "resume-generate-from-jd";
    private static final int JD_SEARCH_TOP_K = 50;
    private static final int PREVIEW_MAX = 300;
    static final String RESUME_GENERATED_CARD_TITLE = "简历已生成";
    static final String GENERATE_PROGRESS_MESSAGE = "正在生成简历...";

    private static final List<String> PLACEHOLDER_MARKERS = List.of(
            "暂无", "待补充", "示例", "公司A", "项目A", "某某公司", "XXX", "xxx"
    );

    private static final Set<String> RESUME_SECTIONS = Set.of(
            "个人优势", "专业技能", "工作经历", "项目经历", "教育经历"
    );

    private final WorkspaceSessionRepository workspaceSessionRepository;
    private final ResumeContextProvider resumeContextProvider;
    private final RagForgeClient ragForgeClient;
    private final LlmClient llmClient;
    private final ResumeVersionService resumeVersionService;
    private final ObjectMapper objectMapper;
    private final PromptTemplateService promptTemplateService;

    GenerateResumeWorkflowRunner(
            WorkspaceSessionRepository workspaceSessionRepository,
            ResumeContextProvider resumeContextProvider,
            RagForgeClient ragForgeClient,
            LlmClient llmClient,
            ResumeVersionService resumeVersionService,
            ObjectMapper objectMapper,
            PromptTemplateService promptTemplateService
    ) {
        this.workspaceSessionRepository = workspaceSessionRepository;
        this.resumeContextProvider = resumeContextProvider;
        this.ragForgeClient = ragForgeClient;
        this.llmClient = llmClient;
        this.resumeVersionService = resumeVersionService;
        this.objectMapper = objectMapper;
        this.promptTemplateService = promptTemplateService;
    }

    GenerateResumeWorkflowResult execute(GenerateResumeWorkflowRun run, GenerateResumeWorkflowEventSink eventSink) {
        executeStep(run, eventSink, GenerateResumeWorkflowStep.LOAD_WORKSPACE, this::stepLoadWorkspace);
        executeStep(run, eventSink, GenerateResumeWorkflowStep.LOAD_RESUME, this::stepLoadResume);
        executeStep(run, eventSink, GenerateResumeWorkflowStep.LOAD_JD, this::stepLoadJd);
        executeStep(run, eventSink, GenerateResumeWorkflowStep.ANALYZE_GAP, this::stepAnalyzeGap);
        executeStep(run, eventSink, GenerateResumeWorkflowStep.GENERATE_RESUME, this::stepGenerateResume);
        executeStep(run, eventSink, GenerateResumeWorkflowStep.QUALITY_CHECK, this::stepQualityCheck);
        executeStep(run, eventSink, GenerateResumeWorkflowStep.SAVE_VERSION, this::stepSaveVersion);
        executeStep(run, eventSink, GenerateResumeWorkflowStep.EMIT_CARD, this::stepEmitCard);
        return new GenerateResumeWorkflowResult(run.card());
    }

    @FunctionalInterface
    private interface StepAction {
        void run(GenerateResumeWorkflowRun run) throws Exception;
    }

    private void executeStep(
            GenerateResumeWorkflowRun run,
            GenerateResumeWorkflowEventSink eventSink,
            GenerateResumeWorkflowStep step,
            StepAction action
    ) {
        long start = System.currentTimeMillis();
        String requestSummary = buildRequestSummary(step, run);
        try {
            action.run(run);
            eventSink.recordSuccess(step, requestSummary, buildSuccessResponse(step, run), elapsed(start));
        } catch (BizException e) {
            eventSink.recordFailure(step, requestSummary, mapErrorCode(step, e), elapsed(start));
            throw e;
        } catch (Exception e) {
            log.warn("workflow step {} failed: {}", step, e.getMessage());
            eventSink.recordFailure(step, requestSummary, mapErrorCode(step, e), elapsed(start));
            throw new BizException(500, stepFailureMessage(step));
        }
    }

    private void stepLoadWorkspace(GenerateResumeWorkflowRun run) {
        AgentSessionEntity session = workspaceSessionRepository.requireSession(run.userId(), run.sessionId());
        String jdId = run.jdId();
        if (jdId == null || jdId.isBlank()) {
            throw new BizException(400, "缺少目标 JD");
        }
        if (session.getJdId() != null && !jdId.equals(session.getJdId())) {
            throw new BizException(400, "JD 与会话不匹配");
        }
        run.setSession(session);
        run.setJdSnapshot(parseSnapshot(session.getJdSnapshot()));
    }

    private void stepLoadResume(GenerateResumeWorkflowRun run) {
        ResumeContext resumeContext = resumeContextProvider.getResumeContext(run.userId());
        if (!resumeContext.isAvailable() || resumeContext.getContent() == null || resumeContext.getContent().isBlank()) {
            throw new BizException(400, "请先上传简历");
        }
        run.setResumeContext(resumeContext);
    }

    private void stepLoadJd(GenerateResumeWorkflowRun run) {
        Map<String, Object> snapshot = run.jdSnapshot();
        String jdContent = fetchJdContent(run.jdId(), snapshot);
        run.setJdContent(jdContent);
        run.setCompany(stringValue(snapshot.get("company")));
        run.setTitle(stringValue(snapshot.get("title")));
        String versionName = buildVersionName(run.company(), run.title());
        run.setVersionName(versionName);
        run.setTargetLabel(versionName);
    }

    private void stepAnalyzeGap(GenerateResumeWorkflowRun run) {
        run.setGapSummary(analyzeGap(run.jdContent(), run.resumeContext().getContent()));
    }

    private void stepGenerateResume(GenerateResumeWorkflowRun run) {
        PromptRenderResult resumePrompt = promptTemplateService.render(RESUME_PROMPT_ID);
        run.setResumePromptId(resumePrompt.promptId());
        run.setResumePromptVersion(resumePrompt.version());

        String userPrompt = "目标 JD:\n" + run.jdContent()
                + "\n\n用户简历:\n" + run.resumeContext().getContent()
                + "\n\n差距摘要:\n" + run.gapSummary();
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(
                        ChatMessage.builder().role("system").content(resumePrompt.content()).build(),
                        ChatMessage.builder().role("user").content(userPrompt).build()
                ))
                .build();

        StringBuilder fullOutput = new StringBuilder();
        AtomicReference<Throwable> streamError = new AtomicReference<>();

        llmClient.streamChat(request, new StreamCallback() {
            @Override
            public void onToken(String token) {
                if (token == null || token.isEmpty()) {
                    return;
                }
                fullOutput.append(token);
            }

            @Override
            public void onComplete(ChatResponse response) {
            }

            @Override
            public void onError(Throwable error) {
                streamError.set(error);
            }
        });

        if (streamError.get() != null) {
            throw new BizException(500, "LLM 生成失败: " + streamError.get().getMessage());
        }

        if (run.sseEmitterService() != null) {
            run.sseEmitterService().send(
                    run.sessionId(),
                    com.careermate.agent.sse.SseEventType.TOKEN,
                    Map.of("delta", GENERATE_PROGRESS_MESSAGE, "content", GENERATE_PROGRESS_MESSAGE)
            );
        }

        run.setRawLlmOutput(fullOutput.toString());
    }

    private void stepQualityCheck(GenerateResumeWorkflowRun run) {
        GenerateResumeFromJdWorkflow.MetaParseResult meta =
                GenerateResumeFromJdWorkflow.parseMetaBlock(run.rawLlmOutput());
        String markdown = meta.markdown();
        validateMarkdownQuality(markdown);
        run.setMarkdown(markdown);
        run.setChangeSummary(meta.changeSummary());
        run.setOptimizationNotes(meta.changes());
    }

    private void stepSaveVersion(GenerateResumeWorkflowRun run) {
        ResumeVersionVO saved = resumeVersionService.createVersion(
                run.userId(),
                run.sessionId(),
                run.resumeContext().getResumeId(),
                run.jdId(),
                run.targetLabel(),
                run.company(),
                run.title(),
                run.markdown(),
                run.changeSummary(),
                run.optimizationNotes()
        );
        run.setSavedVersion(saved);
    }

    private void stepEmitCard(GenerateResumeWorkflowRun run) {
        String preview = run.markdown().length() <= PREVIEW_MAX
                ? run.markdown()
                : run.markdown().substring(0, PREVIEW_MAX) + "…";
        Map<String, Object> card = buildGeneratedCard(
                run.savedVersion().versionId(),
                run.savedVersion().versionName(),
                preview
        );
        run.setCard(card);

        workspaceSessionRepository.appendMessage(
                run.userId(),
                run.session(),
                "assistant",
                "已为你生成针对「" + run.versionName() + "」的简历版本。",
                "CARD",
                writeJson(Map.of("card", card)),
                null
        );

        if (run.sseEmitterService() != null) {
            run.sseEmitterService().send(
                    run.sessionId(),
                    com.careermate.agent.sse.SseEventType.UI_ACTION,
                    Map.of("card", card)
            );
            run.sseEmitterService().send(
                    run.sessionId(),
                    com.careermate.agent.sse.SseEventType.DONE,
                    Map.of("versionId", run.savedVersion().versionId())
            );
            run.sseEmitterService().complete(run.sessionId());
        }
    }

    static void validateMarkdownQuality(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            throw new BizException(400, "生成结果为空，请重试");
        }
        String stripped = markdown.replaceAll("```[\\w]*", "").replaceAll("`", "").trim();
        if (stripped.isBlank()) {
            throw new BizException(400, "生成结果为空，请重试");
        }
        for (String marker : PLACEHOLDER_MARKERS) {
            if (markdown.contains(marker)) {
                throw new BizException(400, "生成内容含模板占位词，请重试");
            }
        }
        if (MarkdownExportSupport.hasOptimizationMetaResidual(markdown)) {
            throw new BizException(400, "生成结果含优化说明残留，请重试");
        }
    }

    static String analyzeGap(String jdContent, String resumeContent) {
        if (jdContent == null) {
            jdContent = "";
        }
        if (resumeContent == null) {
            resumeContent = "";
        }
        int jdLen = jdContent.length();
        int resumeLen = resumeContent.length();
        List<String> missingSections = new ArrayList<>();
        for (String section : RESUME_SECTIONS) {
            if (!resumeContent.contains(section)) {
                missingSections.add(section);
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("jdChars=").append(jdLen);
        sb.append(", resumeChars=").append(resumeLen);
        if (!missingSections.isEmpty()) {
            sb.append(", missingSections=").append(String.join(",", missingSections));
        } else {
            sb.append(", sections=complete");
        }
        return sb.toString();
    }

    GenerateResumeWorkflowFailure toFailure(GenerateResumeWorkflowStep step, BizException e) {
        return new GenerateResumeWorkflowFailure(
                step,
                mapErrorCode(step, e),
                e.getMessage(),
                isRetryable(step, e)
        );
    }

    GenerateResumeWorkflowFailure toFailure(GenerateResumeWorkflowStep step, Exception e) {
        return new GenerateResumeWorkflowFailure(
                step,
                mapErrorCode(step, e),
                stepFailureMessage(step),
                isRetryable(step, null)
        );
    }

    GenerateResumeWorkflowStep inferFailedStep(BizException e) {
        String msg = e.getMessage() != null ? e.getMessage() : "";
        if (msg.contains("缺少目标 JD") || msg.contains("与会话不匹配")) {
            return GenerateResumeWorkflowStep.LOAD_WORKSPACE;
        }
        if (msg.contains("请先上传简历")) {
            return GenerateResumeWorkflowStep.LOAD_RESUME;
        }
        if (msg.contains("非法 JD") || msg.contains("JD 不存在") || msg.contains("已下架")) {
            return GenerateResumeWorkflowStep.LOAD_JD;
        }
        if (msg.contains("生成结果为空") || msg.contains("模板占位词") || msg.contains("优化说明残留")) {
            return GenerateResumeWorkflowStep.QUALITY_CHECK;
        }
        if (msg.contains("LLM")) {
            return GenerateResumeWorkflowStep.GENERATE_RESUME;
        }
        return GenerateResumeWorkflowStep.LOAD_WORKSPACE;
    }

    private static boolean isRetryable(GenerateResumeWorkflowStep step, BizException e) {
        if (step == GenerateResumeWorkflowStep.LOAD_RESUME && e != null && e.getMessage() != null
                && e.getMessage().contains("请先上传简历")) {
            return false;
        }
        return step != GenerateResumeWorkflowStep.LOAD_WORKSPACE
                || (e != null && e.getCode() != 400);
    }

    private static String mapErrorCode(GenerateResumeWorkflowStep step, Exception e) {
        return switch (step) {
            case LOAD_WORKSPACE -> "WORKFLOW_LOAD_WORKSPACE_FAILED";
            case LOAD_RESUME -> "WORKFLOW_LOAD_RESUME_FAILED";
            case LOAD_JD -> "WORKFLOW_LOAD_JD_FAILED";
            case ANALYZE_GAP -> "WORKFLOW_ANALYZE_GAP_FAILED";
            case GENERATE_RESUME -> "WORKFLOW_GENERATE_RESUME_FAILED";
            case QUALITY_CHECK -> "WORKFLOW_QUALITY_CHECK_FAILED";
            case SAVE_VERSION -> "WORKFLOW_SAVE_VERSION_FAILED";
            case EMIT_CARD -> "WORKFLOW_EMIT_CARD_FAILED";
        };
    }

    private static String stepFailureMessage(GenerateResumeWorkflowStep step) {
        return switch (step) {
            case LOAD_JD -> "读取 JD 失败，请重试或重新选择岗位";
            case LOAD_RESUME -> "请先上传简历";
            case GENERATE_RESUME -> "简历生成失败，请稍后重试";
            case QUALITY_CHECK -> "生成结果为空，请重试";
            default -> "简历生成失败，请稍后重试";
        };
    }

    private static long elapsed(long start) {
        return System.currentTimeMillis() - start;
    }

    private String buildRequestSummary(GenerateResumeWorkflowStep step, GenerateResumeWorkflowRun run) {
        return switch (step) {
            case LOAD_WORKSPACE -> summaryOf("sessionId", run.sessionId(), "jdId", run.jdId());
            case LOAD_RESUME -> summaryOf("userId", run.userId());
            case LOAD_JD -> summaryOf("jdId", run.jdId());
            case ANALYZE_GAP -> summaryOf("jdId", run.jdId());
            case GENERATE_RESUME -> summaryOf("jdId", run.jdId(), "gapSummary", run.gapSummary());
            case QUALITY_CHECK -> summaryOf("rawLength", safeLength(run.rawLlmOutput()));
            case SAVE_VERSION -> summaryOf("versionName", run.versionName());
            case EMIT_CARD -> summaryOf(
                    "versionId", run.savedVersion() != null ? run.savedVersion().versionId() : null
            );
        };
    }

    private String buildSuccessResponse(GenerateResumeWorkflowStep step, GenerateResumeWorkflowRun run) {
        return switch (step) {
            case LOAD_WORKSPACE -> summaryOf("ok", true);
            case LOAD_RESUME -> summaryOf(
                    "resumeId", run.resumeContext() != null ? run.resumeContext().getResumeId() : null
            );
            case LOAD_JD -> summaryOf("jdChars", safeLength(run.jdContent()), "versionName", run.versionName());
            case ANALYZE_GAP -> summaryOf("gapSummary", run.gapSummary());
            case GENERATE_RESUME -> summaryOf(
                    "outputChars", safeLength(run.rawLlmOutput()),
                    "promptId", run.resumePromptId(),
                    "promptVersion", run.resumePromptVersion()
            );
            case QUALITY_CHECK -> summaryOf(
                    "markdownChars", safeLength(run.markdown()),
                    "notesCount", run.optimizationNotes() != null ? run.optimizationNotes().size() : 0
            );
            case SAVE_VERSION -> summaryOf(
                    "versionId", run.savedVersion() != null ? run.savedVersion().versionId() : null
            );
            case EMIT_CARD -> summaryOf("cardType", "RESUME_GENERATED");
        };
    }

    private String summaryOf(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return writeJson(map);
    }

    private static int safeLength(String value) {
        return value == null ? 0 : value.length();
    }

    private String fetchJdContent(String jdId, Map<String, Object> jdSnapshot) {
        Long docId = parseDocId(jdId);

        List<RagForgeChunk> direct = ragForgeClient.fetchDocumentChunks(docId);
        if (!direct.isEmpty()) {
            return mergeChunkContent(direct);
        }

        // TODO(T10-follow-up): 迁移至 KnowledgeRetrievalService.retrieve(OPPORTUNITY, ...)
        List<RagForgeChunk> chunks = ragForgeClient.searchJd("工程师", JD_SEARCH_TOP_K);
        List<RagForgeChunk> filtered = filterByDocId(chunks, docId);
        if (filtered.isEmpty()) {
            chunks = ragForgeClient.searchJd("Java 后端", JD_SEARCH_TOP_K);
            filtered = filterByDocId(chunks, docId);
        }
        if (!filtered.isEmpty()) {
            return mergeChunkContent(filtered);
        }

        String cached = stringValue(jdSnapshot.get("jdContent"));
        if (!cached.isBlank()) {
            return cached;
        }

        throw new BizException(404, "JD 不存在或已下架");
    }

    private static List<RagForgeChunk> filterByDocId(List<RagForgeChunk> chunks, Long docId) {
        return chunks.stream()
                .filter(c -> docId.equals(c.docId()))
                .toList();
    }

    private static String mergeChunkContent(List<RagForgeChunk> chunks) {
        return chunks.stream()
                .sorted(Comparator.comparing(RagForgeChunk::chunkId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(RagForgeChunk::content)
                .filter(c -> c != null && !c.isBlank())
                .collect(Collectors.joining("\n"));
    }

    static Map<String, Object> buildGeneratedCard(String versionId, String versionName, String preview) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("type", "RESUME_GENERATED");
        card.put("title", RESUME_GENERATED_CARD_TITLE);
        card.put("versionId", versionId);
        card.put("versionName", versionName);
        card.put("previewMarkdown", preview);
        Map<String, Object> pdfPayload = new LinkedHashMap<>();
        pdfPayload.put("versionId", versionId);
        pdfPayload.put("versionName", versionName);
        card.put("actions", List.of(
                Map.of("label", "查看完整简历", "action", "VIEW_RESUME", "payload", versionId),
                Map.of("label", "复制 Markdown", "action", "COPY_MARKDOWN", "payload", versionId),
                Map.of("label", "下载 PDF", "action", "DOWNLOAD_PDF", "payload", pdfPayload),
                Map.of("label", "下载 Word", "action", "DOWNLOAD_WORD", "payload", pdfPayload),
                Map.of("label", "去我的简历继续改", "action", "NAVIGATE", "payload", "/mine/resume")
        ));
        return card;
    }

    static Map<String, Object> buildFailedCard(
            String sessionId,
            String jdId,
            GenerateResumeWorkflowStep failedStep,
            String message,
            boolean retryable,
            ObjectMapper objectMapper
    ) {
        Map<String, Object> retryPayload = new LinkedHashMap<>();
        retryPayload.put("sessionId", sessionId);
        retryPayload.put("jdId", jdId != null ? jdId : "");
        retryPayload.put("failedStep", failedStep.name());
        retryPayload.put("retryable", retryable);
        String retryPayloadJson = writeJsonStatic(retryPayload, objectMapper);

        Map<String, Object> retryAction = new LinkedHashMap<>();
        retryAction.put("label", "重试");
        retryAction.put("action", "RETRY");
        retryAction.put("payload", retryPayloadJson);

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("type", "GENERATE_FAILED");
        card.put("message", message);
        card.put("sessionId", sessionId);
        card.put("jdId", jdId != null ? jdId : "");
        card.put("failedStep", failedStep.name());
        card.put("retryable", retryable);
        card.put("actions", List.of(retryAction));
        card.put("payload", retryPayload);
        return card;
    }

    private static String writeJsonStatic(Object value, ObjectMapper objectMapper) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private Map<String, Object> parseSnapshot(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static String buildVersionName(String company, String title) {
        if (company != null && !company.isBlank() && title != null && !title.isBlank()) {
            return company + " - " + title;
        }
        if (title != null && !title.isBlank()) {
            return title;
        }
        return "定制简历版";
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static Long parseDocId(String jdId) {
        if (jdId == null || !jdId.startsWith("doc-")) {
            throw new BizException(400, "非法 JD ID");
        }
        String raw = jdId.substring(4).trim();
        if (raw.isEmpty()) {
            throw new BizException(400, "非法 JD ID");
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw new BizException(400, "非法 JD ID");
        }
    }
}
