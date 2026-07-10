package com.careermate.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrentUser {

    private Long userId;
    private String username;
    private String role;
    private boolean authenticated;
    /** 当前请求 access token 的 jti，用于单设备退出/踢出会话时精确吊销 */
    private String jti;
    /** auth-gateway 侧的用户标识（token 里的 user_id），用于"退出全部/改密踢设备"的用户级吊销键 */
    private String authUserKey;
    /** 当前 token 的签发时间（epoch 秒），用户级吊销按签发时间判定 */
    private Long issuedAtEpochSeconds;
}
