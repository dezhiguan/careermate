package com.careermate.interview;

import com.careermate.common.api.ApiResponse;
import com.careermate.interview.dto.InterviewAnswerRequest;
import com.careermate.interview.dto.InterviewQuestionResponse;
import com.careermate.interview.dto.InterviewSessionCreateRequest;
import com.careermate.interview.dto.InterviewSessionDetailResponse;
import com.careermate.interview.dto.InterviewSessionListItemResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/interview-sessions")
public class InterviewPracticeController {

    private final InterviewPracticeService interviewPracticeService;

    public InterviewPracticeController(InterviewPracticeService interviewPracticeService) {
        this.interviewPracticeService = interviewPracticeService;
    }

    @GetMapping
    public ApiResponse<List<InterviewSessionListItemResponse>> list() {
        return ApiResponse.success(interviewPracticeService.listSessions());
    }

    @PostMapping
    public ApiResponse<InterviewSessionDetailResponse> create(
            @RequestBody(required = false) @Valid InterviewSessionCreateRequest request
    ) {
        return ApiResponse.success(interviewPracticeService.createSession(
                request == null ? new InterviewSessionCreateRequest() : request
        ));
    }

    @GetMapping("/{id}")
    public ApiResponse<InterviewSessionDetailResponse> get(@PathVariable Long id) {
        return ApiResponse.success(interviewPracticeService.getSession(id));
    }

    @PostMapping("/{id}/questions/{questionId}/answer")
    public ApiResponse<InterviewQuestionResponse> submitAnswer(
            @PathVariable Long id,
            @PathVariable Long questionId,
            @RequestBody @Valid InterviewAnswerRequest request
    ) {
        return ApiResponse.success(interviewPracticeService.submitAnswer(id, questionId, request));
    }

    @PostMapping("/{id}/complete")
    public ApiResponse<InterviewSessionDetailResponse> complete(@PathVariable Long id) {
        return ApiResponse.success(interviewPracticeService.completeSession(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        interviewPracticeService.deleteSession(id);
        return ApiResponse.success();
    }
}
