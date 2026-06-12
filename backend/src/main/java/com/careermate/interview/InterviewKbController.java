package com.careermate.interview;

import com.careermate.common.api.ApiResponse;
import com.careermate.interview.dto.CompanyPrepVO;
import com.careermate.interview.dto.KbQuestionsVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/interview")
public class InterviewKbController {

    private final InterviewKbService interviewKbService;

    public InterviewKbController(InterviewKbService interviewKbService) {
        this.interviewKbService = interviewKbService;
    }

    @GetMapping("/kb-questions")
    public ApiResponse<KbQuestionsVO> kbQuestions(
            @RequestParam(defaultValue = "Java后端") String query
    ) {
        return ApiResponse.success(interviewKbService.getKbQuestions(query));
    }

    @GetMapping("/company-prep")
    public ApiResponse<CompanyPrepVO> companyPrep(@RequestParam String company) {
        return ApiResponse.success(interviewKbService.getCompanyPrep(company));
    }
}
