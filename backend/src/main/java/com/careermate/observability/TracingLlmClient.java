package com.careermate.observability;

import com.careermate.llm.LlmClient;
import com.careermate.llm.StreamCallback;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.dto.ChatResponse;
import com.careermate.llm.dto.ToolCallRequest;
import com.careermate.llm.dto.ToolCallResponse;

public class TracingLlmClient implements LlmClient {

    private final LlmClient delegate;
    private final LlmTracingSupport llmTracingSupport;
    private final LlmChatTraceRecorder llmChatTraceRecorder;

    public TracingLlmClient(
            LlmClient delegate,
            LlmTracingSupport llmTracingSupport,
            LlmChatTraceRecorder llmChatTraceRecorder
    ) {
        this.delegate = delegate;
        this.llmTracingSupport = llmTracingSupport;
        this.llmChatTraceRecorder = llmChatTraceRecorder;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        String model = request == null ? null : request.getModel();
        return llmTracingSupport.traceChat(null, model, false, () -> {
            ChatResponse response = delegate.chat(request);
            long latencyMs = response.getLatencyMs() == null ? 0L : response.getLatencyMs();
            llmChatTraceRecorder.record(response, true, latencyMs, null);
            return response;
        });
    }

    @Override
    public void streamChat(ChatRequest request, StreamCallback callback) {
        String model = request == null ? null : request.getModel();
        llmTracingSupport.traceChat(null, model, true, () -> {
            delegate.streamChat(request, new StreamCallback() {
                @Override
                public void onToken(String token) {
                    callback.onToken(token);
                }

                @Override
                public void onComplete(ChatResponse response) {
                    long latencyMs = response != null && response.getLatencyMs() != null
                            ? response.getLatencyMs()
                            : 0L;
                    llmChatTraceRecorder.record(response, true, latencyMs, null);
                    callback.onComplete(response);
                }

                @Override
                public void onError(Throwable error) {
                    llmChatTraceRecorder.record(null, false, 0L, error == null ? "LLM_ERROR" : error.getClass().getSimpleName());
                    callback.onError(error);
                }
            });
            return null;
        });
    }

    @Override
    public ToolCallResponse toolCall(ToolCallRequest request) {
        return delegate.toolCall(request);
    }
}
