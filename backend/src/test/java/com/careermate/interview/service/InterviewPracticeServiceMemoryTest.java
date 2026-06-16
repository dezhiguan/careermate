package com.careermate.interview.service;

import com.careermate.agent.memory.AgentMemoryService;
import com.careermate.interview.InterviewAnswerEvaluator;
import com.careermate.interview.dto.InterviewAnswerRequest;
import com.careermate.jobmatch.JobMatchJsonSupport;
import com.careermate.mapper.InterviewQuestionMapper;
import com.careermate.mapper.InterviewSessionMapper;
import com.careermate.mapper.JobMatchMapper;
import com.careermate.mapper.ResumeMapper;
import com.careermate.mapper.UserMapper;
import com.careermate.mapper.UserProfileMapper;
import com.careermate.model.entity.InterviewQuestionEntity;
import com.careermate.model.entity.InterviewSessionEntity;
import com.careermate.model.entity.ResumeEntity;
import com.careermate.resume.service.ResumeService;
import com.careermate.security.CurrentUser;
import com.careermate.security.CurrentUserContext;
import com.careermate.testsupport.TestUserSupport;
import com.careermate.testsupport.TestUsers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
class InterviewPracticeServiceMemoryTest {

    @Autowired
    private InterviewPracticeService interviewPracticeService;

    @Autowired
    private InterviewSessionMapper sessionMapper;

    @Autowired
    private InterviewQuestionMapper questionMapper;

    @Autowired
    private ResumeMapper resumeMapper;

    @Autowired
    private JobMatchJsonSupport jobMatchJsonSupport;

    @MockBean
    private InterviewAnswerEvaluator answerEvaluator;

    @MockBean
    private AgentMemoryService agentMemoryService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserProfileMapper userProfileMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JobMatchMapper jobMatchMapper;

    @BeforeEach
    void setUp() {
        TestUserSupport.ensureTestUsers(userMapper, userProfileMapper, passwordEncoder);
        TestUserSupport.cleanupUserBusinessData(
                resumeMapper, jobMatchMapper, sessionMapper, questionMapper);
        loginAs(TestUsers.USER_A);
    }

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    @Test
    void submitAnswerRemembersWeaknessOnLowScore() {
        long sessionId = insertSessionWithQuestion();

        org.mockito.Mockito.when(answerEvaluator.evaluate(any(), anyString(), anyList()))
                .thenReturn(new InterviewAnswerEvaluator.EvaluationResult(
                        65,
                        "需要补充缓存一致性说明",
                        List.of("表达清晰"),
                        List.of("Redis 双写一致性")
                ));

        InterviewAnswerRequest request = new InterviewAnswerRequest();
        request.setAnswerText("我使用 Redis 做缓存。");

        var response = interviewPracticeService.submitAnswer(sessionId, questionIdFor(sessionId), request);
        assertEquals("ANSWERED", response.getStatus());
        assertEquals(65, response.getScore());

        verify(agentMemoryService).rememberInterviewWeakness(
                anyLong(),
                anyInt(),
                anyString(),
                anyString(),
                anyList(),
                anyList()
        );
    }

    @Test
    void submitAnswerSucceedsWhenMemoryWriteFails() {
        long sessionId = insertSessionWithQuestion();

        org.mockito.Mockito.when(answerEvaluator.evaluate(any(), anyString(), anyList()))
                .thenReturn(new InterviewAnswerEvaluator.EvaluationResult(
                        55,
                        "回答不完整",
                        List.of(),
                        List.of("补充 JVM 调优经验")
                ));
        doThrow(new RuntimeException("memory down"))
                .when(agentMemoryService)
                .rememberInterviewWeakness(
                        anyLong(),
                        anyInt(),
                        anyString(),
                        anyString(),
                        anyList(),
                        anyList()
                );

        InterviewAnswerRequest request = new InterviewAnswerRequest();
        request.setAnswerText("简短回答");

        var response = interviewPracticeService.submitAnswer(sessionId, questionIdFor(sessionId), request);
        assertEquals("ANSWERED", response.getStatus());
        assertEquals(55, response.getScore());
    }

    private long insertSessionWithQuestion() {
        OffsetDateTime now = OffsetDateTime.now();
        ResumeEntity resume = new ResumeEntity();
        resume.setUserId(TestUsers.USER_A);
        resume.setTitle("memory_test_resume");
        resume.setContent("Java, Spring");
        resume.setSourceType(ResumeService.SOURCE_TYPE_TEXT);
        resume.setIsDefault(true);
        resume.setStatus(ResumeService.STATUS_ACTIVE);
        resume.setCreatedAt(now);
        resume.setUpdatedAt(now);
        resumeMapper.insert(resume);

        InterviewSessionEntity session = new InterviewSessionEntity();
        session.setUserId(TestUsers.USER_A);
        session.setResumeId(resume.getId());
        session.setTitle("memory test");
        session.setStatus(InterviewPracticeService.STATUS_ACTIVE);
        session.setTotalQuestions(1);
        session.setAnsweredQuestions(0);
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        sessionMapper.insert(session);

        InterviewQuestionEntity question = new InterviewQuestionEntity();
        question.setSessionId(session.getId());
        question.setUserId(TestUsers.USER_A);
        question.setQuestionNo(1);
        question.setQuestionType("SKILL");
        question.setQuestionText("请说明 Redis 缓存一致性");
        question.setReferencePoints(jobMatchJsonSupport.writeStringList(List.of("双写一致性")));
        question.setStatus(InterviewPracticeService.QUESTION_PENDING);
        question.setStrengths(jobMatchJsonSupport.writeStringList(List.of()));
        question.setImprovements(jobMatchJsonSupport.writeStringList(List.of()));
        question.setCreatedAt(now);
        question.setUpdatedAt(now);
        questionMapper.insert(question);
        return session.getId();
    }

    private long questionIdFor(long sessionId) {
        return questionMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<InterviewQuestionEntity>()
                        .eq(InterviewQuestionEntity::getSessionId, sessionId)
        ).get(0).getId();
    }

    private void loginAs(long userId) {
        CurrentUserContext.set(CurrentUser.builder()
                .userId(userId)
                .username(TestUsers.USER_A_NAME)
                .role("USER")
                .authenticated(true)
                .build());
    }
}
