package com.careermate.jobmatch;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.careermate.common.exception.BizException;
import com.careermate.jobmatch.dto.JobMatchAnalyzeRequest;
import com.careermate.jobmatch.dto.JobMatchDetailResponse;
import com.careermate.jobmatch.dto.JobMatchListItemResponse;
import com.careermate.mapper.JobMatchMapper;
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
public class JobMatchService {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DELETED = "DELETED";

    private final JobMatchMapper jobMatchMapper;
    private final ResumeService resumeService;
    private final JobMatchAnalyzer jobMatchAnalyzer;
    private final JobMatchJsonSupport jobMatchJsonSupport;

    public JobMatchService(
            JobMatchMapper jobMatchMapper,
            ResumeService resumeService,
            JobMatchAnalyzer jobMatchAnalyzer,
            JobMatchJsonSupport jobMatchJsonSupport
    ) {
        this.jobMatchMapper = jobMatchMapper;
        this.resumeService = resumeService;
        this.jobMatchAnalyzer = jobMatchAnalyzer;
        this.jobMatchJsonSupport = jobMatchJsonSupport;
    }

    public Optional<JobMatchEntity> getLatestActiveMatch(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        JobMatchEntity entity = jobMatchMapper.selectOne(
                new LambdaQueryWrapper<JobMatchEntity>()
                        .eq(JobMatchEntity::getUserId, userId)
                        .eq(JobMatchEntity::getStatus, STATUS_ACTIVE)
                        .orderByDesc(JobMatchEntity::getCreatedAt)
                        .last("LIMIT 1")
        );
        return Optional.ofNullable(entity);
    }

    public List<JobMatchListItemResponse> listActiveMatches() {
        Long userId = requireUserId();
        List<JobMatchEntity> rows = jobMatchMapper.selectList(
                new LambdaQueryWrapper<JobMatchEntity>()
                        .eq(JobMatchEntity::getUserId, userId)
                        .eq(JobMatchEntity::getStatus, STATUS_ACTIVE)
                        .orderByDesc(JobMatchEntity::getCreatedAt)
        );
        return rows.stream().map(this::toListItem).toList();
    }

    @Transactional
    public JobMatchDetailResponse analyzeCurrentUserDefaultResume(JobMatchAnalyzeRequest request) {
        Long userId = requireUserId();
        ResumeEntity resume = resumeService.getDefaultActiveResume(userId)
                .orElseThrow(() -> new BizException(400, "请先创建并设置默认简历后再进行岗位匹配"));

        JobMatchAnalysisResult analysis = jobMatchAnalyzer.analyze(
                resume.getContent(),
                request.getJdContent(),
                request.getJobTitle()
        );

        OffsetDateTime now = OffsetDateTime.now();
        JobMatchEntity entity = new JobMatchEntity();
        entity.setUserId(userId);
        entity.setResumeId(resume.getId());
        entity.setJobTitle(request.getJobTitle().trim());
        entity.setCompanyName(trimToNull(request.getCompanyName()));
        entity.setJdContent(request.getJdContent().trim());
        entity.setMatchScore(analysis.getMatchScore());
        entity.setMatchLevel(analysis.getMatchLevel());
        entity.setMatchedSkills(jobMatchJsonSupport.writeStringList(analysis.getMatchedSkills()));
        entity.setMissingSkills(jobMatchJsonSupport.writeStringList(analysis.getMissingSkills()));
        entity.setStrengths(jobMatchJsonSupport.writeStringList(analysis.getStrengths()));
        entity.setRisks(jobMatchJsonSupport.writeStringList(analysis.getRisks()));
        entity.setSuggestions(jobMatchJsonSupport.writeStringList(analysis.getSuggestions()));
        entity.setAnalysisSummary(analysis.getAnalysisSummary());
        entity.setStatus(STATUS_ACTIVE);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        jobMatchMapper.insert(entity);

        return toDetail(entity);
    }

    public JobMatchDetailResponse getMatch(Long id) {
        Long userId = requireUserId();
        JobMatchEntity entity = getActiveMatchOrThrow(userId, id);
        return toDetail(entity);
    }

    @Transactional
    public void deleteMatch(Long id) {
        Long userId = requireUserId();
        getActiveMatchOrThrow(userId, id);
        jobMatchMapper.update(
                null,
                new LambdaUpdateWrapper<JobMatchEntity>()
                        .eq(JobMatchEntity::getId, id)
                        .eq(JobMatchEntity::getUserId, userId)
                        .set(JobMatchEntity::getStatus, STATUS_DELETED)
                        .set(JobMatchEntity::getUpdatedAt, OffsetDateTime.now())
        );
    }

    private JobMatchEntity getActiveMatchOrThrow(Long userId, Long id) {
        JobMatchEntity entity = jobMatchMapper.selectOne(
                new LambdaQueryWrapper<JobMatchEntity>()
                        .eq(JobMatchEntity::getId, id)
                        .eq(JobMatchEntity::getUserId, userId)
                        .eq(JobMatchEntity::getStatus, STATUS_ACTIVE)
        );
        if (entity == null) {
            throw new BizException(404, "匹配记录不存在");
        }
        return entity;
    }

    private Long requireUserId() {
        Long userId = CurrentUserContext.getUserId();
        if (userId == null) {
            throw new BizException(401, "未认证");
        }
        return userId;
    }

    private JobMatchListItemResponse toListItem(JobMatchEntity entity) {
        return JobMatchListItemResponse.builder()
                .id(entity.getId())
                .resumeId(entity.getResumeId())
                .jobTitle(entity.getJobTitle())
                .companyName(entity.getCompanyName())
                .matchScore(entity.getMatchScore())
                .matchLevel(entity.getMatchLevel())
                .matchedSkills(jobMatchJsonSupport.readStringList(entity.getMatchedSkills()))
                .missingSkills(jobMatchJsonSupport.readStringList(entity.getMissingSkills()))
                .analysisSummary(entity.getAnalysisSummary())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private JobMatchDetailResponse toDetail(JobMatchEntity entity) {
        return JobMatchDetailResponse.builder()
                .id(entity.getId())
                .resumeId(entity.getResumeId())
                .jobTitle(entity.getJobTitle())
                .companyName(entity.getCompanyName())
                .jdContent(entity.getJdContent())
                .matchScore(entity.getMatchScore())
                .matchLevel(entity.getMatchLevel())
                .matchedSkills(jobMatchJsonSupport.readStringList(entity.getMatchedSkills()))
                .missingSkills(jobMatchJsonSupport.readStringList(entity.getMissingSkills()))
                .strengths(jobMatchJsonSupport.readStringList(entity.getStrengths()))
                .risks(jobMatchJsonSupport.readStringList(entity.getRisks()))
                .suggestions(jobMatchJsonSupport.readStringList(entity.getSuggestions()))
                .analysisSummary(entity.getAnalysisSummary())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
