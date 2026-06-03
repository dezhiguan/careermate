package com.careermate.agent.context;

import com.careermate.jobmatch.JobMatchJsonSupport;
import com.careermate.model.entity.CareerProfileEntity;
import com.careermate.profile.CareerProfileService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CareerProfileContextProvider {

    private static final String PROFILE_HEADER = "【用户求职画像】";

    private final CareerProfileService careerProfileService;
    private final JobMatchJsonSupport jobMatchJsonSupport;

    public CareerProfileContextProvider(
            CareerProfileService careerProfileService,
            JobMatchJsonSupport jobMatchJsonSupport
    ) {
        this.careerProfileService = careerProfileService;
        this.jobMatchJsonSupport = jobMatchJsonSupport;
    }

    public CareerProfileContextResult load(Long userId) {
        if (userId == null) {
            return CareerProfileContextResult.empty();
        }
        CareerProfileEntity entity = careerProfileService.findEntityByUserId(userId);
        if (entity == null || !hasProfileContent(entity)) {
            return CareerProfileContextResult.empty();
        }
        List<String> skills = jobMatchJsonSupport.readStringList(entity.getSkillKeywords());
        String contextText = buildContextText(entity, skills);
        return CareerProfileContextResult.builder()
                .available(true)
                .contextText(contextText)
                .targetRole(entity.getTargetRole())
                .skillCount(skills == null ? 0 : skills.size())
                .build();
    }

    private boolean hasProfileContent(CareerProfileEntity entity) {
        return isNotBlank(entity.getTargetRole())
                || isNotBlank(entity.getTargetCity())
                || isNotBlank(entity.getSeniority())
                || isNotBlank(entity.getWorkMode())
                || isNotBlank(entity.getPreferenceSummary())
                || !jobMatchJsonSupport.readStringList(entity.getSkillKeywords()).isEmpty();
    }

    private String buildContextText(CareerProfileEntity entity, List<String> skills) {
        StringBuilder sb = new StringBuilder(PROFILE_HEADER);
        appendLine(sb, "目标岗位", entity.getTargetRole());
        appendLine(sb, "目标城市", entity.getTargetCity());
        appendLine(sb, "经验阶段", entity.getSeniority());
        appendLine(sb, "工作偏好", entity.getWorkMode());
        appendLine(sb, "技能关键词", joinSkills(skills));
        appendLine(sb, "偏好说明", entity.getPreferenceSummary());
        return sb.toString().trim();
    }

    private void appendLine(StringBuilder sb, String label, String value) {
        if (!isNotBlank(value)) {
            return;
        }
        sb.append('\n').append(label).append('：').append(value.trim());
    }

    private String joinSkills(List<String> skills) {
        if (skills == null || skills.isEmpty()) {
            return null;
        }
        return String.join(", ", skills);
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
