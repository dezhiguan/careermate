package com.careermate.home.dto;

import com.careermate.auth.dto.CurrentUserResponse;
import com.careermate.opportunity.dto.OpportunityListItemVO;
import com.careermate.profile.dto.CareerProfileResponse;
import com.careermate.resume.dto.ResumeListItemResponse;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class HomeBootstrapResponse {

    private CurrentUserResponse user;
    private CareerProfileResponse careerProfile;
    private ResumeListItemResponse defaultResume;
    private List<OpportunityListItemVO> topOpportunities;
}
