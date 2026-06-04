package com.careermate.health;

import com.careermate.common.api.ApiResponse;
import com.careermate.llm.LlmProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final LlmProperties llmProperties;

    public HealthController(LlmProperties llmProperties) {
        this.llmProperties = llmProperties;
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, String>> health() {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("service", "careermate-backend");
        data.put("version", "0.1.0");
        data.put("llmProvider", safeProvider());
        data.put("llmModel", safeModel());
        data.put("llmApiKeyConfigured", apiKeyConfigured() ? "true" : "false");
        return ApiResponse.success(data);
    }

    private String safeProvider() {
        String provider = llmProperties.getProvider();
        return provider == null || provider.isBlank() ? "mock" : provider.trim();
    }

    private String safeModel() {
        String model = llmProperties.getModel();
        return model == null || model.isBlank() ? "mock-chat" : model.trim();
    }

    private boolean apiKeyConfigured() {
        String key = llmProperties.getApiKey();
        if (key == null || key.isBlank()) {
            return false;
        }
        String trimmed = key.trim();
        return !trimmed.startsWith("your_") && !"change-me".equalsIgnoreCase(trimmed);
    }
}
