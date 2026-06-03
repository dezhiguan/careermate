package com.careermate.profile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.careermate.jobmatch.JobMatchJsonSupport;
import com.careermate.mapper.CareerProfileMapper;
import com.careermate.model.entity.CareerProfileEntity;
import com.careermate.profile.dto.CareerProfileResponse;
import com.careermate.profile.dto.CareerProfileUpsertRequest;
import com.careermate.security.CurrentUserContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
public class CareerProfileService {

    private final CareerProfileMapper careerProfileMapper;
    private final JobMatchJsonSupport jobMatchJsonSupport;

    public CareerProfileService(CareerProfileMapper careerProfileMapper, JobMatchJsonSupport jobMatchJsonSupport) {
        this.careerProfileMapper = careerProfileMapper;
        this.jobMatchJsonSupport = jobMatchJsonSupport;
    }

    public CareerProfileResponse getCurrentUserProfile() {
        return getProfile(CurrentUserContext.getUserId());
    }

    public CareerProfileResponse getProfile(Long userId) {
        if (userId == null) {
            return emptyResponse();
        }
        CareerProfileEntity entity = findByUserId(userId);
        if (entity == null) {
            return emptyResponse();
        }
        return toResponse(entity);
    }

    @Transactional
    public CareerProfileResponse upsertCurrentUserProfile(CareerProfileUpsertRequest request) {
        Long userId = CurrentUserContext.getUserId();
        return upsertProfile(userId, request, "manual");
    }

    @Transactional
    public CareerProfileResponse upsertProfile(Long userId, CareerProfileUpsertRequest request, String source) {
        if (userId == null) {
            return emptyResponse();
        }
        CareerProfileUpsertRequest safeRequest = request == null ? new CareerProfileUpsertRequest() : request;
        LocalDateTime now = LocalDateTime.now();
        CareerProfileEntity entity = findByUserId(userId);
        if (entity == null) {
            entity = new CareerProfileEntity();
            entity.setUserId(userId);
            entity.setSkillKeywords(jobMatchJsonSupport.writeStringList(Collections.emptyList()));
            entity.setSource(source == null ? "agent" : source);
            entity.setCreatedAt(now);
            applyRequest(entity, safeRequest, source);
            entity.setUpdatedAt(now);
            careerProfileMapper.insert(entity);
        } else {
            applyRequest(entity, safeRequest, source);
            entity.setUpdatedAt(now);
            careerProfileMapper.updateById(entity);
        }
        return toResponse(entity);
    }

    @Transactional
    public CareerProfileUpdateResult updateTargetRoleFromAgent(Long userId, String targetRole) {
        if (userId == null || targetRole == null || targetRole.isBlank()) {
            return CareerProfileUpdateResult.notUpdated();
        }
        String normalized = normalize(targetRole, 120);
        CareerProfileUpsertRequest request = new CareerProfileUpsertRequest();
        request.setTargetRole(normalized);
        upsertProfile(userId, request, "agent");
        return CareerProfileUpdateResult.updated(List.of("targetRole"), normalized);
    }

    public CareerProfileEntity findEntityByUserId(Long userId) {
        return findByUserId(userId);
    }

    private CareerProfileEntity findByUserId(Long userId) {
        return careerProfileMapper.selectOne(
                new LambdaQueryWrapper<CareerProfileEntity>()
                        .eq(CareerProfileEntity::getUserId, userId)
                        .last("LIMIT 1")
        );
    }

    private void applyRequest(CareerProfileEntity entity, CareerProfileUpsertRequest request, String source) {
        if (request.getTargetRole() != null) {
            entity.setTargetRole(normalize(request.getTargetRole(), 120));
        }
        if (request.getTargetCity() != null) {
            entity.setTargetCity(normalize(request.getTargetCity(), 120));
        }
        if (request.getSeniority() != null) {
            entity.setSeniority(normalize(request.getSeniority(), 80));
        }
        if (request.getWorkMode() != null) {
            entity.setWorkMode(normalize(request.getWorkMode(), 80));
        }
        if (request.getSkillKeywords() != null) {
            entity.setSkillKeywords(jobMatchJsonSupport.writeStringList(request.getSkillKeywords()));
        }
        if (request.getPreferenceSummary() != null) {
            entity.setPreferenceSummary(normalize(request.getPreferenceSummary(), 500));
        }
        if (source != null && !source.isBlank()) {
            entity.setSource(source);
        }
    }

    private CareerProfileResponse toResponse(CareerProfileEntity entity) {
        List<String> skills = jobMatchJsonSupport.readStringList(entity.getSkillKeywords());
        return CareerProfileResponse.builder()
                .targetRole(entity.getTargetRole())
                .targetCity(entity.getTargetCity())
                .seniority(entity.getSeniority())
                .workMode(entity.getWorkMode())
                .skillKeywords(skills)
                .preferenceSummary(entity.getPreferenceSummary())
                .source(entity.getSource())
                .build();
    }

    private CareerProfileResponse emptyResponse() {
        return CareerProfileResponse.builder()
                .skillKeywords(Collections.emptyList())
                .build();
    }

    private String normalize(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= maxLen ? trimmed : trimmed.substring(0, maxLen);
    }
}
