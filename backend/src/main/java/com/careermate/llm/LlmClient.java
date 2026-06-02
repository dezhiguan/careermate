package com.careermate.llm;

import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.dto.ChatResponse;
import com.careermate.llm.dto.ToolCallRequest;
import com.careermate.llm.dto.ToolCallResponse;

public interface LlmClient {

    ChatResponse chat(ChatRequest request);

    void streamChat(ChatRequest request, StreamCallback callback);

    ToolCallResponse toolCall(ToolCallRequest request);
}
