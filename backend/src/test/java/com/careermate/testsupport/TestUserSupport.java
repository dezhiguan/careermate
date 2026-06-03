package com.careermate.testsupport;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.careermate.mapper.InterviewQuestionMapper;
import com.careermate.mapper.InterviewSessionMapper;
import com.careermate.mapper.JobMatchMapper;
import com.careermate.mapper.ResumeMapper;
import com.careermate.mapper.UserMapper;
import com.careermate.mapper.UserProfileMapper;
import com.careermate.model.entity.InterviewQuestionEntity;
import com.careermate.model.entity.InterviewSessionEntity;
import com.careermate.model.entity.JobMatchEntity;
import com.careermate.model.entity.ResumeEntity;
import com.careermate.model.entity.UserEntity;
import com.careermate.model.entity.UserProfileEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

public final class TestUserSupport {

    private TestUserSupport() {
    }

    public static void ensureTestUsers(UserMapper userMapper, UserProfileMapper userProfileMapper,
                                       PasswordEncoder passwordEncoder) {
        ensureUser(userMapper, userProfileMapper, passwordEncoder, TestUsers.USER_A, TestUsers.USER_A_NAME);
        ensureUser(userMapper, userProfileMapper, passwordEncoder, TestUsers.USER_B, TestUsers.USER_B_NAME);
    }

    public static void cleanupUserBusinessData(
            ResumeMapper resumeMapper,
            JobMatchMapper jobMatchMapper
    ) {
        cleanupUserBusinessData(resumeMapper, jobMatchMapper, null, null);
    }

    public static void cleanupUserBusinessData(
            ResumeMapper resumeMapper,
            JobMatchMapper jobMatchMapper,
            InterviewSessionMapper interviewSessionMapper,
            InterviewQuestionMapper interviewQuestionMapper
    ) {
        List<Long> testUserIds = List.of(TestUsers.USER_A, TestUsers.USER_B);
        if (interviewQuestionMapper != null) {
            interviewQuestionMapper.delete(new LambdaQueryWrapper<InterviewQuestionEntity>()
                    .in(InterviewQuestionEntity::getUserId, testUserIds));
        }
        if (interviewSessionMapper != null) {
            interviewSessionMapper.delete(new LambdaQueryWrapper<InterviewSessionEntity>()
                    .in(InterviewSessionEntity::getUserId, testUserIds));
        }
        jobMatchMapper.delete(new LambdaQueryWrapper<JobMatchEntity>()
                .in(JobMatchEntity::getUserId, testUserIds));
        resumeMapper.delete(new LambdaQueryWrapper<ResumeEntity>()
                .in(ResumeEntity::getUserId, testUserIds));
    }

    private static void ensureUser(UserMapper userMapper, UserProfileMapper userProfileMapper,
                                   PasswordEncoder passwordEncoder, long userId, String username) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            UserEntity entity = new UserEntity();
            entity.setId(userId);
            entity.setUsername(username);
            entity.setPasswordHash(passwordEncoder.encode("test-password-" + userId));
            entity.setEmail(username + "@careermate.test");
            entity.setRole("USER");
            entity.setStatus("ACTIVE");
            userMapper.insert(entity);
        }
        UserProfileEntity profile = userProfileMapper.selectOne(
                new LambdaQueryWrapper<UserProfileEntity>()
                        .eq(UserProfileEntity::getUserId, userId)
                        .last("LIMIT 1")
        );
        if (profile == null) {
            UserProfileEntity entity = new UserProfileEntity();
            entity.setUserId(userId);
            userProfileMapper.insert(entity);
        }
    }
}
