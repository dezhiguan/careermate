package com.careermate.keel;

import com.careermate.llm.dto.*;
import com.careermate.llm.StreamCallback;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.function.Consumer;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class KeelLlmClientTest {
    final ObjectMapper json = new ObjectMapper();
    final KeelRuntimeClient runtime = mock(KeelRuntimeClient.class);
    final KeelLlmClient client = new KeelLlmClient(runtime, json);

    @Test void governedModeRejectsDirectProviderAndFrameworkBypasses() {
        var actual = new KeelRuntimeClient(json, "http://runtime.invalid", "service");
        actual.verifyGovernedConfiguration("keel", false, false);
        assertThatThrownBy(() -> actual.verifyGovernedConfiguration("qwen", false, false)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> actual.verifyGovernedConfiguration("keel", true, false)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> actual.verifyGovernedConfiguration("keel", false, true)).isInstanceOf(IllegalStateException.class);
    }

    @Test void routesNativeMessagesAndPromptVersionThroughSdk() throws Exception {
        KeelPromptEvidence.remember("managed-template", "agent-base", 7);
        when(runtime.post(eq("llm"), any())).thenReturn(json.readTree("{\"content\":\"回答\",\"model\":\"chat-default\"}"));
        var result = client.chat(ChatRequest.builder().messages(List.of(
                ChatMessage.builder().role("system").content("managed-template context").build(),
                ChatMessage.builder().role("agent").content("prior").build())).build());
        assertThat(result.getContent()).isEqualTo("回答");
        assertThat(result.getProvider()).isEqualTo("keel");
        var captured = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(runtime).post(eq("llm"), captured.capture());
        assertThat(captured.getValue()).containsEntry("promptName", "agent-base").containsEntry("promptVersion", 7);
        assertThat(captured.getValue().get("messages").toString()).contains("assistant");
        assertThat(result.getInputTokens()).isNull(); // Unknown usage must not be fabricated as zero.
    }

    @Test void streamingPreservesTokensAndTerminalResponse() throws Exception {
        var callback = mock(StreamCallback.class);
        doAnswer(call -> {
            Consumer<com.fasterxml.jackson.databind.JsonNode> consumer = call.getArgument(1);
            consumer.accept(json.readTree("{\"token\":\"你\"}"));
            consumer.accept(json.readTree("{\"token\":\"好\"}"));
            consumer.accept(json.readTree("{\"final\":{\"content\":\"你好\",\"model\":\"chat-default\"}}"));
            return null;
        }).when(runtime).stream(any(), any());
        client.streamChat(ChatRequest.builder().messages(List.of(ChatMessage.builder().role("user").content("hi").build())).build(), callback);
        var order = inOrder(callback);
        order.verify(callback).onToken("你");
        order.verify(callback).onToken("好");
        order.verify(callback).onComplete(any());
        verify(callback, never()).onError(any());
    }

    @Test void runtimeFailureIsNotReplacedByMockAnswer() {
        when(runtime.post(eq("llm"), any())).thenThrow(new IllegalStateException("unavailable"));
        assertThatThrownBy(() -> client.chat(ChatRequest.builder().messages(List.of(
                ChatMessage.builder().role("user").content("hi").build())).build())).isInstanceOf(IllegalStateException.class);
    }
}
