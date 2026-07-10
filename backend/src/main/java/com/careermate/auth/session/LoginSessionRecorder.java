package com.careermate.auth.session;

import com.careermate.mapper.UserLoginSessionMapper;
import com.careermate.model.entity.UserLoginSessionEntity;
import com.careermate.common.web.ClientIpResolver;
import com.careermate.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;

/**
 * 登录成功后写入 user_login_sessions，使"账号与安全 - 登录设备"列表有数据，
 * 且踢出会话可按 session 精确吊销（session id == access token 的 jti）。
 *
 * <p>之前登录链路从不写入该表，导致列表恒空、踢出无对象。此记录器由短信登录与
 * 密码登录两条链路共用，best-effort：写入失败不阻断登录。</p>
 */
@Slf4j
@Component
public class LoginSessionRecorder {

    private final UserLoginSessionMapper sessionMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final ClientIpResolver clientIpResolver;

    public LoginSessionRecorder(
            UserLoginSessionMapper sessionMapper,
            JwtTokenProvider jwtTokenProvider,
            ClientIpResolver clientIpResolver
    ) {
        this.sessionMapper = sessionMapper;
        this.jwtTokenProvider = jwtTokenProvider;
        this.clientIpResolver = clientIpResolver;
    }

    public void record(Long localUserId, String accessToken, boolean rememberMe, HttpServletRequest request) {
        if (localUserId == null || !StringUtils.hasText(accessToken)) {
            return;
        }
        try {
            Claims claims = jwtTokenProvider.parseToken(accessToken);
            String jti = claims.getId();
            if (!StringUtils.hasText(jti)) {
                return;
            }
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            // 会话行有效期跟随 access token 过期时间即可满足列表展示（列表按 expiresAt>now 过滤）
            OffsetDateTime expiresAt = toOffset(claims.getExpiration(), now.plusMinutes(15));

            // 幂等：同一 jti 只保留一条
            if (sessionMapper.selectById(jti) != null) {
                return;
            }
            UserLoginSessionEntity session = new UserLoginSessionEntity();
            session.setId(jti);
            session.setUserId(localUserId);
            session.setUserAgent(header(request, "User-Agent"));
            session.setDeviceName(deviceName(header(request, "User-Agent")));
            session.setIpAddress(clientIpResolver.resolve(request));
            session.setRememberMe(rememberMe);
            session.setExpiresAt(expiresAt);
            session.setLastActive(now);
            session.setCreatedAt(now);
            sessionMapper.insert(session);
        } catch (RuntimeException ex) {
            log.warn("record login session failed (non-fatal): {}", ex.getMessage());
        }
    }

    /** 删除单条会话（按 jti/session id）。best-effort。 */
    public void deleteById(String jti) {
        if (StringUtils.hasText(jti)) {
            sessionMapper.deleteById(jti);
        }
    }

    /** 删除某用户的全部会话行。用于退出全部设备。 */
    public void deleteAllForUser(Long localUserId) {
        if (localUserId != null) {
            sessionMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserLoginSessionEntity>()
                    .eq(UserLoginSessionEntity::getUserId, localUserId));
        }
    }

    private OffsetDateTime toOffset(Date date, OffsetDateTime fallback) {
        return date == null ? fallback : date.toInstant().atOffset(ZoneOffset.UTC);
    }

    private String header(HttpServletRequest request, String name) {
        return request == null ? null : request.getHeader(name);
    }

    /** 从 User-Agent 粗略识别设备/浏览器，作为列表展示名。 */
    private String deviceName(String ua) {
        if (!StringUtils.hasText(ua)) {
            return "未知设备";
        }
        String os = "未知系统";
        if (ua.contains("Windows")) os = "Windows";
        else if (ua.contains("Mac OS") || ua.contains("Macintosh")) os = "macOS";
        else if (ua.contains("Android")) os = "Android";
        else if (ua.contains("iPhone") || ua.contains("iPad") || ua.contains("iOS")) os = "iOS";
        else if (ua.contains("Linux")) os = "Linux";

        String browser = "浏览器";
        if (ua.contains("Edg")) browser = "Edge";
        else if (ua.contains("Chrome")) browser = "Chrome";
        else if (ua.contains("Firefox")) browser = "Firefox";
        else if (ua.contains("Safari")) browser = "Safari";
        return os + " · " + browser;
    }
}
