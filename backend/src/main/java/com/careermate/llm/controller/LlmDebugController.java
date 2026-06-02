package com.careermate.llm.controller;

import com.careermate.common.api.ApiResponse;
import com.careermate.llm.LlmClient;
import com.careermate.llm.controller.dto.LlmDebugChatRequest;
import com.careermate.llm.dto.ChatMessage;
import com.careermate.llm.dto.ChatRequest;
import com.careermate.llm.dto.ChatResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/debug/llm")
public class LlmDebugController {

    private final LlmClient llmClient;

    public LlmDebugController(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    @PostMapping("/chat")
    public ApiResponse<ChatResponse> chat(@Valid @RequestBody LlmDebugChatRequest request) {
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(List.of(
                        ChatMessage.builder()
                                .role("system")
                                .content("你是 CareerMate 求职智能体。")
                                .build(),
                        ChatMessage.builder()
                                .role("user")
                                .content(request.getMessage())
                                .build()
                ))
                .build();
        return ApiResponse.success(llmClient.chat(chatRequest));
    }
}
