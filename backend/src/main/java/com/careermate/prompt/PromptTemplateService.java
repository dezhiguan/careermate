package com.careermate.prompt;

import org.springframework.stereotype.Service;

@Service
public class PromptTemplateService {

    private final PromptTemplateRegistry registry;
    private final PromptProperties properties;
    private com.careermate.keel.KeelRuntimeClient runtime;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setRuntime(com.careermate.keel.KeelRuntimeClient runtime) {
        this.runtime = runtime;
    }

    public PromptTemplateService(PromptTemplateRegistry registry, PromptProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    public PromptRenderResult render(String promptId) {
        if (runtime != null) {
            var prompt = runtime.post("prompt", java.util.Map.of("name", promptId));
            if (prompt.path("fallback").asBoolean() || !prompt.hasNonNull("version")) {
                throw new IllegalStateException("Managed prompt version is required");
            }
            com.careermate.keel.KeelPromptEvidence.remember(prompt.path("content").asText(), promptId, prompt.get("version").asInt());
            return new PromptRenderResult(promptId, "langfuse:" + prompt.get("version").asText(), prompt.path("content").asText());
        }
        String version = resolveActiveVersion(promptId);
        PromptTemplate template = registry.get(promptId, version);
        return new PromptRenderResult(template.promptId(), template.version(), template.content());
    }

    String resolveActiveVersion(String promptId) {
        String configured = properties.getActiveVersions().get(promptId);
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        return registry.getManifestActiveVersion(promptId);
    }
}
