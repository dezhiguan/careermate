package com.careermate.resume.service;

import com.careermate.profile.dto.CareerProfileResponse;
import com.careermate.profile.dto.CareerProfileUpsertRequest;
import com.careermate.profile.service.CareerProfileService;
import com.careermate.resume.service.ResumeProfileExtractor.ExtractedProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 简历上传后自动回填职业画像（评审 P0-1）。
 *
 * <p>核心约束：<b>只填画像里当前为空的字段，绝不覆盖用户已手填的值</b>。
 * 依赖 {@link CareerProfileService#upsertProfile} 的合并语义（仅应用请求中非 null 字段），
 * 因此只在请求里 set「当前为空且抽取到值」的字段。抽取失败或无可填字段时无副作用。
 */
@Slf4j
@Service
public class ResumeProfileAutoFillService {

    private final ResumeProfileExtractor extractor;
    private final CareerProfileService careerProfileService;

    public ResumeProfileAutoFillService(ResumeProfileExtractor extractor,
                                        CareerProfileService careerProfileService) {
        this.extractor = extractor;
        this.careerProfileService = careerProfileService;
    }

    /**
     * @return 实际被填充的字段名列表；空列表表示无变更（抽取失败、或字段均已由用户填写）。
     */
    public List<String> autoFill(Long userId, String resumeContent) {
        if (userId == null) {
            return List.of();
        }
        Optional<ExtractedProfile> opt = extractor.extract(resumeContent);
        if (opt.isEmpty()) {
            return List.of();
        }
        ExtractedProfile ex = opt.get();
        CareerProfileResponse current = careerProfileService.getProfile(userId);
        CareerProfileUpsertRequest req = new CareerProfileUpsertRequest();
        List<String> filled = new ArrayList<>();

        if (isBlank(current.getTargetRole()) && notBlank(ex.targetRole())) {
            req.setTargetRole(ex.targetRole().trim());
            filled.add("targetRole");
        }
        if (isBlank(current.getTargetCity()) && notBlank(ex.targetCity())) {
            req.setTargetCity(ex.targetCity().trim());
            filled.add("targetCity");
        }
        if (isBlank(current.getSeniority()) && notBlank(ex.seniority())) {
            req.setSeniority(ex.seniority().trim());
            filled.add("seniority");
        }
        if (isBlank(current.getWorkMode()) && notBlank(ex.workMode())) {
            req.setWorkMode(ex.workMode().trim());
            filled.add("workMode");
        }
        if (isEmpty(current.getSkillKeywords()) && ex.skillKeywords() != null && !ex.skillKeywords().isEmpty()) {
            req.setSkillKeywords(ex.skillKeywords());
            filled.add("skillKeywords");
        }

        if (filled.isEmpty()) {
            return List.of();
        }
        careerProfileService.upsertProfile(userId, req, "resume_autofill");
        log.info("resume auto-fill profile: userId={}, filled={}", userId, filled);
        return filled;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static boolean isEmpty(List<?> list) {
        return list == null || list.isEmpty();
    }
}
