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
        // 头像是存成 data URL 的内嵌 base64（上限 600KB，实测带完整 EXIF 的 JPEG 约 300KB）。
        // 首屏 /auth/me 和 /home/bootstrap 都会被调到，各带一份，同一张图下载两遍，
        // 而 bootstrap 本就是首屏最慢的接口。这里去掉它，头像由 /auth/me 那份提供。
        CurrentUserResponse user = withoutAvatar(authService.currentUser());
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

    /** 复制一份不带头像的用户信息，避免污染 authService 返回的对象。 */
    private static CurrentUserResponse withoutAvatar(CurrentUserResponse user) {
        if (user == null || user.getAvatarUrl() == null) {
            return user;
        }
        CurrentUserResponse copy = new CurrentUserResponse();
        org.springframework.beans.BeanUtils.copyProperties(user, copy);
        copy.setAvatarUrl(null);
        return copy;
    }
}
