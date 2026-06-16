package com.careermate.agent.memory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.careermate.jobmatch.JobMatchJsonSupport;
import com.careermate.mapper.AgentMessageMapper;
import com.careermate.mapper.AgentSessionMapper;
import com.careermate.mapper.CareerProfileMapper;
import com.careermate.mapper.JobMatchMapper;
import com.careermate.mapper.ResumeMapper;
import com.careermate.mapper.UserMapper;
import com.careermate.mapper.UserProfileMapper;
import com.careermate.model.entity.AgentMessageEntity;
import com.careermate.model.entity.AgentSessionEntity;
import com.careermate.model.entity.CareerProfileEntity;
import com.careermate.profile.dto.CareerProfileUpsertRequest;
import com.careermate.profile.service.CareerProfileService;
import com.careermate.testsupport.TestUserSupport;
import com.careermate.testsupport.TestUsers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class AgentMemoryServiceTest {

    @Autowired
    private AgentMemoryService agentMemoryService;

    @Autowired
    private CareerProfileService careerProfileService;

    @Autowired
    private CareerProfileMapper careerProfileMapper;

    @Autowired
    private AgentSessionMapper agentSessionMapper;

    @Autowired
    private AgentMessageMapper agentMessageMapper;

    @Autowired
    private JobMatchJsonSupport jobMatchJsonSupport;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserProfileMapper userProfileMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ResumeMapper resumeMapper;

    @Autowired
    private JobMatchMapper jobMatchMapper;

    @BeforeEach
    void setUp() {
        TestUserSupport.ensureTestUsers(userMapper, userProfileMapper, passwordEncoder);
        TestUserSupport.cleanupUserBusinessData(resumeMapper, jobMatchMapper);
        careerProfileMapper.delete(new LambdaQueryWrapper<CareerProfileEntity>()
                .in(CareerProfileEntity::getUserId, List.of(TestUsers.USER_A, TestUsers.USER_B)));
        agentMessageMapper.delete(new LambdaQueryWrapper<AgentMessageEntity>()
                .in(AgentMessageEntity::getUserId, List.of(TestUsers.USER_A, TestUsers.USER_B)));
        agentSessionMapper.delete(new LambdaQueryWrapper<AgentSessionEntity>()
                .in(AgentSessionEntity::getUserId, List.of(TestUsers.USER_A, TestUsers.USER_B)));
    }

    @Test
    void remembersLowScoreInterviewWeakness() {
        agentMemoryService.rememberInterviewWeakness(
                TestUsers.USER_A,
                65,
                "SKILL",
                "请说明 Redis 缓存一致性方案",
                List.of("缓存穿透", "双写一致性"),
                List.of("补充 Redis 失效策略", "说明缓存雪崩防护")
        );

        CareerProfileEntity entity = careerProfileMapper.selectOne(
                new LambdaQueryWrapper<CareerProfileEntity>()
                        .eq(CareerProfileEntity::getUserId, TestUsers.USER_A)
        );
        assertTrue(entity != null);
        List<String> weaknesses = jobMatchJsonSupport.readStringList(entity.getWeaknessKeywords());
        assertTrue(weaknesses.contains("SKILL"));
        assertTrue(weaknesses.stream().anyMatch(k -> k.contains("Redis")));
        assertTrue(entity.getInterviewWeaknessSummary().contains("Redis"));
    }

    @Test
    void deduplicatesRepeatedWeaknessKeywords() {
        agentMemoryService.rememberInterviewWeakness(
                TestUsers.USER_A,
                60,
                "SKILL",
                "Redis 缓存一致性",
                List.of("缓存穿透"),
                List.of("补充 Redis 失效策略")
        );
        agentMemoryService.rememberInterviewWeakness(
                TestUsers.USER_A,
                55,
                "SKILL",
                "Redis 缓存一致性",
                List.of("缓存穿透"),
                List.of("补充 Redis 失效策略")
        );

        CareerProfileEntity entity = careerProfileMapper.selectOne(
                new LambdaQueryWrapper<CareerProfileEntity>()
                        .eq(CareerProfileEntity::getUserId, TestUsers.USER_A)
        );
        List<String> weaknesses = jobMatchJsonSupport.readStringList(entity.getWeaknessKeywords());
        long typeCount = weaknesses.stream().filter("SKILL"::equals).count();
        assertEquals(1, typeCount);
    }

    @Test
    void doesNotOverwriteExistingTargetRoleAndCity() {
        CareerProfileUpsertRequest request = new CareerProfileUpsertRequest();
        request.setTargetRole("Java 后端开发工程师");
        request.setTargetCity("广州");
        careerProfileService.upsertProfile(TestUsers.USER_A, request, "manual");

        agentMemoryService.rememberInterviewWeakness(
                TestUsers.USER_A,
                50,
                "BEHAVIOR",
                "团队协作案例",
                List.of(),
                List.of("STAR 结构不完整")
        );

        CareerProfileEntity entity = careerProfileMapper.selectOne(
                new LambdaQueryWrapper<CareerProfileEntity>()
                        .eq(CareerProfileEntity::getUserId, TestUsers.USER_A)
        );
        assertEquals("Java 后端开发工程师", entity.getTargetRole());
        assertEquals("广州", entity.getTargetCity());
        assertFalse(jobMatchJsonSupport.readStringList(entity.getWeaknessKeywords()).isEmpty());
    }

    @Test
    void refreshConversationSummaryFromRecentMessages() {
        OffsetDateTime now = OffsetDateTime.now();
        AgentSessionEntity session = new AgentSessionEntity();
        session.setSessionId("memory-summary-session-" + System.nanoTime());
        session.setUserId(TestUsers.USER_A);
        session.setStatus("ACTIVE");
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        agentSessionMapper.insert(session);

        String sessionId = session.getSessionId();
        insertMessage(session.getId(), TestUsers.USER_A, "user", "我想找 Java 后端岗位", 1, now);
        insertMessage(session.getId(), TestUsers.USER_A, "agent", "好的，已记录你的目标。", 2, now);

        agentMemoryService.refreshConversationSummary(TestUsers.USER_A, sessionId);

        AgentSessionEntity updated = agentSessionMapper.selectById(session.getId());
        assertTrue(updated.getConversationSummary().contains("Java 后端"));
        assertTrue(updated.getConversationSummaryUpdatedAt() != null);

        AgentMemoryContext memory = agentMemoryService.loadMemoryContext(
                TestUsers.USER_A,
                sessionId
        );
        assertTrue(memory.isHasSessionSummary());
        assertTrue(memory.getContextText().contains("当前会话摘要"));
    }

    @Test
    void skipsCardJsonWhenRefreshingSummary() {
        OffsetDateTime now = OffsetDateTime.now();
        AgentSessionEntity session = new AgentSessionEntity();
        session.setSessionId("memory-card-session-" + System.nanoTime());
        session.setUserId(TestUsers.USER_A);
        session.setStatus("ACTIVE");
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        agentSessionMapper.insert(session);

        String sessionId = session.getSessionId();
        insertMessage(session.getId(), TestUsers.USER_A, "user", "{\"cardType\":\"RESUME\"}", 1, now);
        insertMessage(session.getId(), TestUsers.USER_A, "agent", "简历卡片已生成", 2, now);

        agentMemoryService.refreshConversationSummary(TestUsers.USER_A, sessionId);

        AgentSessionEntity updated = agentSessionMapper.selectById(session.getId());
        assertFalse(updated.getConversationSummary().contains("cardType"));
        assertTrue(updated.getConversationSummary().contains("简历卡片"));
    }

    private void insertMessage(
            Long sessionPk,
            Long userId,
            String role,
            String content,
            int sequenceNo,
            OffsetDateTime now
    ) {
        AgentMessageEntity message = new AgentMessageEntity();
        message.setSessionId(sessionPk);
        message.setUserId(userId);
        message.setRole(role);
        message.setContent(content);
        message.setMessageType("text");
        message.setSequenceNo(sequenceNo);
        message.setCreatedAt(now);
        agentMessageMapper.insert(message);
    }
}
