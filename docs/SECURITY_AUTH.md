# CareerMate 认证与权限说明

本文按当前代码实现描述 CareerMate 的认证、权限、用户隔离和审计边界。

## 1. 认证架构

```text
Browser
  |  username/password, mobile code
  v
CareerMate Backend
  |  client_assertion
  v
Auth Gateway
  |  access_token (JWT) + refresh_token
  v
CareerMate API
```

CareerMate 后端不再本地签发旧版 JWT。登录、短信、密码重置通过 `AuthGatewayClient` 调用 Auth Gateway，拿到 access token 后返回给前端。后端收到业务请求时使用 `JwtTokenProvider` 拉取 Auth Gateway JWKS，校验 JWT issuer、audience、过期时间和签名。

## 2. 前端会话

- access token 存在 `localStorage`，key 为 `careermate_token`。
- 用户信息存在 `localStorage`，key 为 `careermate_user`。
- API 请求统一在 `src/api/http.js` 注入 `Authorization: Bearer <token>`。
- 收到 401 或业务 code 401 时清理本地会话并跳转 `#/login`。
- refresh token 由后端通过 `AuthGatewayCookieSupport` 写入 HttpOnly cookie，cookie 名默认 `cm_refresh`。

## 3. 匿名接口

`SecurityConfig` 与 `JwtAuthenticationFilter` 只放行以下接口：

| 方法 | 路径 | 用途 |
|------|------|------|
| GET | `/api/health` | 健康检查 |
| POST | `/api/auth/register` | 注册 |
| POST | `/api/auth/login` | 账号密码登录 |
| POST | `/api/auth/sms/send` | 手机验证码发送 |
| POST | `/api/auth/mobile/login` | 手机号验证码登录 |
| POST | `/api/auth/password-reset/sms/send` | 密码重置验证码 |
| POST | `/api/auth/password-reset/confirm` | 确认重置密码 |
| POST | `/api/v1/events/**`、`/api/events/**` | Auth Gateway 事件 webhook |
| GET | `/actuator/health` | Actuator 健康检查 |

其它 `/api/**` 必须带 Bearer token。

## 4. 用户与角色字段

本地 `users` 表保存 CareerMate 业务用户：

- `id`：CareerMate 内部用户 ID，业务表统一引用它。
- `auth_user_id`：Auth Gateway 用户 ID，JWT `user_id` 会映射到该字段。
- `role`：CareerMate 本地角色，默认 `USER`。
- `platform_role`：统一认证平台角色，来自阶段性迁移字段。
- `session_version`：用于会话版本/吊销扩展。
- `status`：只有 `ACTIVE` 用户可通过 JWT 认证。

当前接口级权限主要是“已登录用户”级别，业务授权依靠 `user_id` 数据隔离和服务层校验；后续如需要 ADMIN 能力，可在 Spring Security authority 或业务 service 层增加显式 role 检查。

## 5. Auth Gateway 事件吊销

后端接收两类 webhook：

- `POST /api/v1/events/session-revoked`
- `POST /api/v1/events/password-changed`

事件由 `AuthEventService` 处理：

- 使用 `AUTH_EVENT_HMAC_SECRET` 校验 HMAC-SHA256 签名。
- 使用 Redis 记录事件 ID，保证幂等。
- 将 revoked jti 写入 Redis。
- 密码变更事件会写入用户级 `revoked_after`，使该时间点之前签发的 token 失效。

如果 Redis 不可用，事件接口返回不可用；JWT 过滤器在吊销存储短暂不可用时会继续做签名/issuer/audience/exp 校验。

## 6. 短信登录与密码重置

手机号登录：

- `POST /api/auth/sms/send`
- `POST /api/auth/mobile/login`

密码重置：

- `POST /api/auth/password-reset/sms/send`
- `POST /api/auth/password-reset/confirm`

开发环境可用内存存储和 mock provider；生产应启用 Redis 与真实短信 provider。限流相关逻辑在 `SmsAuthRateLimiter`，包含手机号、IP、手机号+IP 维度。

## 7. RAGForge Token Exchange

`RagForgeClient` 调用 RAGForge 时会读取当前请求的 Bearer token，并通过 Auth Gateway `oauth/token-exchange` 换取目标 audience 的 token：

- `RAGFORGE_REQUESTED_AUDIENCE` 默认 `ragforge-api`
- `RAGFORGE_REQUESTED_SCOPES` 默认 `rag:search`
- 交换后的 token 有短 TTL 缓存，默认 300 秒

这样 CareerMate 不直接复用面向自身 audience 的 token 调用 RAGForge。

## 8. Agent 工具权限与风险

Agent 工具定义包含：

- `AgentToolPermission`：`READ_USER_DATA`、`WRITE_USER_DATA`、`CALL_EXTERNAL_SERVICE`、`LONG_RUNNING_TASK`
- `AgentToolRiskLevel`：`LOW`、`MEDIUM`、`HIGH`

这些字段用于工具注册、前端工具卡片和后续审批策略。当前高风险写入主要通过 pending action / 工具卡片承接，业务服务仍以当前用户上下文和资源归属校验为准。

## 9. 审计

`security_audit_logs` 保存敏感操作摘要。当前已覆盖注册、登录、个人资料更新、短信/密码重置等安全动作。审计日志只记录摘要，不记录密码、验证码、token、完整 prompt、完整简历或完整 JD。

## 10. 关键环境变量

| 变量 | 说明 |
|------|------|
| `AUTH_GATEWAY_BASE_URL` | Auth Gateway 地址 |
| `AUTH_GATEWAY_ISSUER` | JWT issuer |
| `AUTH_GATEWAY_AUDIENCE` | CareerMate API audience |
| `AUTH_GATEWAY_TOKEN_ENDPOINT_AUDIENCE` | client assertion aud |
| `AUTH_GATEWAY_CLIENT_ID` | CareerMate 后端 client ID |
| `AUTH_GATEWAY_CLIENT_ASSERTION_PRIVATE_KEY` | RSA 私钥路径 |
| `AUTH_GATEWAY_CLIENT_ASSERTION_KID` | RSA key id |
| `AUTH_GATEWAY_REFRESH_COOKIE_NAME` | refresh cookie 名 |
| `AUTH_GATEWAY_REFRESH_COOKIE_DOMAIN` | refresh cookie domain |
| `AUTH_GATEWAY_REFRESH_COOKIE_PATH` | refresh cookie path |
| `AUTH_GATEWAY_REFRESH_COOKIE_SECURE` | 是否 secure cookie |
| `AUTH_EVENT_HMAC_SECRET` | auth event webhook HMAC 密钥 |
| `TRUST_PROXY_HEADERS` | 是否信任网关清洗后的 IP 头 |
