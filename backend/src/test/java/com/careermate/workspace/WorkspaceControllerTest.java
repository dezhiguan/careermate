package com.careermate.workspace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.careermate.security.CurrentUser;
import com.careermate.security.CurrentUserContext;
import com.careermate.testsupport.TestUsers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
class WorkspaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUpUser() {
        CurrentUserContext.set(CurrentUser.builder()
                .userId(TestUsers.USER_A)
                .username(TestUsers.USER_A_NAME)
                .role("user")
                .authenticated(true)
                .build());
    }

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    @Test
    void createWorkspaceReturnsWorkspaceId() throws Exception {
        mockMvc.perform(post("/api/workspace")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workspaceType": "MARKET",
                                  "title": "广州 Java后端",
                                  "goalText": "生成谈薪脚本",
                                  "entryAction": "NEGOTIATION_SCRIPT",
                                  "contextMetadata": {"city": "广州", "role": "Java后端"}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.workspaceId").isNotEmpty())
                .andExpect(jsonPath("$.data.redirectPath").value(org.hamcrest.Matchers.startsWith("/chat/WS-")))
                .andExpect(jsonPath("$.data.workspaceType").value("MARKET"));
    }

    @Test
    void createWorkspaceThenGetWorkspaceShouldSucceed() throws Exception {
        var createResult = mockMvc.perform(post("/api/workspace")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workspaceType": "MARKET",
                                  "title": "广州 Java后端",
                                  "goalText": "生成谈薪脚本",
                                  "entryAction": "NEGOTIATION_SCRIPT",
                                  "contextMetadata": {"city": "广州", "role": "Java后端"}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.workspaceId").isNotEmpty())
                .andReturn();

        JsonNode root = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String workspaceId = root.path("data").path("workspaceId").asText();

        mockMvc.perform(get("/api/workspace/{workspaceId}", workspaceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.workspaceId").value(workspaceId))
                .andExpect(jsonPath("$.data.workspaceType").value("MARKET"));
    }

    @Test
    void createWorkspaceRequiresAuthenticatedUserContext() throws Exception {
        CurrentUserContext.clear();

        mockMvc.perform(post("/api/workspace")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workspaceType": "MARKET",
                                  "title": "test"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }
}
