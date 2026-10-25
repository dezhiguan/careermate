package com.careermate.keel;

import com.careermate.agent.path.AgentPathMode;
import com.careermate.agent.tool.*;
import com.careermate.auth.identity.LocalUserMirror;
import com.careermate.security.CurrentUser;
import com.careermate.security.CurrentUserContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Only the authenticated SDK service may supply an already-authenticated auth_user_id. */
@RestController
@RequestMapping("/internal/keel/tools")
@ConditionalOnProperty(name = "careermate.keel.enabled", havingValue = "true")
public class KeelBusinessController {
    @ExceptionHandler(ResponseStatusException.class)
    public org.springframework.http.ResponseEntity<Map<String, Object>> rejected(ResponseStatusException error) {
        return org.springframework.http.ResponseEntity.status(error.getStatusCode()).body(Map.of(
                "status", error.getStatusCode().value(), "message", "CareerMate business access rejected"));
    }
    private final AgentToolRegistry registry;
    private final AgentToolExecutionService execution;
    private final LocalUserMirror users;
    private final String token;

    public KeelBusinessController(AgentToolRegistry registry, AgentToolExecutionService execution,
                                   LocalUserMirror users, @Value("${CAREERMATE_SERVICE_TOKEN}") String token) {
        this.registry = registry;
        this.execution = execution;
        this.users = users;
        this.token = token;
    }

    private void authenticate(String authorization) {
        if (token == null || token.isBlank() || authorization == null || !MessageDigest.isEqual(
                ("Bearer " + token).getBytes(StandardCharsets.UTF_8), authorization.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
    }

    @GetMapping
    public List<Map<String, Object>> definitions(@RequestHeader(value = "Authorization", required = false) String auth) {
        authenticate(auth);
        return registry.listDefinitions().stream().filter(AgentToolDefinition::isEnabled).map(d -> {
            var properties = new LinkedHashMap<String, Object>();
            var required = new ArrayList<String>();
            for (var p : d.getParameters()) {
                var field = new LinkedHashMap<String, Object>();
                field.put("type", p.getType().name().toLowerCase(Locale.ROOT));
                if (p.getDescription() != null) field.put("description", p.getDescription());
                if (p.getEnumValues() != null && !p.getEnumValues().isEmpty()) field.put("enum", p.getEnumValues());
                if (p.getType() == AgentToolParameterType.ARRAY) field.put("items", Map.of("type", "string"));
                properties.put(p.getName(), field);
                if (p.isRequired()) required.add(p.getName());
            }
            return Map.<String, Object>of("type", "function", "function", Map.of(
                    "name", d.getName(), "description", d.getDescription(), "parameters", Map.of(
                            "type", "object", "properties", properties, "required", required, "additionalProperties", false)));
        }).toList();
    }

    public record ToolRequest(Long authUserId, String sessionId, String userMessage, Map<String, Object> arguments) {}

    @PostMapping("/{name}")
    public AgentToolResult executeHttp(@RequestHeader(value = "Authorization", required = false) String auth,
                                      @PathVariable String name, @RequestBody ToolRequest body,
                                      jakarta.servlet.http.HttpServletRequest request) {
        authenticate(auth);
        var previous = org.slf4j.MDC.get("keelTraceParent");
        var incoming = request.getHeader("traceparent");
        try {
            if (KeelRuntimeClient.validTraceParent(incoming)) org.slf4j.MDC.put("keelTraceParent", incoming);
            else org.slf4j.MDC.remove("keelTraceParent");
            return execute(auth, name, body);
        } finally {
            if (previous == null) org.slf4j.MDC.remove("keelTraceParent");
            else org.slf4j.MDC.put("keelTraceParent", previous);
        }
    }

    public AgentToolResult execute(@RequestHeader(value = "Authorization", required = false) String auth,
                                   @PathVariable String name, @RequestBody ToolRequest body) {
        authenticate(auth);
        var user = users.findByAuthUserId(body.authUserId());
        if (user == null || !"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        var definition = registry.definitionOf(name).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!definition.isEnabled()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        var arguments = body.arguments() == null ? Map.<String, Object>of() : body.arguments();
        if (arguments.containsKey("userId") || arguments.containsKey("authUserId") || arguments.containsKey("sessionId")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        // Domain services that inspect CurrentUserContext get exactly the same owner.
        try {
            CurrentUserContext.set(CurrentUser.builder().userId(user.getId()).username(user.getUsername())
                    .role(user.getRole()).authUserKey(String.valueOf(body.authUserId())).authenticated(true).build());
            return execution.execute(AgentToolContext.builder().userId(user.getId())
                    .sessionId(body.sessionId()).userMessage(body.userMessage()).args(arguments)
                    .pathMode(AgentPathMode.DEEP).build(), name);
        } finally {
            CurrentUserContext.clear();
        }
    }
}
