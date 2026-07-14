package com.careermate.resume.coldstart;

import java.util.List;

import org.springframework.stereotype.Service;

import com.careermate.agent.context.CareerProfileContextProvider;
import com.careermate.agent.context.CareerProfileContextResult;
import com.careermate.resume.dto.ResumeDetailResponse;
import com.careermate.resume.service.ResumeService;

import lombok.extern.slf4j.Slf4j;

/**
 * 无上传冷启动建档编排（P1）。
 *
 * <p>按用户已有信息量降级：有画像（目标岗位）走 <b>L2</b> 预填，什么都没有走 <b>L3</b>
 * 默认引导占位骨架。产物均为 {@code DRAFT_SKELETON}，落库后引导用户到 Canvas 填充。
 *
 * <p>L1（用户在对话中提供真实经历）由生成链路的 LLM 建档承担，不在本编排内。
 */
@Slf4j
@Service
public class ColdStartResumeService {

    private final ResumeService resumeService;
    private final DefaultResumeSkeletonBuilder skeletonBuilder;
    private final CareerProfileContextProvider profileProvider;

    public ColdStartResumeService(ResumeService resumeService,
                                  DefaultResumeSkeletonBuilder skeletonBuilder,
                                  CareerProfileContextProvider profileProvider) {
        this.resumeService = resumeService;
        this.skeletonBuilder = skeletonBuilder;
        this.profileProvider = profileProvider;
    }

    /** 为指定用户建一份冷启动初版简历并落库。 */
    public ResumeDetailResponse createForUser(Long userId) {
        return resumeService.createColdStartResume(userId, build(userId));
    }

    /** 决定 L2/L3 并构建骨架（不落库，便于单测）。 */
    ColdStartResume build(Long userId) {
        CareerProfileContextResult profile = safeLoadProfile(userId);
        if (profile != null && profile.isAvailable() && hasText(profile.getTargetRole())) {
            // L2：仅有画像 → 用目标岗位预填，其余留引导占位
            return skeletonBuilder.buildFromProfile(profile.getTargetRole(), List.of());
        }
        // L3：什么都没有 → 系统默认引导占位骨架
        return skeletonBuilder.buildDefaultSkeleton();
    }

    private CareerProfileContextResult safeLoadProfile(Long userId) {
        try {
            return profileProvider.load(userId);
        } catch (Exception e) {
            log.warn("cold-start load profile failed, fallback to L3: {}", e.getMessage());
            return null;
        }
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
