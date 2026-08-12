package com.careermate.pipeline.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.careermate.common.exception.BizException;
import com.careermate.mapper.SavedJobMapper;
import com.careermate.model.entity.SavedJobEntity;
import com.careermate.pipeline.dto.ApplicationVO;
import com.careermate.pipeline.dto.CreateApplicationRequest;
import com.careermate.pipeline.dto.SaveJobRequest;
import com.careermate.pipeline.dto.SavedJobVO;
import com.careermate.common.support.JdDocIds;
import com.careermate.opportunity.dto.OpportunityDetailVO;
import com.careermate.opportunity.service.OpportunityService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 暂存区服务：收藏但未动的 JD（不占投递看板），一键转为机会。
 *
 * <p>与 {@link PipelineService}（正在推进的机会）分开：收藏是"还没动"，转机会即进看板并从暂存区移除。
 */
@Slf4j
@Service
public class SavedJobService {

    private final SavedJobMapper savedJobMapper;
    private final PipelineService pipelineService;
    private final OpportunityService opportunityService;

    public SavedJobService(SavedJobMapper savedJobMapper,
                           PipelineService pipelineService,
                           OpportunityService opportunityService) {
        this.savedJobMapper = savedJobMapper;
        this.pipelineService = pipelineService;
        this.opportunityService = opportunityService;
    }

    /** 回填用的 JD 查询：查不到就算了，绝不能让收藏这个动作本身失败。 */
    private OpportunityDetailVO loadJdQuietly(Long userId, Long jdDocId) {
        try {
            return opportunityService.detail(userId, JdDocIds.format(jdDocId));
        } catch (RuntimeException e) {
            log.info("收藏回填 JD 信息失败 jdDocId={}: {}", jdDocId, e.getMessage());
            return null;
        }
    }

    /** 暂存区列表（最近收藏在前）。 */
    public List<SavedJobVO> list(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return savedJobMapper.selectList(activeByUser(userId).orderByDesc(SavedJobEntity::getSavedAt)).stream()
                .map(SavedJobService::toVO)
                .toList();
    }

    /** 收藏一个 JD（按 jdDocId 幂等，已收藏则原样返回）。 */
    @Transactional
    public SavedJobVO save(Long userId, SaveJobRequest request) {
        if (userId == null) {
            throw new BizException(401, "未认证");
        }
        Long jdDocId = request == null ? null : request.getJdDocId();
        if (jdDocId == null) {
            throw new BizException(400, "缺少 jdDocId");
        }
        SavedJobEntity existing = savedJobMapper.selectOne(byUserJd(userId, jdDocId).last("LIMIT 1"));
        if (existing != null) {
            return toVO(existing);
        }
        String company = trimTo(request.getCompany(), 128);
        String roleTitle = trimTo(request.getRoleTitle(), 200);
        // 只给 jdDocId 也要能存出一条可读记录：调用方（Agent 工具、MCP、第三方）未必带上公司和岗位名，
        // 缺了就按 jdDocId 回查 JD 补齐。此前直接存 null，收藏页只剩一张空白卡片。
        if (company == null || roleTitle == null) {
            OpportunityDetailVO jd = loadJdQuietly(userId, jdDocId);
            if (jd != null) {
                company = company != null ? company : trimTo(jd.company(), 128);
                roleTitle = roleTitle != null ? roleTitle : trimTo(jd.title(), 200);
            }
        }

        SavedJobEntity entity = new SavedJobEntity();
        entity.setUserId(userId);
        entity.setJdDocId(jdDocId);
        entity.setCompany(company);
        entity.setRoleTitle(roleTitle);
        entity.setSavedAt(LocalDateTime.now());
        savedJobMapper.insert(entity);
        return toVO(entity);
    }

    /** 取消收藏（按 jdDocId 软删）。 */
    @Transactional
    public void removeByJd(Long userId, Long jdDocId) {
        if (userId == null) {
            throw new BizException(401, "未认证");
        }
        if (jdDocId == null) {
            throw new BizException(400, "缺少 jdDocId");
        }
        SavedJobEntity entity = savedJobMapper.selectOne(byUserJd(userId, jdDocId).last("LIMIT 1"));
        if (entity != null) {
            entity.setDeletedAt(LocalDateTime.now());
            savedJobMapper.updateById(entity);
        }
    }

    /** 一键转为机会：进投递看板（jd 去重），并从暂存区移除。 */
    @Transactional
    public ApplicationVO promote(Long userId, Long jdDocId) {
        if (userId == null) {
            throw new BizException(401, "未认证");
        }
        if (jdDocId == null) {
            throw new BizException(400, "缺少 jdDocId");
        }
        SavedJobEntity entity = savedJobMapper.selectOne(byUserJd(userId, jdDocId).last("LIMIT 1"));
        if (entity == null) {
            throw new BizException(404, "暂存记录不存在");
        }
        CreateApplicationRequest req = new CreateApplicationRequest();
        req.setJdDocId(jdDocId);
        req.setCompany(entity.getCompany());
        req.setRoleTitle(entity.getRoleTitle());
        ApplicationVO vo = pipelineService.createApplication(userId, req);

        entity.setDeletedAt(LocalDateTime.now());
        savedJobMapper.updateById(entity);
        return vo;
    }

    private LambdaQueryWrapper<SavedJobEntity> activeByUser(Long userId) {
        return new LambdaQueryWrapper<SavedJobEntity>()
                .eq(SavedJobEntity::getUserId, userId)
                .isNull(SavedJobEntity::getDeletedAt);
    }

    private LambdaQueryWrapper<SavedJobEntity> byUserJd(Long userId, Long jdDocId) {
        return activeByUser(userId).eq(SavedJobEntity::getJdDocId, jdDocId);
    }

    private static SavedJobVO toVO(SavedJobEntity e) {
        return new SavedJobVO(e.getId(), e.getJdDocId(), e.getCompany(), e.getRoleTitle(), e.getSavedAt());
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
