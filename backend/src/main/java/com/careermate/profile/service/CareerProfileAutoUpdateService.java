package com.careermate.profile.service;

import com.careermate.profile.CareerProfileTargetRoleExtractor;
import com.careermate.profile.CareerProfileUpdateResult;
import org.springframework.stereotype.Service;

@Service
public class CareerProfileAutoUpdateService {

    private final CareerProfileTargetRoleExtractor targetRoleExtractor;
    private final CareerProfileService careerProfileService;

    public CareerProfileAutoUpdateService(
            CareerProfileTargetRoleExtractor targetRoleExtractor,
            CareerProfileService careerProfileService
    ) {
        this.targetRoleExtractor = targetRoleExtractor;
        this.careerProfileService = careerProfileService;
    }

    public CareerProfileUpdateResult tryAutoUpdateTargetRole(Long userId, String userMessage) {
        return targetRoleExtractor.extract(userMessage)
                .map(role -> careerProfileService.updateTargetRoleFromAgent(userId, role))
                .orElseGet(CareerProfileUpdateResult::notUpdated);
    }
}
