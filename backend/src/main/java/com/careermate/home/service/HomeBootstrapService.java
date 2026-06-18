package com.careermate.home.service;

import com.careermate.auth.dto.CurrentUserResponse;
import com.careermate.auth.service.AuthService;
import com.careermate.common.api.PageResult;
import com.careermate.home.dto.HomeBootstrapResponse;
import com.careermate.opportunity.dto.OpportunityListItemVO;
import com.careermate.opportunity.dto.OpportunityListRequest;
import com.careermate.opportunity.service.OpportunityService;
import com.careermate.profile.dto.CareerProfileResponse;
import com.careermate.profile.service.CareerProfileService;
import com.careermate.resume.dto.ResumeListItemResponse;
import com.careermate.resume.service.ResumeService;
import com.careermate.security.CurrentUserContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class HomeBootstrapService {

    private final AuthService authService;
    private final CareerProfileService careerProfileService;
    private final ResumeService resumeService;
    private final OpportunityService opportunityService;

    public HomeBootstrapService(
            AuthService authService,
            CareerProfileService careerProfileService,
            ResumeService resumeService,
            OpportunityService opportunityService
    ) {
        this.authService = authService;
        this.careerProfileService = careerProfileService;
        this.resumeService = resumeService;
        this.opportunityService = opportunityService;
    }

    @Transactional(readOnly = true)
    public HomeBootstrapResponse bootstrap() {
        Long userId = CurrentUserContext.getUserId();
        CurrentUserResponse user = authService.currentUser();
        CareerProfileResponse careerProfile = careerProfileService.getProfile(userId);
        ResumeListItemResponse defaultResume = resumeService.getDefaultActiveResumeItem(userId).orElse(null);
        String mode = defaultResume == null ? "demo" : null;
        PageResult<OpportunityListItemVO> opportunities = opportunityService.listCached(
                userId,
                new OpportunityListRequest(null, null, null, mode, 1, 10)
        );

        return HomeBootstrapResponse.builder()
                .user(user)
                .careerProfile(careerProfile)
                .defaultResume(defaultResume)
                .topOpportunities(opportunities == null ? List.of() : opportunities.items())
                .build();
    }
}
