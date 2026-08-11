package com.careermate.interview.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.careermate.agent.memory.AgentMemoryService;
import com.careermate.common.exception.BizException;
import com.careermate.interview.InterviewAnswerEvaluator;
import com.careermate.interview.InterviewQuestionGenerator;
import com.careermate.interview.dto.InterviewAnswerRequest;
import com.careermate.interview.dto.InterviewQuestionResponse;
import com.careermate.interview.dto.InterviewSessionCreateRequest;
import com.careermate.interview.dto.InterviewSessionDetailResponse;
import com.careermate.interview.dto.InterviewSessionListItemResponse;
import com.careermate.jobmatch.JobMatchJsonSupport;
import com.careermate.jobmatch.service.JobMatchService;
import com.careermate.mapper.InterviewQuestionMapper;
import com.careermate.mapper.InterviewSessionMapper;
import com.careermate.model.entity.InterviewQuestionEntity;
import com.careermate.model.entity.InterviewSessionEntity;
import com.careermate.model.entity.JobMatchEntity;
import com.careermate.model.entity.ResumeEntity;
import com.careermate.resume.service.ResumeService;
import com.careermate.security.CurrentUserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class InterviewPracticeService {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_DELETED = "DELETED";
    public static final String QUESTION_PENDING = "PENDING";
    public static final String QUESTION_ANSWERED = "ANSWERED";

    private static final int QUESTION_COUNT = 5;
    private static final int MAX_TITLE_LENGTH = 60;
    private static final DateTimeFormatter TITLE_TIME_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private static final String NO_DEFAULT_RESUME_MSG =
            "面试训练会基于你的简历出专属题目，请先到「我的简历」上传并设为默认简历，然后回来开始练习。";

    private final InterviewSessionMapper sessionMapper;
    private final InterviewQuestionMapper questionMapper;
    private final ResumeService resumeService;
    private final JobMatchService jobMatchService;
    private final InterviewQuestionGenerator questionGenerator;
    private final InterviewAnswerEvaluator answerEvaluator;
    private final JobMatchJsonSupport jobMatchJsonSupport;
    private final AgentMemoryService agentMemoryService;

    public InterviewPracticeService(
            InterviewSessionMapper sessionMapper,
            InterviewQuestionMapper questionMapper,
            ResumeService resumeService,
            JobMatchService jobMatchService,
            InterviewQuestionGenerator questionGenerator,
            InterviewAnswerEvaluator answerEvaluator,
            JobMatchJsonSupport jobMatchJsonSupport,
            AgentMemoryService agentMemoryService
    ) {
        this.sessionMapper = sessionMapper;
        this.questionMapper = questionMapper;
        this.resumeService = resumeService;
        this.jobMatchService = jobMatchService;
        this.questionGenerator = questionGenerator;
        this.answerEvaluator = answerEvaluator;
        this.jobMatchJsonSupport = jobMatchJsonSupport;
        this.agentMemoryService = agentMemoryService;
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
        return createSessionForUser(requireUserId(), request);
    }

    @Transactional
    public InterviewSessionDetailResponse createSessionForUser(Long userId, InterviewSessionCreateRequest request) {
        if (userId == null) {
            throw new BizException(401, "未认证");
        }
        ResumeEntity resume = resumeService.getDefaultActiveResume(userId)
                .orElseThrow(() -> new BizException(400, NO_DEFAULT_RESUME_MSG));

        Optional<JobMatchEntity> jobMatch = jobMatchService.getLatestActiveMatch(userId);
        OffsetDateTime now = OffsetDateTime.now();

        String title = resolveTitle(request, jobMatch, now);

        // 复用未开始的训练：Agent 在对话中命中「准备面试」等意图就会调用本方法，
        // 若每次都新建，用户只要多聊几轮就会积累一堆零作答的空记录（线上曾一天生成 9 条）。
        // 因此当已存在同名（或未指定标题）且一题未答的进行中训练时，直接返回它。
        Optional<InterviewSessionEntity> reusable = findReusableEmptySession(userId, request, title);
        if (reusable.isPresent()) {
            InterviewSessionEntity existing = reusable.get();
            log.info("Reuse empty interview session instead of creating a new one: userId={}, sessionId={}",
                    userId, existing.getId());
            return getSessionForUser(existing.getId(), userId);
        }

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

        return getSessionForUser(session.getId(), userId);
    }

    public InterviewSessionDetailResponse getSession(Long sessionId) {
        return getSessionForUser(sessionId, requireUserId());
    }

    public InterviewSessionDetailResponse getSessionForUser(Long sessionId, Long userId) {
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
        rememberWeaknessSafely(userId, eval.score(), question, eval.improvements());
        return toQuestionResponse(question);
    }

    private void rememberWeaknessSafely(
            Long userId,
            int score,
            InterviewQuestionEntity question,
            List<String> improvements
    ) {
        try {
            List<String> referencePoints = jobMatchJsonSupport.readStringList(question.getReferencePoints());
            agentMemoryService.rememberInterviewWeakness(
                    userId,
                    score,
                    question.getQuestionType(),
                    question.getQuestionText(),
                    referencePoints,
                    improvements
            );
        } catch (Exception e) {
            log.warn(
                    "Failed to remember interview weakness: userId={}, questionId={}",
                    userId,
                    question.getId(),
                    e
            );
        }
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
        String weakness = weakestQuestionType(answered);
        sessionMapper.update(null, new LambdaUpdateWrapper<InterviewSessionEntity>()
                .eq(InterviewSessionEntity::getId, sessionId)
                .eq(InterviewSessionEntity::getUserId, userId)
                .set(InterviewSessionEntity::getStatus, STATUS_COMPLETED)
                .set(InterviewSessionEntity::getSummary, summary)
                .set(InterviewSessionEntity::getWeakness, weakness)
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
        // 分母必须是「有分数的题数」而非「已答题数」：若某题分数为 null，
        // 用 answered.size() 作分母会把均分算低。
        List<Integer> scores = answered.stream()
                .map(InterviewQuestionEntity::getScore)
                .filter(s -> s != null)
                .toList();
        Integer avg = scores.isEmpty()
                ? null
                : scores.stream().mapToInt(Integer::intValue).sum() / scores.size();
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

    /**
     * 标题优先级：显式指定 &gt; 目标岗位（岗位名/公司） &gt; 「面试训练 · MM-dd HH:mm」。
     * 兜底带上时间是为了让列表可区分——此前所有无 JD 的训练都叫「面试训练」，
     * 用户在列表里无法定位任何一次记录。
     */
    private String resolveTitle(
            InterviewSessionCreateRequest request,
            Optional<JobMatchEntity> jobMatch,
            OffsetDateTime now
    ) {
        if (request != null && request.getTitle() != null && !request.getTitle().isBlank()) {
            return request.getTitle().trim();
        }
        String fromMatch = jobMatch.map(this::titleFromJobMatch).filter(StringUtils::hasText).orElse(null);
        if (fromMatch != null) {
            return fromMatch;
        }
        return "面试训练 · " + now.format(TITLE_TIME_FORMATTER);
    }

    private String titleFromJobMatch(JobMatchEntity match) {
        String job = match.getJobTitle() == null ? "" : match.getJobTitle().trim();
        String company = match.getCompanyName() == null ? "" : match.getCompanyName().trim();
        if (StringUtils.hasText(job) && StringUtils.hasText(company)) {
            return job + " · " + company;
        }
        return StringUtils.hasText(job) ? job : company;
    }

    /**
     * 找出可复用的「一题未答且进行中」的训练。
     * 指定了标题时只复用同名的，避免把针对某个岗位的训练和泛化训练混为一谈。
     */
    private Optional<InterviewSessionEntity> findReusableEmptySession(
            Long userId,
            InterviewSessionCreateRequest request,
            String resolvedTitle
    ) {
        boolean explicitTitle = request != null
                && request.getTitle() != null
                && !request.getTitle().isBlank();

        LambdaQueryWrapper<InterviewSessionEntity> wrapper =
                new LambdaQueryWrapper<InterviewSessionEntity>()
                        .eq(InterviewSessionEntity::getUserId, userId)
                        .eq(InterviewSessionEntity::getStatus, STATUS_ACTIVE)
                        .and(w -> w.isNull(InterviewSessionEntity::getAnsweredQuestions)
                                .or()
                                .eq(InterviewSessionEntity::getAnsweredQuestions, 0))
                        .orderByDesc(InterviewSessionEntity::getCreatedAt)
                        .last("limit 1");
        if (explicitTitle) {
            wrapper.eq(InterviewSessionEntity::getTitle, resolvedTitle);
        }
        return Optional.ofNullable(sessionMapper.selectOne(wrapper));
    }

    /** 重命名训练记录——列表里同名记录堆积时，用户需要自己整理的手段。 */
    @Transactional
    public InterviewSessionDetailResponse renameSession(Long sessionId, String title) {
        Long userId = requireUserId();
        requireOwnedSession(sessionId, userId);
        if (!StringUtils.hasText(title)) {
            throw new BizException(400, "标题不能为空");
        }
        String trimmed = title.trim();
        if (trimmed.length() > MAX_TITLE_LENGTH) {
            throw new BizException(400, "标题最多 " + MAX_TITLE_LENGTH + " 个字");
        }
        sessionMapper.update(null, new LambdaUpdateWrapper<InterviewSessionEntity>()
                .eq(InterviewSessionEntity::getId, sessionId)
                .eq(InterviewSessionEntity::getUserId, userId)
                .set(InterviewSessionEntity::getTitle, trimmed)
                .set(InterviewSessionEntity::getUpdatedAt, OffsetDateTime.now()));
        return getSessionForUser(sessionId, userId);
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

    /** 最弱题型：按题型均分排序，取最低且低于阈值(75)的那类；不足则空。 */
    private String weakestQuestionType(List<InterviewQuestionEntity> answered) {
        if (answered == null || answered.isEmpty()) {
            return null;
        }
        java.util.Map<String, int[]> agg = new java.util.HashMap<>();  // type -> [sum, count]
        for (InterviewQuestionEntity q : answered) {
            if (q.getQuestionType() == null || q.getScore() == null) {
                continue;
            }
            int[] a = agg.computeIfAbsent(q.getQuestionType(), k -> new int[2]);
            a[0] += q.getScore();
            a[1] += 1;
        }
        String weakest = null;
        double lowest = Double.MAX_VALUE;
        for (var e : agg.entrySet()) {
            double avg = (double) e.getValue()[0] / e.getValue()[1];
            if (avg < lowest) {
                lowest = avg;
                weakest = e.getKey();
            }
        }
        return lowest < 75 ? weakest : null;
    }

    private InterviewSessionListItemResponse toListItem(InterviewSessionEntity entity) {
        return InterviewSessionListItemResponse.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .status(entity.getStatus())
                .sessionType(entity.getSessionType() == null ? "MOCK" : entity.getSessionType())
                .weakness(entity.getWeakness())
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
