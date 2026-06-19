package com.careermate.auth;

import com.careermate.auth.dto.RegisterRequest;
import com.careermate.auth.gateway.AuthGatewayClient;
import com.careermate.auth.gateway.AuthGatewayCookieSupport;
import com.careermate.mapper.UserMapper;
import com.careermate.model.entity.UserEntity;
import com.careermate.observability.MdcKeys;
import com.careermate.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = true)
class AuthMeJwtTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserMapper userMapper;

    @MockBean
    private AuthGatewayClient authGatewayClient;

    @MockBean
    private AuthGatewayCookieSupport cookieSupport;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        doNothing().when(cookieSupport).writeRefreshCookie(anyString());
        when(authGatewayClient.loginPassword(anyString(), anyString())).thenAnswer(invocation -> {
            String account = invocation.getArgument(0, String.class);
            AuthGatewayClient.TokenResponse response = new AuthGatewayClient.TokenResponse();
            response.setAccessToken("test-token:" + account);
            response.setRefreshToken("test-refresh:" + account);
            response.setTokenType("Bearer");
            response.setExpiresIn(3600L);
            return response;
        });
        when(jwtTokenProvider.getUserId(anyString())).thenAnswer(invocation ->
                userIdFromToken(invocation.getArgument(0, String.class)));
        when(jwtTokenProvider.getPlatformRole(anyString())).thenReturn("USER");
        when(jwtTokenProvider.parseToken(anyString())).thenAnswer(invocation -> {
            Long userId = userIdFromToken(invocation.getArgument(0, String.class));
            Claims claims = Jwts.claims();
            claims.setSubject(String.valueOf(userId));
            claims.put("user_id", userId);
            claims.setId("test-jti:" + userId);
            claims.setIssuedAt(new java.util.Date());
            return claims;
        });
    }

    @Test
    void authMeRequiresBearerToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(header().exists(MdcKeys.HEADER_TRACE_ID))
                .andExpect(header().exists(MdcKeys.HEADER_REQUEST_ID));
    }

    @Test
    void authMeReturnsRegisteredUserWhenBearerPresent() throws Exception {
        String username = "test_auth_me_token_" + System.nanoTime();
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setUsername(username);
        registerRequest.setPassword("Test123456!");
        registerRequest.setEmail(username + "@careermate.test");

        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode registerJson = objectMapper.readTree(registerResult.getResponse().getContentAsString());
        String token = registerJson.path("data").path("token").asText();

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value(username))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(header().exists(MdcKeys.HEADER_TRACE_ID));
    }

    private Long userIdFromToken(String token) {
        String account = token.substring(token.indexOf(':') + 1);
        UserEntity user = userMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getUsername, account)
                .last("LIMIT 1"));
        return user.getId();
    }
}
