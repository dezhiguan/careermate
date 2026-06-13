package com.careermate.profile.controller;

import com.careermate.common.api.ApiResponse;
import com.careermate.profile.dto.CareerProfileResponse;
import com.careermate.profile.dto.CareerProfileUpsertRequest;
import com.careermate.profile.service.CareerProfileService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
public class CareerProfileController {

    private final CareerProfileService careerProfileService;

    public CareerProfileController(CareerProfileService careerProfileService) {
        this.careerProfileService = careerProfileService;
    }

    @GetMapping("/career")
    public ApiResponse<CareerProfileResponse> getCareerProfile() {
        return ApiResponse.success(careerProfileService.getCurrentUserProfile());
    }

    @PutMapping("/career")
    public ApiResponse<CareerProfileResponse> upsertCareerProfile(@RequestBody CareerProfileUpsertRequest request) {
        return ApiResponse.success(careerProfileService.upsertCurrentUserProfile(request));
    }
}
