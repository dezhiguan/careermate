package com.careermate.resume.version.workflow;

import com.careermate.agent.sse.SseEmitterService;
import com.careermate.agent.sse.SseEventType;
import com.careermate.common.exception.BizException;
import com.careermate.llm.LlmClient;
import com.careermate.llm.StreamCallback;
import com.careermate.llm.dto.ChatMessage;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.dto.ChatResponse;
import com.careermate.model.entity.AgentSessionEntity;
import com.careermate.ragforge.RagForgeChunk;
import com.careermate.ragforge.RagForgeClient;
import com.careermate.resume.ResumeContext;
import com.careermate.resume.ResumeContextProvider;
import com.careermate.resume.version.dto.ResumeVersionVO;
import com.careermate.resume.version.service.ResumeVersionService;
import com.careermate.workspace.support.WorkspaceSessionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Component
public class GenerateResumeFromJdWorkflow {

    private static final String SYSTEM_PROMPT = """
            你是简历操盘手小职。基于目标 JD 优化用户简历。
            规则:① 关键词对齐 JD ② 不许虚构经历 ③ 突出匹配项弱化无关项
                 ④ 输出纯 Markdown ⑤ 末尾 JSON 块 ```meta 包含 changes 数组""";

    private static final Pattern META_BLOCK_PATTERN = Pattern.compile(
            "```meta\\s*([\\s\\S]*?)```",
            Pattern.CASE_INSENSITIVE
    );

    private static final int JD_SEARCH_TOP_K = 50;
    private static final int PREVIEW_MAX = 300;

    private final WorkspaceSessionRepository workspaceSessionRepository;
    private final ResumeContextProvider resumeContextProvider;
    private final RagForgeClient ragForgeClient;
    private final LlmClient llmClient;
    private final ResumeVersionService resumeVersionService;
    private final ObjectMapper objectMapper;

    public GenerateResumeFromJdWorkflow(
            WorkspaceSessionRepository workspaceSessionRepository,
            ResumeContextProvider resumeContextProvider,
            RagForgeClient ragForgeClient,
            LlmClient llmClient,
            ResumeVersionService resumeVersionService,
            ObjectMapper objectMapper
    ) {
        this.workspaceSessionRepository = workspaceSessionRepository;
        this.resumeContextProvider = resumeContextProvider;
        this.ragForgeClient = ragForgeClient;
        this.llmClient = llmClient;
        this.resumeVersionService = resumeVersionService;
        this.objectMapper = objectMapper;
    }

    public void generate(Long userId, String sessionId, String jdId, SseEmitterService sseEmitterService) {
        try {
            doGenerate(userId, sessionId, jdId, sseEmitterService);
        } catch (BizException e) {
            log.warn("generate resume failed: sessionId={}, code={}, msg={}", sessionId, e.getCode(), e.getMessage());
            pushError(sseEmitterService, sessionId, e.getMessage());
            appendFailedCard(userId, sessionId, e.getMessage());
            sseEmitterService.complete(sessionId);
        } catch (Exception e) {
            log.error("generate resume unexpected error: sessionId={}", sessionId, e);
            pushError(sseEmitterService, sessionId, "简历生成失败，请稍后重试");
            appendFailedCard(userId, sessionId, "简历生成失败，请稍后重试");
            sseEmitterService.complete(sessionId);
        }
    }

    protected void doGenerate(Long userId, String sessionId, String jdId, SseEmitterService sseEmitterService) {
        AgentSessionEntity session = workspaceSessionRepository.requireSession(userId, sessionId);
        if (jdId == null || jdId.isBlank()) {
            throw new BizException(400, "缺少目标 JD");
        }
        if (session.getJdId() != null && !jdId.equals(session.getJdId())) {
            throw new BizException(400, "JD 与会话不匹配");
        }

        ResumeContext resumeContext = resumeContextProvider.getResumeContext(userId);
        if (!resumeContext.isAvailable() || resumeContext.getContent() == null || resumeContext.getContent().isBlank()) {
            throw new BizException(400, "请先上传简历");
        }

        String jdContent = fetchJdContent(jdId);
        Map<String, Object> snapshot = parseSnapshot(session.getJdSnapshot());
        String company = stringValue(snapshot.get("company"));
        String title = stringValue(snapshot.get("title"));
        String versionName = buildVersionName(company, title);
        String targetLabel = versionName;

        String userPrompt = "目标 JD:\n" + jdContent + "\n\n用户简历:\n" + resumeContext.getContent();
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(
                        ChatMessage.builder().role("system").content(SYSTEM_PROMPT).build(),
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
                if (sseEmitterService != null) {
                    sseEmitterService.send(sessionId, SseEventType.TOKEN, Map.of("delta", token, "content", token));
                }
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

        String raw = fullOutput.toString();
        MetaParseResult meta = parseMetaBlock(raw);
        String markdown = meta.markdown();

        ResumeVersionVO saved = resumeVersionService.createVersion(
                userId,
                sessionId,
                resumeContext.getResumeId(),
                jdId,
                targetLabel,
                versionName,
                markdown,
                meta.changes()
        );

        String preview = markdown.length() <= PREVIEW_MAX
                ? markdown
                : markdown.substring(0, PREVIEW_MAX) + "…";

        Map<String, Object> card = buildGeneratedCard(saved.versionId(), saved.versionName(), preview);
        String cardMetadata = writeJson(Map.of("card", card));

        workspaceSessionRepository.appendMessage(
                userId,
                session,
                "assistant",
                "已为你生成针对「" + versionName + "」的简历版本。",
                "CARD",
                cardMetadata,
                null
        );

        if (sseEmitterService != null) {
            sseEmitterService.send(sessionId, SseEventType.UI_ACTION, Map.of("card", card));
            sseEmitterService.send(sessionId, SseEventType.DONE, Map.of("versionId", saved.versionId()));
            sseEmitterService.complete(sessionId);
        }
    }

    private String fetchJdContent(String jdId) {
        Long docId = parseDocId(jdId);
        List<RagForgeChunk> chunks = ragForgeClient.searchJd("工程师", JD_SEARCH_TOP_K);
        List<RagForgeChunk> filtered = chunks.stream()
                .filter(c -> docId.equals(c.docId()))
                .toList();
        if (filtered.isEmpty()) {
            chunks = ragForgeClient.searchJd("Java 后端", JD_SEARCH_TOP_K);
            filtered = chunks.stream()
                    .filter(c -> docId.equals(c.docId()))
                    .toList();
        }
        if (filtered.isEmpty()) {
            throw new BizException(404, "JD 不存在或已下架");
        }
        return filtered.stream()
                .sorted(Comparator.comparing(RagForgeChunk::chunkId, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(RagForgeChunk::content)
                .filter(c -> c != null && !c.isBlank())
                .collect(Collectors.joining("\n"));
    }

    private void pushError(SseEmitterService sseEmitterService, String sessionId, String message) {
        if (sseEmitterService == null) {
            return;
        }
        sseEmitterService.send(sessionId, SseEventType.ERROR, Map.of("message", message));
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("type", "GENERATE_FAILED");
        card.put("message", message);
        card.put("actions", List.of(
                Map.of("label", "重试", "action", "RETRY", "payload", "")
        ));
        sseEmitterService.send(sessionId, SseEventType.UI_ACTION, Map.of("card", card));
    }

    private void appendFailedCard(Long userId, String sessionId, String message) {
        try {
            AgentSessionEntity session = workspaceSessionRepository.requireSession(userId, sessionId);
            Map<String, Object> card = new LinkedHashMap<>();
            card.put("type", "GENERATE_FAILED");
            card.put("message", message);
            card.put("actions", List.of(
                    Map.of("label", "重试", "action", "RETRY", "payload", session.getJdId() != null ? session.getJdId() : "")
            ));
            workspaceSessionRepository.appendMessage(
                    userId,
                    session,
                    "assistant",
                    "简历生成失败：" + message,
                    "CARD",
                    writeJson(Map.of("card", card)),
                    null
            );
        } catch (Exception e) {
            log.warn("append failed card error: {}", e.getMessage());
        }
    }

    private Map<String, Object> buildGeneratedCard(String versionId, String versionName, String preview) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("type", "RESUME_GENERATED");
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
                Map.of("label", "去我的简历继续改", "action", "NAVIGATE", "payload", "/mine")
        ));
        return card;
    }

    static MetaParseResult parseMetaBlock(String raw) {
        if (raw == null) {
            return new MetaParseResult("", List.of());
        }
        Matcher matcher = META_BLOCK_PATTERN.matcher(raw);
        String markdown = raw;
        List<Map<String, Object>> changes = List.of();
        if (matcher.find()) {
            markdown = matcher.replaceFirst("").trim();
            String json = matcher.group(1).trim();
            changes = parseChangesJson(json);
        }
        return new MetaParseResult(markdown, changes);
    }

    private static List<Map<String, Object>> parseChangesJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> root = mapper.readValue(json, new TypeReference<>() {});
            Object changes = root.get("changes");
            if (changes instanceof List<?> list) {
                List<Map<String, Object>> result = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Map<?, ?> map) {
                        Map<String, Object> cast = new LinkedHashMap<>();
                        map.forEach((k, v) -> cast.put(String.valueOf(k), v));
                        result.add(cast);
                    }
                }
                return result;
            }
            return List.of();
        } catch (Exception e) {
            return List.of();
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

    record MetaParseResult(String markdown, List<Map<String, Object>> changes) {
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
