package com.careermate.interview;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.careermate.common.exception.BizException;
import com.careermate.interview.dto.InterviewAnswerRequest;
import com.careermate.interview.dto.InterviewQuestionResponse;
import com.careermate.interview.dto.InterviewSessionCreateRequest;
import com.careermate.interview.dto.InterviewSessionDetailResponse;
import com.careermate.interview.dto.InterviewSessionListItemResponse;
import com.careermate.jobmatch.JobMatchJsonSupport;
import com.careermate.jobmatch.JobMatchService;
import com.careermate.mapper.InterviewQuestionMapper;
import com.careermate.mapper.InterviewSessionMapper;
import com.careermate.model.entity.InterviewQuestionEntity;
import com.careermate.model.entity.InterviewSessionEntity;
import com.careermate.model.entity.JobMatchEntity;
import com.careermate.model.entity.ResumeEntity;
import com.careermate.resume.ResumeService;
import com.careermate.security.CurrentUserContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class InterviewPracticeService {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_DELETED = "DELETED";
    public static final String QUESTION_PENDING = "PENDING";
    public static final String QUESTION_ANSWERED = "ANSWERED";

    private static final int QUESTION_COUNT = 5;
    private static final String NO_DEFAULT_RESUME_MSG = "请先创建并设置默认简历后再开始面试训练";

    private final InterviewSessionMapper sessionMapper;
    private final InterviewQuestionMapper questionMapper;
    private final ResumeService resumeService;
    private final JobMatchService jobMatchService;
    private final InterviewQuestionGenerator questionGenerator;
    private final InterviewAnswerEvaluator answerEvaluator;
    private final JobMatchJsonSupport jobMatchJsonSupport;

    public InterviewPracticeService(
            InterviewSessionMapper sessionMapper,
            InterviewQuestionMapper questionMapper,
            ResumeService resumeService,
            JobMatchService jobMatchService,
            InterviewQuestionGenerator questionGenerator,
            InterviewAnswerEvaluator answerEvaluator,
            JobMatchJsonSupport jobMatchJsonSupport
    ) {
        this.sessionMapper = sessionMapper;
        this.questionMapper = questionMapper;
        this.resumeService = resumeService;
        this.jobMatchService = jobMatchService;
        this.questionGenerator = questionGenerator;
        this.answerEvaluator = answerEvaluator;
        this.jobMatchJsonSupport = jobMatchJsonSupport;
    }

    public List<InterviewSessionListItemResponse> listSessions() {
        Long userId = requireUserId();
        List<InterviewSessionEntity> rows = sessionMapper.selectList(
                new LambdaQueryWrapper<InterviewSessionEntity>()
                        .eq(InterviewSessionEntity::getUserId, userId)
                        .ne(InterviewSessionEntity::getStatus, STATUS_DELETED)
                        .orderByDesc(InterviewSessionEntity::getCreatedAt)
        );
        return rows.stream().map(this::toListItem).toList();
    }

    @Transactional
    public InterviewSessionDetailResponse createSession(InterviewSessionCreateRequest request) {
        Long userId = requireUserId();
        ResumeEntity resume = resumeService.getDefaultActiveResume(userId)
                .orElseThrow(() -> new BizException(400, NO_DEFAULT_RESUME_MSG));

        Optional<JobMatchEntity> jobMatch = jobMatchService.getLatestActiveMatch(userId);
        OffsetDateTime now = OffsetDateTime.now();

        String title = resolveTitle(request);
        InterviewSessionEntity session = new InterviewSessionEntity();
        session.setUserId(userId);
        session.setResumeId(resume.getId());
        session.setJobMatchId(jobMatch.map(JobMatchEntity::getId).orElse(null));
        session.setTitle(title);
        session.setStatus(STATUS_ACTIVE);
        session.setTotalQuestions(QUESTION_COUNT);
        session.setAnsweredQuestions(0);
        session.setAverageScore(null);
        session.setSummary(null);
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        sessionMapper.insert(session);

        List<InterviewQuestionGenerator.GeneratedQuestion> generated =
                questionGenerator.generate(resume, jobMatch);
        for (InterviewQuestionGenerator.GeneratedQuestion g : generated) {
            InterviewQuestionEntity q = new InterviewQuestionEntity();
            q.setSessionId(session.getId());
            q.setUserId(userId);
            q.setQuestionNo(g.questionNo());
            q.setQuestionType(g.questionType());
            q.setQuestionText(g.questionText());
            q.setReferencePoints(jobMatchJsonSupport.writeStringList(g.referencePoints()));
            q.setStatus(QUESTION_PENDING);
            q.setStrengths(jobMatchJsonSupport.writeStringList(List.of()));
            q.setImprovements(jobMatchJsonSupport.writeStringList(List.of()));
            q.setCreatedAt(now);
            q.setUpdatedAt(now);
            questionMapper.insert(q);
        }

        return getSession(session.getId());
    }

    public InterviewSessionDetailResponse getSession(Long sessionId) {
        Long userId = requireUserId();
        InterviewSessionEntity session = requireOwnedSession(sessionId, userId);
        List<InterviewQuestionEntity> questions = questionMapper.selectList(
                new LambdaQueryWrapper<InterviewQuestionEntity>()
                        .eq(InterviewQuestionEntity::getSessionId, sessionId)
                        .eq(InterviewQuestionEntity::getUserId, userId)
                        .orderByAsc(InterviewQuestionEntity::getQuestionNo)
        );
        return toDetail(session, questions);
    }

    @Transactional
    public InterviewQuestionResponse submitAnswer(Long sessionId, Long questionId, InterviewAnswerRequest request) {
        Long userId = requireUserId();
        requireOwnedSession(sessionId, userId);

        InterviewQuestionEntity question = questionMapper.selectOne(
                new LambdaQueryWrapper<InterviewQuestionEntity>()
                        .eq(InterviewQuestionEntity::getId, questionId)
                        .eq(InterviewQuestionEntity::getSessionId, sessionId)
                        .eq(InterviewQuestionEntity::getUserId, userId)
        );
        if (question == null) {
            throw new BizException(404, "题目不存在");
        }

        String answerText = request.getAnswerText().trim();
        List<String> referencePoints = jobMatchJsonSupport.readStringList(question.getReferencePoints());
        InterviewAnswerEvaluator.EvaluationResult eval =
                answerEvaluator.evaluate(question, answerText, referencePoints);

        OffsetDateTime now = OffsetDateTime.now();
        question.setAnswerText(answerText);
        question.setScore(eval.score());
        question.setFeedback(eval.feedback());
        question.setStrengths(jobMatchJsonSupport.writeStringList(eval.strengths()));
        question.setImprovements(jobMatchJsonSupport.writeStringList(eval.improvements()));
        question.setStatus(QUESTION_ANSWERED);
        question.setUpdatedAt(now);
        questionMapper.updateById(question);

        refreshSessionProgress(sessionId, userId, now);
        return toQuestionResponse(question);
    }

    @Transactional
    public InterviewSessionDetailResponse completeSession(Long sessionId) {
        Long userId = requireUserId();
        InterviewSessionEntity session = requireOwnedSession(sessionId, userId);
        OffsetDateTime now = OffsetDateTime.now();

        List<InterviewQuestionEntity> answered = questionMapper.selectList(
                new LambdaQueryWrapper<InterviewQuestionEntity>()
                        .eq(InterviewQuestionEntity::getSessionId, sessionId)
                        .eq(InterviewQuestionEntity::getUserId, userId)
                        .eq(InterviewQuestionEntity::getStatus, QUESTION_ANSWERED)
        );

        String summary = buildSummary(session, answered);
        sessionMapper.update(null, new LambdaUpdateWrapper<InterviewSessionEntity>()
                .eq(InterviewSessionEntity::getId, sessionId)
                .eq(InterviewSessionEntity::getUserId, userId)
                .set(InterviewSessionEntity::getStatus, STATUS_COMPLETED)
                .set(InterviewSessionEntity::getSummary, summary)
                .set(InterviewSessionEntity::getUpdatedAt, now));

        return getSession(sessionId);
    }

    @Transactional
    public void deleteSession(Long sessionId) {
        Long userId = requireUserId();
        requireOwnedSession(sessionId, userId);
        sessionMapper.update(null, new LambdaUpdateWrapper<InterviewSessionEntity>()
                .eq(InterviewSessionEntity::getId, sessionId)
                .eq(InterviewSessionEntity::getUserId, userId)
                .set(InterviewSessionEntity::getStatus, STATUS_DELETED)
                .set(InterviewSessionEntity::getUpdatedAt, OffsetDateTime.now()));
    }

    private void refreshSessionProgress(Long sessionId, Long userId, OffsetDateTime now) {
        List<InterviewQuestionEntity> answered = questionMapper.selectList(
                new LambdaQueryWrapper<InterviewQuestionEntity>()
                        .eq(InterviewQuestionEntity::getSessionId, sessionId)
                        .eq(InterviewQuestionEntity::getUserId, userId)
                        .eq(InterviewQuestionEntity::getStatus, QUESTION_ANSWERED)
        );
        int count = answered.size();
        Integer avg = null;
        if (count > 0) {
            int sum = answered.stream()
                    .map(InterviewQuestionEntity::getScore)
                    .filter(s -> s != null)
                    .mapToInt(Integer::intValue)
                    .sum();
            avg = sum / count;
        }
        sessionMapper.update(null, new LambdaUpdateWrapper<InterviewSessionEntity>()
                .eq(InterviewSessionEntity::getId, sessionId)
                .eq(InterviewSessionEntity::getUserId, userId)
                .set(InterviewSessionEntity::getAnsweredQuestions, count)
                .set(InterviewSessionEntity::getAverageScore, avg)
                .set(InterviewSessionEntity::getUpdatedAt, now));
    }

    private String buildSummary(InterviewSessionEntity session, List<InterviewQuestionEntity> answered) {
        int total = session.getTotalQuestions() == null ? QUESTION_COUNT : session.getTotalQuestions();
        int done = answered.size();
        int avg = session.getAverageScore() == null ? 0 : session.getAverageScore();
        if (answered.stream().anyMatch(q -> q.getScore() != null)) {
            avg = (int) answered.stream()
                    .map(InterviewQuestionEntity::getScore)
                    .filter(s -> s != null)
                    .mapToInt(Integer::intValue)
                    .average()
                    .orElse(0);
        }
        return String.format(
                "训练「%s」已完成：共 %d 题，已作答 %d 题，平均得分 %d 分。建议针对低分题对照参考要点再练一轮。",
                session.getTitle(), total, done, avg
        );
    }

    private String resolveTitle(InterviewSessionCreateRequest request) {
        if (request != null && request.getTitle() != null && !request.getTitle().isBlank()) {
            return request.getTitle().trim();
        }
        return "面试训练";
    }

    private InterviewSessionEntity requireOwnedSession(Long sessionId, Long userId) {
        InterviewSessionEntity session = sessionMapper.selectOne(
                new LambdaQueryWrapper<InterviewSessionEntity>()
                        .eq(InterviewSessionEntity::getId, sessionId)
                        .eq(InterviewSessionEntity::getUserId, userId)
                        .ne(InterviewSessionEntity::getStatus, STATUS_DELETED)
        );
        if (session == null) {
            throw new BizException(404, "训练记录不存在");
        }
        return session;
    }

    private Long requireUserId() {
        Long userId = CurrentUserContext.getUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        return userId;
    }

    private InterviewSessionListItemResponse toListItem(InterviewSessionEntity entity) {
        return InterviewSessionListItemResponse.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .status(entity.getStatus())
                .totalQuestions(entity.getTotalQuestions())
                .answeredQuestions(entity.getAnsweredQuestions())
                .averageScore(entity.getAverageScore())
                .summary(entity.getSummary())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private InterviewSessionDetailResponse toDetail(
            InterviewSessionEntity session,
            List<InterviewQuestionEntity> questions
    ) {
        return InterviewSessionDetailResponse.builder()
                .id(session.getId())
                .resumeId(session.getResumeId())
                .jobMatchId(session.getJobMatchId())
                .title(session.getTitle())
                .status(session.getStatus())
                .totalQuestions(session.getTotalQuestions())
                .answeredQuestions(session.getAnsweredQuestions())
                .averageScore(session.getAverageScore())
                .summary(session.getSummary())
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .questions(questions.stream().map(this::toQuestionResponse).toList())
                .build();
    }

    private InterviewQuestionResponse toQuestionResponse(InterviewQuestionEntity entity) {
        return InterviewQuestionResponse.builder()
                .id(entity.getId())
                .questionNo(entity.getQuestionNo())
                .questionType(entity.getQuestionType())
                .questionText(entity.getQuestionText())
                .referencePoints(jobMatchJsonSupport.readStringList(entity.getReferencePoints()))
                .answerText(entity.getAnswerText())
                .score(entity.getScore())
                .feedback(entity.getFeedback())
                .strengths(jobMatchJsonSupport.readStringList(entity.getStrengths()))
                .improvements(jobMatchJsonSupport.readStringList(entity.getImprovements()))
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
