package com.careermate.interview;

import com.careermate.common.api.ApiResponse;
import com.careermate.interview.controller.InterviewQuestionController;
import com.careermate.interview.dto.JdAwareQuestionsVO;
import com.careermate.interview.service.InterviewQuestionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewQuestionControllerTest {

    @Mock
    private InterviewQuestionService service;

    @Test
    void jdAwareQuestionsDelegatesAndWraps() {
        JdAwareQuestionsVO vo = new JdAwareQuestionsVO();
        vo.setJdDocId(599L);
        vo.setDataAvailable(true);
        when(service.generateJdAwareQuestions(eq(599L), any())).thenReturn(vo);

        InterviewQuestionController controller = new InterviewQuestionController(service);
        ApiResponse<JdAwareQuestionsVO> response = controller.jdAwareQuestions(599L);

        assertNotNull(response.getData());
        assertEquals(599L, response.getData().getJdDocId());
        assertTrue(response.getData().isDataAvailable());
    }
}
