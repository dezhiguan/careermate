package com.careermate.agent.context;

import com.careermate.agent.session.AgentSessionService;
import com.careermate.mapper.JobMatchMapper;
import com.careermate.mapper.ResumeMapper;
import com.careermate.mapper.UserMapper;
import com.careermate.mapper.UserProfileMapper;
import com.careermate.testsupport.TestUserSupport;
import com.careermate.testsupport.TestUsers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class AgentConversationContextProviderTest {

    @Autowired
    private AgentConversationContextProvider conversationContextProvider;

    @Autowired
    private AgentSessionService agentSessionService;

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
    }

    @Test
    void loadsRecentMessagesForCurrentSession() {
        long userId = TestUsers.USER_A;
        String sessionId = agentSessionService.createSession(userId).getSessionId();
        agentSessionService.appendMessage(userId, sessionId, "user", "我的目标是 Java 后端", "text");
        agentSessionService.appendMessage(userId, sessionId, "agent", "收到，已记录你的目标。", "text");
        agentSessionService.appendMessage(userId, sessionId, "user", "你还记得我的目标吗", "text");

        ConversationContextResult result = conversationContextProvider.load(
                userId,
                sessionId,
                "你还记得我的目标吗"
        );

        assertTrue(result.isAvailable());
        assertTrue(result.getContextText().contains("【当前会话历史】"));
        assertTrue(result.getContextText().contains("user: 我的目标是 Java 后端"));
        assertTrue(result.getContextText().contains("assistant: 收到，已记录你的目标。"));
        assertFalse(result.getContextText().contains("你还记得我的目标吗"));
        assertEquals(2, result.getMessageCount());
    }

    @Test
    void doesNotLoadOtherUserMessages() {
        String sessionA = agentSessionService.createSession(TestUsers.USER_A).getSessionId();
        agentSessionService.appendMessage(TestUsers.USER_A, sessionA, "user", "用户A专属消息", "text");

        String sessionB = agentSessionService.createSession(TestUsers.USER_B).getSessionId();
        agentSessionService.appendMessage(TestUsers.USER_B, sessionB, "user", "用户B专属消息", "text");
        agentSessionService.appendMessage(TestUsers.USER_B, sessionB, "user", "用户B第二条", "text");

        ConversationContextResult result = conversationContextProvider.load(
                TestUsers.USER_A,
                sessionA,
                "不会出现的排除句"
        );

        assertTrue(result.isAvailable());
        assertTrue(result.getContextText().contains("用户A专属消息"));
        assertFalse(result.getContextText().contains("用户B专属消息"));
    }

    @Test
    void doesNotLoadOtherSessionMessages() {
        long userId = TestUsers.USER_A;
        String sessionOne = agentSessionService.createSession(userId).getSessionId();
        agentSessionService.appendMessage(userId, sessionOne, "user", "会话一消息", "text");

        String sessionTwo = agentSessionService.createSession(userId).getSessionId();
        agentSessionService.appendMessage(userId, sessionTwo, "user", "会话二消息", "text");
        agentSessionService.appendMessage(userId, sessionTwo, "user", "会话二当前消息", "text");

        ConversationContextResult result = conversationContextProvider.load(
                userId,
                sessionTwo,
                "会话二当前消息"
        );

        assertTrue(result.isAvailable());
        assertTrue(result.getContextText().contains("会话二消息"));
        assertFalse(result.getContextText().contains("会话一消息"));
    }

}

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "careermate.agent.conversation-context-max-chars=120")
class AgentConversationContextProviderTruncateTest {

    @Autowired
    private AgentConversationContextProvider conversationContextProvider;

    @Autowired
    private AgentSessionService agentSessionService;

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
    }

    @Test
    void truncatesWhenExceedingMaxChars() {
        long userId = TestUsers.USER_A;
        String sessionId = agentSessionService.createSession(userId).getSessionId();
        agentSessionService.appendMessage(userId, sessionId, "user", "A".repeat(80), "text");
        agentSessionService.appendMessage(userId, sessionId, "agent", "B".repeat(80), "text");
        agentSessionService.appendMessage(userId, sessionId, "user", "C".repeat(80), "text");
        agentSessionService.appendMessage(userId, sessionId, "user", "当前排除消息", "text");

        ConversationContextResult result = conversationContextProvider.load(
                userId,
                sessionId,
                "当前排除消息"
        );

        assertTrue(result.isAvailable());
        assertTrue(result.getCharCount() <= 120);
        assertFalse(result.getContextText().contains("A".repeat(80)));
    }
}
