package com.careermate.opportunity;

import com.careermate.common.api.PageResult;
import com.careermate.opportunity.dto.OpportunityDetailVO;
import com.careermate.opportunity.dto.OpportunityListItemVO;
import com.careermate.opportunity.dto.OpportunityPrepareResponse;
import com.careermate.opportunity.service.OpportunityService;
import com.careermate.security.CurrentUser;
import com.careermate.security.CurrentUserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
class OpportunityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OpportunityService opportunityService;

    @BeforeEach
    void setUpUser() {
        CurrentUserContext.set(CurrentUser.builder()
                .userId(1L)
                .username("test-user")
                .role("user")
                .authenticated(true)
                .build());
    }

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    @Test
    void listReturnsCompleteStructure() throws Exception {
        when(opportunityService.list(eq(1L), any())).thenReturn(new PageResult<>(
                1, 1, 10, true, "MATCH",
                List.of(new OpportunityListItemVO(
                        "doc-100", 100L, "星天科技", "算法工程师", null, "15-20K", "北京",
                        "1-3年", 1, 3, "硕士", "100-499人", "2026-06-09",
                        87, "HIGH", List.of("技能命中 Java"), List.of("Java", "Redis"),
                        0.87, null, false
                ))
        ));

        mockMvc.perform(get("/api/opportunity/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.hasResume").value(true))
                .andExpect(jsonPath("$.data.sortStrategy").value("MATCH"))
                .andExpect(jsonPath("$.data.items[0].jdId").value("doc-100"))
                .andExpect(jsonPath("$.data.items[0].company").value("星天科技"))
                .andExpect(jsonPath("$.data.items[0].salaryRange").value("15-20K"))
                .andExpect(jsonPath("$.data.items[0].matchScore").value(87))
                .andExpect(jsonPath("$.data.items[0].skills[0]").value("Java"));
    }

    @Test
    void detailContainsJdContent() throws Exception {
        when(opportunityService.detail(1L, "doc-55")).thenReturn(new OpportunityDetailVO(
                "doc-55", 55L, "星天科技", "算法工程师", null, "北京",
                "1-3年", 1, 3, "硕士", "100-499人", "2026-06-09",
                80, "MEDIUM", List.of("技能命中 Java"), List.of("Java"),
                0.8, null,
                "完整 JD 描述正文", null, null, null
        ));

        mockMvc.perform(get("/api/opportunity/doc-55"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jdContent").value("完整 JD 描述正文"))
                .andExpect(jsonPath("$.data.title").value("算法工程师"));
    }

    @Test
    void prepareReturns200WithValidBody() throws Exception {
        when(opportunityService.prepare(1L, "doc-9")).thenReturn(
                new OpportunityPrepareResponse("WS-abc123def456", "/chat/WS-abc123def456")
        );

        mockMvc.perform(post("/api/opportunity/doc-9/prepare"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.workspaceId").value("WS-abc123def456"))
                .andExpect(jsonPath("$.data.redirectPath").value("/chat/WS-abc123def456"));
    }

}
