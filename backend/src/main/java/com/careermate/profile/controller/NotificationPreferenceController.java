package com.careermate.profile.controller;

import com.careermate.common.api.ApiResponse;
import com.careermate.mapper.NotificationPreferenceMapper;
import com.careermate.model.entity.NotificationPreferenceEntity;
import com.careermate.security.CurrentUserContext;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 通知偏好读写（设计 06 我的·设置卡「通知偏好」）。prefs 键由前端约定。 */
@RestController
@RequestMapping("/api/user/notification-preferences")
public class NotificationPreferenceController {

    private final NotificationPreferenceMapper mapper;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public NotificationPreferenceController(NotificationPreferenceMapper mapper,
                                            com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> get() {
        Long userId = CurrentUserContext.getUserId();
        if (userId == null) {
            return ApiResponse.success(Map.of());
        }
        NotificationPreferenceEntity e = mapper.selectById(userId);
        return ApiResponse.success(parse(e == null ? null : e.getPrefs()));
    }

    @PutMapping
    public ApiResponse<Map<String, Object>> update(@RequestBody Map<String, Object> prefs) {
        Long userId = CurrentUserContext.getUserId();
        if (userId == null) {
            return ApiResponse.success(Map.of());
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(prefs == null ? Map.of() : prefs);
        } catch (Exception ex) {
            json = "{}";
        }
        NotificationPreferenceEntity existing = mapper.selectById(userId);
        NotificationPreferenceEntity e = new NotificationPreferenceEntity();
        e.setUserId(userId);
        e.setPrefs(json);
        e.setUpdatedAt(OffsetDateTime.now());
        if (existing == null) {
            mapper.insert(e);
        } else {
            mapper.updateById(e);
        }
        return ApiResponse.success(parse(json));
    }

    private Map<String, Object> parse(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }
}
