package com.careermate.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.careermate.mapper.UserMapper;
import com.careermate.mapper.UserProfileMapper;
import com.careermate.model.entity.UserEntity;
import com.careermate.model.entity.UserProfileEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;

@Slf4j
@Component
public class SingleUserInitializer implements ApplicationRunner {

    private final SecurityProperties securityProperties;
    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    public SingleUserInitializer(
            SecurityProperties securityProperties,
            UserMapper userMapper,
            UserProfileMapper userProfileMapper,
            PasswordEncoder passwordEncoder,
            JdbcTemplate jdbcTemplate
    ) {
        this.securityProperties = securityProperties;
        this.userMapper = userMapper;
        this.userProfileMapper = userProfileMapper;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!"single-user".equals(securityProperties.getMode())) {
            return;
        }
        Long userId = securityProperties.getSingleUser().getUserId();
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            UserEntity localUser = new UserEntity();
            localUser.setId(userId);
            localUser.setUsername(securityProperties.getSingleUser().getUsername());
            localUser.setPasswordHash(passwordEncoder.encode("local-password-" + System.nanoTime()));
            localUser.setRole("USER");
            localUser.setStatus("ACTIVE");
            userMapper.insert(localUser);
            log.info("Initialized single-user account: userId={}, username={}", userId, localUser.getUsername());
        }
        UserProfileEntity profile = userProfileMapper.selectOne(new LambdaQueryWrapper<UserProfileEntity>()
                .eq(UserProfileEntity::getUserId, userId)
                .last("LIMIT 1"));
        if (profile == null) {
            UserProfileEntity localProfile = new UserProfileEntity();
            localProfile.setUserId(userId);
            userProfileMapper.insert(localProfile);
            log.info("Initialized single-user profile: userId={}", userId);
        }
        // Keep BIGSERIAL sequence aligned after explicit id insert.
        jdbcTemplate.execute("SELECT setval('users_id_seq', COALESCE((SELECT MAX(id) FROM users), 1))");
    }
}
