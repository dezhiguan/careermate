package com.careermate.resume;

import com.careermate.resume.dto.ResumeCreateRequest;
import com.careermate.security.CurrentUser;
import com.careermate.security.CurrentUserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class ResumeContextProviderTest {

    @Autowired
    private ResumeContextProvider resumeContextProvider;

    @Autowired
    private ResumeService resumeService;

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
        loginAs(1L, "local-user");
        String title = "ProviderTest-" + System.nanoTime();
        ResumeCreateRequest request = new ResumeCreateRequest();
        request.setTitle(title);
        request.setContent("熟悉 Playwright 与 Spring Boot。");
        var created = resumeService.createResume(request);
        resumeService.setDefaultResume(created.getId());

        ResumeContext ctx = resumeContextProvider.getResumeContext(1L);
        assertTrue(ctx.isAvailable());
        assertEquals(title, ctx.getTitle());
        assertTrue(ctx.getContextText().contains("用户默认简历："));
        assertTrue(ctx.getContextText().contains("Playwright"));
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
