package com.careermate.home.controller;

import com.careermate.common.api.ApiResponse;
import com.careermate.home.dto.HomeBootstrapResponse;
import com.careermate.home.service.HomeBootstrapService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/home")
public class HomeBootstrapController {

    private final HomeBootstrapService homeBootstrapService;

    public HomeBootstrapController(HomeBootstrapService homeBootstrapService) {
        this.homeBootstrapService = homeBootstrapService;
    }

    @GetMapping("/bootstrap")
    public ApiResponse<HomeBootstrapResponse> bootstrap() {
        return ApiResponse.success(homeBootstrapService.bootstrap());
    }
}
