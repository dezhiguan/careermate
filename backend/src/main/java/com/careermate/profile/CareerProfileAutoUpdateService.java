package com.careermate.profile;

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
