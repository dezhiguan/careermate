package com.careermate.dashboard;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.careermate.common.exception.BizException;
import com.careermate.dashboard.dto.DashboardActivityResponse;
import com.careermate.dashboard.dto.DashboardOverviewResponse;
import com.careermate.dashboard.dto.DashboardSuggestionResponse;
import com.careermate.dashboard.dto.InterviewStatsResponse;
import com.careermate.dashboard.dto.JobMatchStatsResponse;
import com.careermate.dashboard.dto.ResumeStatsResponse;
import com.careermate.dashboard.dto.SkillGapItem;
import com.careermate.dashboard.dto.SkillGapResponse;
import com.careermate.interview.InterviewPracticeService;
import com.careermate.jobmatch.JobMatchJsonSupport;
import com.careermate.jobmatch.JobMatchService;
import com.careermate.mapper.InterviewSessionMapper;
import com.careermate.mapper.JobMatchMapper;
import com.careermate.mapper.ResumeMapper;
import com.careermate.model.entity.InterviewSessionEntity;
import com.careermate.model.entity.JobMatchEntity;
import com.careermate.model.entity.ResumeEntity;
import com.careermate.resume.ResumeService;
import com.careermate.security.CurrentUserContext;
import com.careermate.task.CareerTaskService;
import com.careermate.task.dto.DashboardTaskItemResponse;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class DashboardService {

    private static final int MAX_ACTIVITIES = 10;

    private final ResumeMapper resumeMapper;
    private final JobMatchMapper jobMatchMapper;
    private final InterviewSessionMapper interviewSessionMapper;
    private final CareerTaskService careerTaskService;
    private final JobMatchJsonSupport jobMatchJsonSupport;

    public DashboardService(
            ResumeMapper resumeMapper,
            JobMatchMapper jobMatchMapper,
            InterviewSessionMapper interviewSessionMapper,
            CareerTaskService careerTaskService,
            JobMatchJsonSupport jobMatchJsonSupport
    ) {
        this.resumeMapper = resumeMapper;
        this.jobMatchMapper = jobMatchMapper;
        this.interviewSessionMapper = interviewSessionMapper;
        this.careerTaskService = careerTaskService;
        this.jobMatchJsonSupport = jobMatchJsonSupport;
    }

    public DashboardOverviewResponse getOverview() {
        return getOverviewForUser(requireUserId());
    }

    public SkillGapResponse getSkillGap() {
        Long userId = requireUserId();
        List<JobMatchEntity> jobMatches = jobMatchMapper.selectList(
                new LambdaQueryWrapper<JobMatchEntity>()
                        .eq(JobMatchEntity::getUserId, userId)
                        .eq(JobMatchEntity::getStatus, JobMatchService.STATUS_ACTIVE)
        );

        Map<String, Integer> countMap = new java.util.LinkedHashMap<>();
        for (JobMatchEntity entity : jobMatches) {
            List<String> missing = jobMatchJsonSupport.readStringList(entity.getMissingSkills());
            for (String skill : missing) {
                if (skill != null && !skill.isBlank()) {
                    countMap.merge(skill.trim(), 1, Integer::sum);
                }
            }
        }

        List<SkillGapItem> items = countMap.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .map(e -> SkillGapItem.builder().skill(e.getKey()).count(e.getValue()).build())
                .toList();

        return SkillGapResponse.builder()
                .totalJobMatches(jobMatches.size())
                .items(items)
                .build();
    }

    public DashboardOverviewResponse getOverviewForUser(Long userId) {
        if (userId == null) {
            throw new BizException(401, "未登录");
        }

        List<ResumeEntity> resumes = resumeMapper.selectList(
                new LambdaQueryWrapper<ResumeEntity>()
                        .eq(ResumeEntity::getUserId, userId)
                        .eq(ResumeEntity::getStatus, ResumeService.STATUS_ACTIVE)
        );
        List<JobMatchEntity> jobMatches = jobMatchMapper.selectList(
                new LambdaQueryWrapper<JobMatchEntity>()
                        .eq(JobMatchEntity::getUserId, userId)
                        .eq(JobMatchEntity::getStatus, JobMatchService.STATUS_ACTIVE)
                        .orderByDesc(JobMatchEntity::getCreatedAt)
        );
        List<InterviewSessionEntity> interviewSessions = interviewSessionMapper.selectList(
                new LambdaQueryWrapper<InterviewSessionEntity>()
                        .eq(InterviewSessionEntity::getUserId, userId)
                        .ne(InterviewSessionEntity::getStatus, InterviewPracticeService.STATUS_DELETED)
                        .orderByDesc(InterviewSessionEntity::getUpdatedAt)
        );

        ResumeStatsResponse resumeStats = buildResumeStats(resumes);
        JobMatchStatsResponse jobMatchStats = buildJobMatchStats(jobMatches);
        InterviewStatsResponse interviewStats = buildInterviewStats(interviewSessions);
        List<DashboardSuggestionResponse> suggestions = buildSuggestions(
                resumeStats, jobMatchStats, interviewStats, jobMatches, interviewSessions
        );
        List<DashboardActivityResponse> recentActivities = buildRecentActivities(
                resumes, jobMatches, interviewSessions
        );
        List<DashboardTaskItemResponse> tasks = careerTaskService.listDashboardTodoTasks(userId);

        return DashboardOverviewResponse.builder()
                .resumeStats(resumeStats)
                .jobMatchStats(jobMatchStats)
                .interviewStats(interviewStats)
                .suggestions(suggestions)
                .recentActivities(recentActivities)
                .tasks(tasks)
                .build();
    }

    private ResumeStatsResponse buildResumeStats(List<ResumeEntity> resumes) {
        Optional<ResumeEntity> defaultResume = resumes.stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsDefault()))
                .findFirst();
        OffsetDateTime latestUpdated = resumes.stream()
                .map(ResumeEntity::getUpdatedAt)
                .filter(t -> t != null)
                .max(Comparator.naturalOrder())
                .orElse(null);

        return ResumeStatsResponse.builder()
                .totalResumes(resumes.size())
                .hasDefaultResume(defaultResume.isPresent())
                .defaultResumeTitle(defaultResume.map(ResumeEntity::getTitle).orElse(null))
                .latestUpdatedAt(latestUpdated)
                .build();
    }

    private JobMatchStatsResponse buildJobMatchStats(List<JobMatchEntity> jobMatches) {
        int high = 0;
        int medium = 0;
        int low = 0;
        for (JobMatchEntity m : jobMatches) {
            String level = m.getMatchLevel();
            if ("HIGH".equals(level)) {
                high++;
            } else if ("MEDIUM".equals(level)) {
                medium++;
            } else if ("LOW".equals(level)) {
                low++;
            }
        }
        JobMatchEntity latest = jobMatches.isEmpty() ? null : jobMatches.get(0);
        return JobMatchStatsResponse.builder()
                .totalMatches(jobMatches.size())
                .highMatches(high)
                .mediumMatches(medium)
                .lowMatches(low)
                .latestJobTitle(latest == null ? null : latest.getJobTitle())
                .latestMatchScore(latest == null ? null : latest.getMatchScore())
                .latestCreatedAt(latest == null ? null : latest.getCreatedAt())
                .build();
    }

    private InterviewStatsResponse buildInterviewStats(List<InterviewSessionEntity> sessions) {
        int completed = 0;
        int active = 0;
        int scoreSum = 0;
        int scoreCount = 0;
        for (InterviewSessionEntity s : sessions) {
            if (InterviewPracticeService.STATUS_COMPLETED.equals(s.getStatus())) {
                completed++;
            } else if (InterviewPracticeService.STATUS_ACTIVE.equals(s.getStatus())) {
                active++;
            }
            if (s.getAverageScore() != null) {
                scoreSum += s.getAverageScore();
                scoreCount++;
            }
        }
        InterviewSessionEntity latest = sessions.isEmpty() ? null : sessions.get(0);
        Integer avg = scoreCount > 0 ? scoreSum / scoreCount : null;

        return InterviewStatsResponse.builder()
                .totalSessions(sessions.size())
                .completedSessions(completed)
                .activeSessions(active)
                .averageScore(avg)
                .latestSessionTitle(latest == null ? null : latest.getTitle())
                .latestUpdatedAt(latest == null ? null : latest.getUpdatedAt())
                .build();
    }

    private List<DashboardSuggestionResponse> buildSuggestions(
            ResumeStatsResponse resumeStats,
            JobMatchStatsResponse jobMatchStats,
            InterviewStatsResponse interviewStats,
            List<JobMatchEntity> jobMatches,
            List<InterviewSessionEntity> interviewSessions
    ) {
        List<DashboardSuggestionResponse> list = new ArrayList<>();

        if (!resumeStats.isHasDefaultResume()) {
            list.add(DashboardSuggestionResponse.builder()
                    .type("RESUME")
                    .priority("HIGH")
                    .title("创建默认简历")
                    .description("完成默认简历后，CareerMate 才能进行岗位匹配和面试训练。")
                    .actionText("去创建简历")
                    .route("/resume")
                    .build());
        }

        if (resumeStats.isHasDefaultResume() && jobMatchStats.getTotalMatches() == 0) {
            list.add(DashboardSuggestionResponse.builder()
                    .type("JOB_MATCH")
                    .priority("HIGH")
                    .title("录入岗位 JD")
                    .description("基于默认简历和目标岗位生成匹配分析。")
                    .actionText("去岗位匹配")
                    .route("/match")
                    .build());
        }

        if (jobMatchStats.getTotalMatches() > 0 && interviewStats.getTotalSessions() == 0) {
            list.add(DashboardSuggestionResponse.builder()
                    .type("INTERVIEW")
                    .priority("MEDIUM")
                    .title("开始面试训练")
                    .description("根据简历和岗位差距生成面试问题。")
                    .actionText("去面试特训")
                    .route("/interview")
                    .build());
        }

        if (jobMatchStats.getLatestMatchScore() != null && jobMatchStats.getLatestMatchScore() < 60) {
            list.add(DashboardSuggestionResponse.builder()
                    .type("JOB_MATCH")
                    .priority("HIGH")
                    .title("优化岗位匹配度")
                    .description("当前最近岗位匹配度偏低，建议先补齐缺失技能或调整简历表达。")
                    .actionText("查看岗位匹配")
                    .route("/match")
                    .build());
        }

        boolean hasIncomplete = interviewSessions.stream()
                .anyMatch(s -> InterviewPracticeService.STATUS_ACTIVE.equals(s.getStatus()));
        if (hasIncomplete) {
            list.add(DashboardSuggestionResponse.builder()
                    .type("INTERVIEW")
                    .priority("MEDIUM")
                    .title("完成面试训练")
                    .description("你还有未完成的面试训练，可以继续作答并获取反馈。")
                    .actionText("继续训练")
                    .route("/interview")
                    .build());
        }

        list.add(DashboardSuggestionResponse.builder()
                .type("AGENT")
                .priority("LOW")
                .title("和 Agent 复盘求职计划")
                .description("让 Agent 基于你的简历、岗位匹配和面试训练结果给出下一步建议。")
                .actionText("去对话台")
                .route("/")
                .build());

        return list;
    }

    private List<DashboardActivityResponse> buildRecentActivities(
            List<ResumeEntity> resumes,
            List<JobMatchEntity> jobMatches,
            List<InterviewSessionEntity> interviewSessions
    ) {
        List<DashboardActivityResponse> activities = new ArrayList<>();

        for (ResumeEntity resume : resumes) {
            OffsetDateTime at = resume.getUpdatedAt() != null ? resume.getUpdatedAt() : resume.getCreatedAt();
            if (at == null) {
                continue;
            }
            activities.add(DashboardActivityResponse.builder()
                    .type("RESUME_UPDATED")
                    .title("更新简历")
                    .description(resume.getTitle())
                    .route("/resume")
                    .occurredAt(at)
                    .build());
        }

        for (JobMatchEntity match : jobMatches) {
            if (match.getCreatedAt() == null) {
                continue;
            }
            int score = match.getMatchScore() == null ? 0 : match.getMatchScore();
            activities.add(DashboardActivityResponse.builder()
                    .type("JOB_MATCH_CREATED")
                    .title("岗位匹配")
                    .description(match.getJobTitle() + " · 匹配 " + score + "%")
                    .route("/match")
                    .occurredAt(match.getCreatedAt())
                    .build());
        }

        for (InterviewSessionEntity session : interviewSessions) {
            boolean completed = InterviewPracticeService.STATUS_COMPLETED.equals(session.getStatus());
            OffsetDateTime at = session.getUpdatedAt() != null ? session.getUpdatedAt() : session.getCreatedAt();
            if (at == null) {
                continue;
            }
            activities.add(DashboardActivityResponse.builder()
                    .type(completed ? "INTERVIEW_COMPLETED" : "INTERVIEW_CREATED")
                    .title(completed ? "完成面试训练" : "创建面试训练")
                    .description(session.getTitle())
                    .route("/interview")
                    .occurredAt(at)
                    .build());
        }

        return activities.stream()
                .sorted(Comparator.comparing(DashboardActivityResponse::getOccurredAt).reversed())
                .limit(MAX_ACTIVITIES)
                .toList();
    }

    private Long requireUserId() {
        Long userId = CurrentUserContext.getUserId();
        if (userId == null) {
            throw new BizException(401, "未登录");
        }
        return userId;
    }
}
