package com.careermate.resume;

import com.careermate.mapper.JobMatchMapper;
import com.careermate.mapper.ResumeMapper;
import com.careermate.mapper.UserMapper;
import com.careermate.mapper.UserProfileMapper;
import com.careermate.resume.dto.ResumeCreateRequest;
import com.careermate.resume.service.ResumeService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class ResumeContextProviderTest {

    @Autowired
    private ResumeContextProvider resumeContextProvider;

    @Autowired
    private ResumeService resumeService;

    @Autowired
    private ResumeMapper resumeMapper;

    @Autowired
    private JobMatchMapper jobMatchMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserProfileMapper userProfileMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        TestUserSupport.ensureTestUsers(userMapper, userProfileMapper, passwordEncoder);
        TestUserSupport.cleanupUserBusinessData(resumeMapper, jobMatchMapper);
    }

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    @Test
    void returnsEmptyWhenNoDefaultResume() {
        ResumeContext ctx = resumeContextProvider.getResumeContext(999_999L);
        assertFalse(ctx.isAvailable());
        assertEquals("当前用户暂无默认简历。", ctx.getContextText());
    }

    @Test
    void returnsDefaultResumeContextForUser() {
        loginAs(TestUsers.USER_A, TestUsers.USER_A_NAME);
        String title = "test_provider_" + System.nanoTime();
        ResumeCreateRequest request = new ResumeCreateRequest();
        request.setTitle(title);
        request.setContent("熟悉 Playwright 与 Spring Boot。");
        var created = resumeService.createResume(request);
        resumeService.setDefaultResume(created.getId());

        ResumeContext ctx = resumeContextProvider.getResumeContext(TestUsers.USER_A);
        assertTrue(ctx.isAvailable());
        assertEquals(title, ctx.getTitle());
        assertTrue(ctx.getContextText().contains("用户默认简历："));
        assertTrue(ctx.getContextText().contains("Playwright"));
    }

    @Test
    void truncatesLongResumeInContextText() {
        loginAs(TestUsers.USER_A, TestUsers.USER_A_NAME);
        String title = "test_long_" + System.nanoTime();
        ResumeCreateRequest request = new ResumeCreateRequest();
        request.setTitle(title);
        request.setContent("x".repeat(9000));
        var created = resumeService.createResume(request);
        resumeService.setDefaultResume(created.getId());

        ResumeContext ctx = resumeContextProvider.getResumeContext(TestUsers.USER_A);
        assertTrue(ctx.isAvailable());
        assertEquals(9000, ctx.getContent().length());
        assertTrue(ctx.getContextText().contains("简历内容已截断"));
        assertTrue(ctx.getContextText().length() < 9000);
    }

    private void loginAs(long userId, String username) {
        CurrentUserContext.set(CurrentUser.builder()
                .userId(userId)
                .username(username)
                .role("USER")
                .authenticated(true)
                .build());
    }
}
