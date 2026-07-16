package com.careermate.interview;

import com.careermate.agent.tool.AgentToolContext;
import com.careermate.agent.tool.AgentToolDomain;
import com.careermate.agent.tool.AgentToolResult;
import com.careermate.agent.tool.GenerateJdAwareInterviewQuestionsTool;
import com.careermate.interview.dto.JdAwareQuestionsVO;
import com.careermate.interview.service.InterviewQuestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenerateJdAwareInterviewQuestionsToolTest {

    @Mock
    private InterviewQuestionService service;

    private GenerateJdAwareInterviewQuestionsTool tool;

    @BeforeEach
    void setUp() {
        tool = new GenerateJdAwareInterviewQuestionsTool(service);
    }

    @Test
    void metadataIsWellFormed() {
        assertEquals("generate_jd_aware_questions", tool.name());
        assertEquals(AgentToolDomain.INTERVIEW, tool.definition().getDomain());
        assertEquals(2, tool.definition().getParameters().size());
        assertTrue(tool.supports(AgentToolContext.builder().build()));
        assertTrue(tool.description().contains("面试题"));
    }

    @Test
    void executeReturnsSuccessWithQuestions() {
        JdAwareQuestionsVO vo = new JdAwareQuestionsVO();
        vo.setJdDocId(599L);
        vo.setJdTitle("字节-后端");
        vo.setDataAvailable(true);
        JdAwareQuestionsVO.JdAwareQuestion q = new JdAwareQuestionsVO.JdAwareQuestion();
        q.setQuestionText("缓存一致性");
        vo.setQuestions(List.of(q));
        when(service.generateJdAwareQuestions(eq(599L), isNull(), isNull())).thenReturn(vo);

        AgentToolResult result = tool.execute(AgentToolContext.builder()
                .args(Map.of("jdDocId", "599"))
                .build());

        assertTrue(result.isSuccess());
        assertEquals(1, result.getData().get("questionCount"));
        assertEquals("字节-后端", result.getData().get("jdTitle"));
        assertTrue(result.getSummary().contains("1 道"));
    }

    @Test
    void executePassesCompanyThrough() {
        JdAwareQuestionsVO vo = new JdAwareQuestionsVO();
        vo.setJdDocId(599L);
        vo.setJdTitle("字节-后端");
        vo.setDataAvailable(true);
        vo.setQuestions(List.of());
        when(service.generateJdAwareQuestions(eq(599L), isNull(), eq("字节跳动"))).thenReturn(vo);

        AgentToolResult result = tool.execute(AgentToolContext.builder()
                .args(Map.of("jdDocId", "599", "company", "字节跳动"))
                .build());

        assertTrue(result.isSuccess());
    }

    @Test
    void executeFlagsNoDataWhenJdMissing() {
        JdAwareQuestionsVO vo = new JdAwareQuestionsVO();
        vo.setDataAvailable(false);
        when(service.generateJdAwareQuestions(eq(700L), isNull(), isNull())).thenReturn(vo);

        AgentToolResult result = tool.execute(AgentToolContext.builder()
                .args(Map.of("jdDocId", "700"))
                .build());

        assertTrue(result.isSuccess());
        assertTrue(result.getSummary().contains("未找到"));
        assertEquals(false, result.getData().get("dataAvailable"));
    }

    @Test
    void executeFailsOnInvalidOrMissingJdDocId() {
        AgentToolResult bad = tool.execute(AgentToolContext.builder()
                .args(Map.of("jdDocId", "abc"))
                .build());
        assertFalse(bad.isSuccess());

        AgentToolResult missing = tool.execute(AgentToolContext.builder().build());
        assertFalse(missing.isSuccess());
    }
}
