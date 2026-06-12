package com.careermate.workspace.support;

import com.careermate.common.exception.BizException;
import com.careermate.mapper.UserMapper;
import com.careermate.mapper.UserProfileMapper;
import com.careermate.model.entity.AgentMessageEntity;
import com.careermate.model.entity.AgentSessionEntity;
import com.careermate.security.CurrentUser;
import com.careermate.security.CurrentUserContext;
import com.careermate.testsupport.TestUserSupport;
import com.careermate.testsupport.TestUsers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class WorkspaceSessionRepositoryTest {

    @Autowired
    private WorkspaceSessionRepository repository;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserProfileMapper userProfileMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        TestUserSupport.ensureTestUsers(userMapper, userProfileMapper, passwordEncoder);
        loginAs(TestUsers.USER_A);
    }

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    @Test
    void createJdPrepSessionAndAppendMessages() {
        AgentSessionEntity session = repository.createJdPrepSession(
                TestUsers.USER_A,
                "jd-doc-1",
                "{\"company\":\"腾讯\",\"title\":\"Java 后端\"}",
                "腾讯 Java 后端"
        );

        assertNotNull(session.getSessionId());
        assertTrue(session.getSessionId().startsWith("WS-"));
        assertEquals(WorkspaceSessionRepository.WORKSPACE_JD_PREP, session.getWorkspaceType());
        assertEquals("jd-doc-1", session.getJdId());

        AgentMessageEntity first = repository.appendMessage(
                TestUsers.USER_A, session, "user", "你好", "text", null, null
        );
        AgentMessageEntity second = repository.appendMessage(
                TestUsers.USER_A, session, "agent", "回复", "text", "{\"k\":\"v\"}", null
        );

        assertEquals(1, first.getSequenceNo());
        assertEquals(2, second.getSequenceNo());
        assertEquals("{\"k\":\"v\"}", second.getMetadata());

        List<AgentMessageEntity> all = repository.listMessages(session.getId(), TestUsers.USER_A, null, 10);
        assertEquals(2, all.size());

        List<AgentMessageEntity> afterFirst = repository.listMessages(session.getId(), TestUsers.USER_A, 1, 10);
        assertEquals(1, afterFirst.size());
        assertEquals(2, afterFirst.get(0).getSequenceNo());
    }

    @Test
    void findActiveJdPrepSessionReturnsLatest() {
        repository.createJdPrepSession(
                TestUsers.USER_A, "jd-a", "{}", "first"
        );
        AgentSessionEntity latest = repository.createJdPrepSession(
                TestUsers.USER_A, "jd-a", "{}", "second"
        );

        AgentSessionEntity found = repository.findActiveJdPrepSession(TestUsers.USER_A, "jd-a");

        assertNotNull(found);
        assertEquals(latest.getSessionId(), found.getSessionId());
    }

    @Test
    void requireSessionEnforcesOwnership() {
        AgentSessionEntity session = repository.createJdPrepSession(
                TestUsers.USER_A, "jd-own", "{}", "mine"
        );

        assertEquals(session.getSessionId(), repository.requireSession(TestUsers.USER_A, session.getSessionId()).getSessionId());

        loginAs(TestUsers.USER_B);
        BizException forbidden = assertThrows(
                BizException.class,
                () -> repository.requireSession(TestUsers.USER_B, session.getSessionId())
        );
        assertEquals(403, forbidden.getCode());
    }

    @Test
    void requireSessionThrowsWhenMissing() {
        BizException notFound = assertThrows(
                BizException.class,
                () -> repository.requireSession(TestUsers.USER_A, "WS-missing")
        );
        assertEquals(404, notFound.getCode());
    }

    @Test
    void getSessionIfExistsReturnsNullOnFailure() {
        assertNull(repository.getSessionIfExists(TestUsers.USER_A, "WS-missing"));
    }

    @Test
    void appendMessageRejectsBlankContent() {
        AgentSessionEntity session = repository.createJdPrepSession(
                TestUsers.USER_A, "jd-blank", "{}", "title"
        );

        BizException ex = assertThrows(
                BizException.class,
                () -> repository.appendMessage(TestUsers.USER_A, session, "user", "  ", "text", null, null)
        );
        assertEquals(400, ex.getCode());
    }

    @Test
    void listMessagesClampsLimit() {
        AgentSessionEntity session = repository.createJdPrepSession(
                TestUsers.USER_A, "jd-limit", "{}", "title"
        );
        for (int i = 0; i < 3; i++) {
            repository.appendMessage(
                    TestUsers.USER_A, session, "user", "msg-" + i, "text", null, null
            );
        }

        assertEquals(3, repository.listMessages(session.getId(), TestUsers.USER_A, null, 200).size());
        assertEquals(1, repository.listMessages(session.getId(), TestUsers.USER_A, null, 0).size());
    }

    private void loginAs(long userId) {
        CurrentUserContext.set(CurrentUser.builder()
                .userId(userId)
                .username(userId == TestUsers.USER_A ? TestUsers.USER_A_NAME : TestUsers.USER_B_NAME)
                .role("USER")
                .authenticated(true)
                .build());
    }
}
