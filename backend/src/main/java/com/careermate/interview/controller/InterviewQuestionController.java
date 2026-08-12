package com.careermate.interview.controller;

import com.careermate.common.api.ApiResponse;
import com.careermate.common.exception.BizException;
import com.careermate.common.support.JdDocIds;
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
            @RequestParam String jdDocId,
            @RequestParam(required = false) String company) {
        return ApiResponse.success(interviewQuestionService.generateJdAwareQuestions(
                requireJdDocId(jdDocId), CurrentUserContext.getUserId(), company));
    }

    /** 兼容 "doc-89840" 与 89840 两种写法；都解析不出才报参数错误。 */
    private static Long requireJdDocId(String raw) {
        Long docId = JdDocIds.parse(raw);
        if (docId == null) {
            throw new BizException(400, "参数格式不正确：jdDocId");
        }
        return docId;
    }
}
