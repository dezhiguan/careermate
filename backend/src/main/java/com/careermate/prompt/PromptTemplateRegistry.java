package com.careermate.prompt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class PromptTemplateRegistry {

    private static final String MANIFEST_PATH = "prompts/prompt-manifest.json";

    private final Map<String, Map<String, PromptTemplate>> templates = new LinkedHashMap<>();
    private final Map<String, String> manifestActiveVersions = new LinkedHashMap<>();

    public PromptTemplateRegistry() {
        load();
    }

    public PromptTemplate get(String promptId, String version) {
        if (promptId == null || promptId.isBlank()) {
            throw new PromptTemplateNotFoundException("Prompt id must not be blank");
        }
        if (version == null || version.isBlank()) {
            throw new PromptTemplateNotFoundException("Prompt version must not be blank: " + promptId);
        }
        Map<String, PromptTemplate> versions = templates.get(promptId);
        if (versions == null) {
            throw new PromptTemplateNotFoundException("Prompt not found: " + promptId);
        }
        PromptTemplate template = versions.get(version);
        if (template == null) {
            throw new PromptTemplateNotFoundException("Prompt version not found: " + promptId + "/" + version);
        }
        return template;
    }

    public String getManifestActiveVersion(String promptId) {
        String version = manifestActiveVersions.get(promptId);
        if (version == null || version.isBlank()) {
            throw new PromptTemplateNotFoundException("Prompt not found: " + promptId);
        }
        return version;
    }

    private void load() {
        try (InputStream manifestStream = classLoader().getResourceAsStream(MANIFEST_PATH)) {
            if (manifestStream == null) {
                throw new IllegalStateException("Missing prompt manifest: " + MANIFEST_PATH);
            }
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(manifestStream);
            JsonNode prompts = root.get("prompts");
            if (prompts == null || !prompts.isObject()) {
                throw new IllegalStateException("Invalid prompt manifest: missing prompts");
            }
            prompts.fields().forEachRemaining(entry -> {
                String promptId = entry.getKey();
                JsonNode config = entry.getValue();
                String activeVersion = textValue(config, "activeVersion");
                manifestActiveVersions.put(promptId, activeVersion);
                JsonNode versions = config.get("versions");
                if (versions == null || !versions.isArray()) {
                    throw new IllegalStateException("Invalid prompt manifest for " + promptId + ": missing versions");
                }
                Map<String, PromptTemplate> versionMap = new LinkedHashMap<>();
                for (JsonNode versionNode : versions) {
                    String version = versionNode.asText();
                    String content = loadTemplateContent(promptId, version);
                    versionMap.put(version, new PromptTemplate(promptId, version, content));
                }
                templates.put(promptId, versionMap);
            });
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load prompt manifest", e);
        }
    }

    private String loadTemplateContent(String promptId, String version) {
        String path = "prompts/" + promptId + "/" + version + ".md";
        try (InputStream stream = classLoader().getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing prompt template: " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load prompt template: " + path, e);
        }
    }

    private static String textValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.asText().isBlank()) {
            throw new IllegalStateException("Invalid prompt manifest: missing " + field);
        }
        return value.asText();
    }

    private ClassLoader classLoader() {
        return Thread.currentThread().getContextClassLoader();
    }
}
