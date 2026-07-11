package com.careermate.auth.events;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.careermate.mapper.AgentMessageMapper;
import com.careermate.mapper.AgentSessionMapper;
import com.careermate.mapper.CareerProfileMapper;
import com.careermate.mapper.InterviewQuestionMapper;
import com.careermate.mapper.InterviewSessionMapper;
import com.careermate.mapper.JobMatchMapper;
import com.careermate.mapper.ResumeMapper;
import com.careermate.mapper.ResumeVersionMapper;
import com.careermate.mapper.UserMapper;
import com.careermate.mapper.UserProfileMapper;
import com.careermate.model.entity.AgentMessageEntity;
import com.careermate.model.entity.AgentSessionEntity;
import com.careermate.model.entity.CareerProfileEntity;
import com.careermate.model.entity.InterviewQuestionEntity;
import com.careermate.model.entity.InterviewSessionEntity;
import com.careermate.model.entity.JobMatchEntity;
import com.careermate.model.entity.ResumeEntity;
import com.careermate.model.entity.ResumeVersionEntity;
import com.careermate.model.entity.UserEntity;
import com.careermate.model.entity.UserProfileEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 应用级注销到期后本地数据清理（PIPL 合规）：匿名化 CareerMate 用户主记录 PII + 删除个人内容。
 * 由 {@link AuthEventService} 收到网关 {@code user.app_removed(app=careermate)} 事件时调用。
 * 幂等：已 DELETED 或用户不存在则跳过。
 */
@Slf4j
@Component
public class LocalAccountPurger {

    private final UserMapper userMapper;
    private final ResumeMapper resumeMapper;
    private final ResumeVersionMapper resumeVersionMapper;
    private final AgentSessionMapper agentSessionMapper;
    private final AgentMessageMapper agentMessageMapper;
    private final InterviewSessionMapper interviewSessionMapper;
    private final InterviewQuestionMapper interviewQuestionMapper;
    private final JobMatchMapper jobMatchMapper;
    private final CareerProfileMapper careerProfileMapper;
    private final UserProfileMapper userProfileMapper;

    public LocalAccountPurger(
            UserMapper userMapper, ResumeMapper resumeMapper, ResumeVersionMapper resumeVersionMapper,
            AgentSessionMapper agentSessionMapper, AgentMessageMapper agentMessageMapper,
            InterviewSessionMapper interviewSessionMapper, InterviewQuestionMapper interviewQuestionMapper,
            JobMatchMapper jobMatchMapper, CareerProfileMapper careerProfileMapper,
            UserProfileMapper userProfileMapper) {
        this.userMapper = userMapper;
        this.resumeMapper = resumeMapper;
        this.resumeVersionMapper = resumeVersionMapper;
        this.agentSessionMapper = agentSessionMapper;
        this.agentMessageMapper = agentMessageMapper;
        this.interviewSessionMapper = interviewSessionMapper;
        this.interviewQuestionMapper = interviewQuestionMapper;
        this.jobMatchMapper = jobMatchMapper;
        this.careerProfileMapper = careerProfileMapper;
        this.userProfileMapper = userProfileMapper;
    }

    /**
     * 清理指定 auth-gateway 用户在 CareerMate 的本地数据。
     * @param authUserId 网关侧 user_id
     * @return true=已清理，false=用户不存在或已清理（幂等跳过）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean purgeByAuthUserId(long authUserId) {
        UserEntity user = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getAuthUserId, authUserId)
                .last("LIMIT 1"));
        if (user == null || "DELETED".equalsIgnoreCase(user.getStatus())) {
            return false; // 幂等
        }
        Long uid = user.getId();
        // 删除个人内容
        resumeMapper.delete(new LambdaQueryWrapper<ResumeEntity>().eq(ResumeEntity::getUserId, uid));
        resumeVersionMapper.delete(new LambdaQueryWrapper<ResumeVersionEntity>().eq(ResumeVersionEntity::getUserId, uid));
        agentSessionMapper.delete(new LambdaQueryWrapper<AgentSessionEntity>().eq(AgentSessionEntity::getUserId, uid));
        agentMessageMapper.delete(new LambdaQueryWrapper<AgentMessageEntity>().eq(AgentMessageEntity::getUserId, uid));
        interviewSessionMapper.delete(new LambdaQueryWrapper<InterviewSessionEntity>().eq(InterviewSessionEntity::getUserId, uid));
        interviewQuestionMapper.delete(new LambdaQueryWrapper<InterviewQuestionEntity>().eq(InterviewQuestionEntity::getUserId, uid));
        jobMatchMapper.delete(new LambdaQueryWrapper<JobMatchEntity>().eq(JobMatchEntity::getUserId, uid));
        careerProfileMapper.delete(new LambdaQueryWrapper<CareerProfileEntity>().eq(CareerProfileEntity::getUserId, uid));
        userProfileMapper.delete(new LambdaQueryWrapper<UserProfileEntity>().eq(UserProfileEntity::getUserId, uid));
        // 匿名化用户主记录 PII（不可逆），置 DELETED
        user.setPhone(null);
        user.setEmail(null);
        user.setUsername("deleted_" + uid);
        user.setDisplayName("已注销用户");
        user.setAvatarUrl(null);
        user.setPasswordHash(null);
        user.setStatus("DELETED");
        user.setPendingDeletionAt(null);
        user.setDeletionScheduledAt(null);
        userMapper.updateById(user);
        log.info("Local account purged for authUserId={} (local user {})", authUserId, uid);
        return true;
    }
}
