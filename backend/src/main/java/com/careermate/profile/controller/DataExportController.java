package com.careermate.profile.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.careermate.common.api.ApiResponse;
import com.careermate.mapper.CareerProfileMapper;
import com.careermate.mapper.InterviewSessionMapper;
import com.careermate.mapper.JobApplicationMapper;
import com.careermate.mapper.ResumeVersionMapper;
import com.careermate.mapper.StudyNoteMapper;
import com.careermate.mapper.UserLongTermMemoryMapper;
import com.careermate.model.entity.CareerProfileEntity;
import com.careermate.model.entity.InterviewSessionEntity;
import com.careermate.model.entity.JobApplicationEntity;
import com.careermate.model.entity.ResumeVersionEntity;
import com.careermate.model.entity.StudyNoteEntity;
import com.careermate.model.entity.UserLongTermMemoryEntity;
import com.careermate.security.CurrentUserContext;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 数据导出（设计 06 我的·设置卡「数据导出」）：聚合当前用户全部数据为一份 JSON。 */
@RestController
@RequestMapping("/api/user/data-export")
public class DataExportController {

    private final CareerProfileMapper careerProfileMapper;
    private final ResumeVersionMapper resumeVersionMapper;
    private final InterviewSessionMapper interviewSessionMapper;
    private final StudyNoteMapper studyNoteMapper;
    private final JobApplicationMapper jobApplicationMapper;
    private final UserLongTermMemoryMapper ltmMapper;

    public DataExportController(CareerProfileMapper careerProfileMapper,
                               ResumeVersionMapper resumeVersionMapper,
                               InterviewSessionMapper interviewSessionMapper,
                               StudyNoteMapper studyNoteMapper,
                               JobApplicationMapper jobApplicationMapper,
                               UserLongTermMemoryMapper ltmMapper) {
        this.careerProfileMapper = careerProfileMapper;
        this.resumeVersionMapper = resumeVersionMapper;
        this.interviewSessionMapper = interviewSessionMapper;
        this.studyNoteMapper = studyNoteMapper;
        this.jobApplicationMapper = jobApplicationMapper;
        this.ltmMapper = ltmMapper;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> export() {
        Long userId = CurrentUserContext.getUserId();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("exportedAt", OffsetDateTime.now().toString());
        out.put("userId", userId);
        if (userId == null) {
            return ApiResponse.success(out);
        }
        out.put("profile", careerProfileMapper.selectList(
                new LambdaQueryWrapper<CareerProfileEntity>().eq(CareerProfileEntity::getUserId, userId)));
        out.put("resumeVersions", resumeVersionMapper.selectList(
                new LambdaQueryWrapper<ResumeVersionEntity>().eq(ResumeVersionEntity::getUserId, userId)));
        out.put("interviewSessions", interviewSessionMapper.selectList(
                new LambdaQueryWrapper<InterviewSessionEntity>().eq(InterviewSessionEntity::getUserId, userId)));
        out.put("studyNotes", studyNoteMapper.selectList(
                new LambdaQueryWrapper<StudyNoteEntity>().eq(StudyNoteEntity::getUserId, userId)));
        out.put("applications", jobApplicationMapper.selectList(
                new LambdaQueryWrapper<JobApplicationEntity>().eq(JobApplicationEntity::getUserId, userId)));
        out.put("longTermMemory", ltmMapper.selectList(
                new LambdaQueryWrapper<UserLongTermMemoryEntity>()
                        .eq(UserLongTermMemoryEntity::getUserId, userId)
                        .eq(UserLongTermMemoryEntity::getDeleted, false)));
        return ApiResponse.success(out);
    }
}
