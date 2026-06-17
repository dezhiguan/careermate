package com.careermate.prompt;

import org.springframework.stereotype.Service;

@Service
public class PromptTemplateService {

    private final PromptTemplateRegistry registry;
    private final PromptProperties properties;

    public PromptTemplateService(PromptTemplateRegistry registry, PromptProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    public PromptRenderResult render(String promptId) {
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
