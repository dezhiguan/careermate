package com.careermate.interview.controller;

import com.careermate.common.api.ApiResponse;
import com.careermate.interview.dto.JdAwareQuestionsVO;
import com.careermate.interview.service.InterviewQuestionService;
import com.careermate.security.CurrentUserContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * JD-aware 面试题生成 REST 接口。
 */
@RestController
@RequestMapping("/api/interview")
public class InterviewQuestionController {

    private final InterviewQuestionService interviewQuestionService;

    public InterviewQuestionController(InterviewQuestionService interviewQuestionService) {
        this.interviewQuestionService = interviewQuestionService;
    }

    /**
     * 为一条 JD 生成针对性面试题（结合当前登录用户的默认简历，可选结合该公司面经）。
     *
     * @param jdDocId 目标 JD 的 RAGForge docId
     * @param company 公司名（可选）
     */
    @GetMapping("/jd-aware-questions")
    public ApiResponse<JdAwareQuestionsVO> jdAwareQuestions(
            @RequestParam Long jdDocId,
            @RequestParam(required = false) String company) {
        return ApiResponse.success(interviewQuestionService.generateJdAwareQuestions(
                jdDocId, CurrentUserContext.getUserId(), company));
    }
}
