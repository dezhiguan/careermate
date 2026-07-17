package com.careermate.pipeline.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.careermate.common.exception.BizException;
import com.careermate.mapper.JobApplicationMapper;
import com.careermate.model.entity.JobApplicationEntity;
import com.careermate.pipeline.ApplicationStage;
import com.careermate.pipeline.dto.ApplicationVO;
import com.careermate.pipeline.dto.CreateApplicationRequest;
import com.careermate.pipeline.dto.PipelineBoardVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 投递看板服务：机会的创建（带 jd_id 去重）、阶段流转、看板聚合、归档。
 *
 * <p>阶段维护对应设计稿三层机制中的「一键确认 / 手动兜底」；「对话自动推断」为后续增强，
 * 由 agent 调用本服务的 updateStage 落地。
 */
@Slf4j
@Service
public class PipelineService {

    private final JobApplicationMapper applicationMapper;
    private final com.careermate.mapper.ResumeVersionMapper resumeVersionMapper;

    public PipelineService(JobApplicationMapper applicationMapper,
                           com.careermate.mapper.ResumeVersionMapper resumeVersionMapper) {
        this.applicationMapper = applicationMapper;
        this.resumeVersionMapper = resumeVersionMapper;
    }

    /**
     * 开始一个投递机会。若同一用户对同一条 JD 已有未删记录 → 直接返回它（jd_id 强去重），不重复建。
     */
    @Transactional
    public ApplicationVO createApplication(Long userId, CreateApplicationRequest request) {
        if (userId == null) {
            throw new BizException(401, "未认证");
        }
        CreateApplicationRequest req = request == null ? new CreateApplicationRequest() : request;
        LocalDateTime now = LocalDateTime.now();

        if (req.getJdDocId() != null) {
            JobApplicationEntity existing = applicationMapper.selectOne(
                    new LambdaQueryWrapper<JobApplicationEntity>()
                            .eq(JobApplicationEntity::getUserId, userId)
                            .eq(JobApplicationEntity::getJdDocId, req.getJdDocId())
                            .isNull(JobApplicationEntity::getDeletedAt)
                            .last("limit 1"));
            if (existing != null) {
                existing.setLastActiveAt(now);
                applicationMapper.updateById(existing);
                return toVO(existing);
            }
        }

        ApplicationStage stage = ApplicationStage.fromCode(req.getStage());
        JobApplicationEntity entity = new JobApplicationEntity();
        entity.setUserId(userId);
        entity.setJdDocId(req.getJdDocId());
        entity.setCompany(trimTo(req.getCompany(), 128));
        entity.setRoleTitle(trimTo(req.getRoleTitle(), 200));
        entity.setStage((stage == null ? ApplicationStage.PREPARING : stage).name());
        entity.setResumeVersionId(trimTo(req.getResumeVersionId(), 36));
        entity.setNotes(trimTo(req.getNotes(), 1000));
        entity.setSource("manual");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setLastActiveAt(now);
        applicationMapper.insert(entity);
        return toVO(entity);
    }

    /** 移动阶段（拖拽 / 一键确认 / agent 推断确认）。 */
    @Transactional
    public ApplicationVO updateStage(Long userId, Long applicationId, String stageCode) {
        JobApplicationEntity entity = requireOwned(userId, applicationId);
        ApplicationStage stage = ApplicationStage.fromCode(stageCode);
        if (stage == null) {
            throw new BizException(400, "非法阶段：" + stageCode);
        }
        LocalDateTime now = LocalDateTime.now();
        entity.setStage(stage.name());
        entity.setUpdatedAt(now);
        entity.setLastActiveAt(now);
        applicationMapper.updateById(entity);
        return toVO(entity);
    }

    /**
     * 按 JD 流转阶段（对话驱动）：小职从对话推断意图后调用。
     * 若该 JD 尚无投递记录则按目标阶段自动建一条（对话即事实来源），否则更新其阶段。
     */
    @Transactional
    public ApplicationVO updateStageByJd(Long userId, Long jdDocId, String stageCode, String company, String roleTitle) {
        if (userId == null) {
            throw new BizException(401, "未认证");
        }
        if (jdDocId == null) {
            throw new BizException(400, "缺少 jdDocId");
        }
        ApplicationStage stage = ApplicationStage.fromCode(stageCode);
        if (stage == null) {
            throw new BizException(400, "非法阶段：" + stageCode);
        }
        JobApplicationEntity existing = applicationMapper.selectOne(
                new LambdaQueryWrapper<JobApplicationEntity>()
                        .eq(JobApplicationEntity::getUserId, userId)
                        .eq(JobApplicationEntity::getJdDocId, jdDocId)
                        .isNull(JobApplicationEntity::getDeletedAt)
                        .last("limit 1"));
        if (existing == null) {
            CreateApplicationRequest req = new CreateApplicationRequest();
            req.setJdDocId(jdDocId);
            req.setCompany(company);
            req.setRoleTitle(roleTitle);
            req.setStage(stage.name());
            return createApplication(userId, req);
        }
        LocalDateTime now = LocalDateTime.now();
        existing.setStage(stage.name());
        existing.setUpdatedAt(now);
        existing.setLastActiveAt(now);
        applicationMapper.updateById(existing);
        return toVO(existing);
    }

    /** 该用户每个 JD 已产出的简历版本数（targetJdId → count）。 */
    private Map<Long, Integer> resumeVersionCounts(Long userId) {
        if (userId == null) {
            return Map.of();
        }
        List<com.careermate.model.entity.ResumeVersionEntity> versions = resumeVersionMapper.selectList(
                new LambdaQueryWrapper<com.careermate.model.entity.ResumeVersionEntity>()
                        .eq(com.careermate.model.entity.ResumeVersionEntity::getUserId, userId)
                        .isNotNull(com.careermate.model.entity.ResumeVersionEntity::getTargetJdId));
        Map<Long, Integer> counts = new java.util.HashMap<>();
        if (versions == null) {
            return counts;
        }
        for (com.careermate.model.entity.ResumeVersionEntity v : versions) {
            if (v.getTargetJdId() != null) {
                counts.merge(v.getTargetJdId(), 1, Integer::sum);
            }
        }
        return counts;
    }

    /** 看板：按阶段分列（5 列固定，含空列），列内按最近活跃倒序。 */
    public PipelineBoardVO getBoard(Long userId) {
        PipelineBoardVO board = new PipelineBoardVO();
        List<JobApplicationEntity> rows = userId == null ? List.of() : applicationMapper.selectList(
                new LambdaQueryWrapper<JobApplicationEntity>()
                        .eq(JobApplicationEntity::getUserId, userId)
                        .isNull(JobApplicationEntity::getDeletedAt)
                        .orderByDesc(JobApplicationEntity::getLastActiveAt));

        // 该用户各 JD 已产出的简历版本数（一次查询，jdDocId→count），用于卡片「简历 vN」
        Map<Long, Integer> versionCounts = resumeVersionCounts(userId);

        Map<ApplicationStage, List<ApplicationVO>> grouped = new LinkedHashMap<>();
        for (ApplicationStage s : ApplicationStage.values()) {
            grouped.put(s, new ArrayList<>());
        }
        for (JobApplicationEntity row : rows) {
            ApplicationStage s = ApplicationStage.fromCode(row.getStage());
            ApplicationVO vo = toVO(row);
            if (row.getJdDocId() != null) {
                vo.setResumeVersionCount(versionCounts.getOrDefault(row.getJdDocId(), 0));
            }
            grouped.get(s == null ? ApplicationStage.PREPARING : s).add(vo);
        }

        List<PipelineBoardVO.Column> columns = new ArrayList<>();
        for (ApplicationStage s : ApplicationStage.values()) {
            List<ApplicationVO> apps = grouped.get(s);
            PipelineBoardVO.Column col = new PipelineBoardVO.Column();
            col.setStage(s.name());
            col.setLabel(s.label());
            col.setOrder(s.order());
            col.setCount(apps.size());
            col.setApplications(apps);
            columns.add(col);
        }
        board.setColumns(columns);
        board.setTotal(rows.size());
        return board;
    }

    public ApplicationVO getApplication(Long userId, Long applicationId) {
        return toVO(requireOwned(userId, applicationId));
    }

    /** 卡片改名：空/纯空白 → 清除自定义名（回退自动生成）。 */
    @Transactional
    public ApplicationVO updateName(Long userId, Long applicationId, String displayName) {
        JobApplicationEntity entity = requireOwned(userId, applicationId);
        String name = displayName == null ? null : displayName.trim();
        if (name != null && name.length() > 120) {
            name = name.substring(0, 120);
        }
        String finalName = (name == null || name.isEmpty()) ? null : name;
        applicationMapper.updateDisplayName(applicationId, finalName);
        entity.setDisplayName(finalName);
        return toVO(entity);
    }

    /** 归档（软删）。 */
    @Transactional
    public void archiveApplication(Long userId, Long applicationId) {
        JobApplicationEntity entity = requireOwned(userId, applicationId);
        entity.setDeletedAt(LocalDateTime.now());
        applicationMapper.updateById(entity);
    }

    private JobApplicationEntity requireOwned(Long userId, Long applicationId) {
        if (userId == null) {
            throw new BizException(401, "未认证");
        }
        if (applicationId == null) {
            throw new BizException(400, "缺少 applicationId");
        }
        JobApplicationEntity entity = applicationMapper.selectById(applicationId);
        if (entity == null || entity.getDeletedAt() != null) {
            throw new BizException(404, "投递记录不存在");
        }
        if (!userId.equals(entity.getUserId())) {
            throw new BizException(403, "无权访问该投递记录");
        }
        return entity;
    }

    private static ApplicationVO toVO(JobApplicationEntity e) {
        ApplicationVO vo = new ApplicationVO();
        vo.setId(e.getId());
        vo.setJdDocId(e.getJdDocId());
        vo.setCompany(e.getCompany());
        vo.setRoleTitle(e.getRoleTitle());
        vo.setDisplayName(e.getDisplayName());
        vo.setStage(e.getStage());
        ApplicationStage stage = ApplicationStage.fromCode(e.getStage());
        vo.setStageLabel(stage == null ? e.getStage() : stage.label());
        vo.setResumeVersionId(e.getResumeVersionId());
        vo.setNeedsSalaryNegotiation(ApplicationStage.OFFER.name().equals(e.getStage()));
        vo.setNotes(e.getNotes());
        vo.setCreatedAt(e.getCreatedAt());
        vo.setUpdatedAt(e.getUpdatedAt());
        vo.setLastActiveAt(e.getLastActiveAt());
        return vo;
    }

    private static String trimTo(String s, int max) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        if (t.isEmpty()) {
            return null;
        }
        return t.length() <= max ? t : t.substring(0, max);
    }
}
