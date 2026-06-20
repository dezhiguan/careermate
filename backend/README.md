# CareerMate Backend

CareerMate 后端是 Spring Boot 3.5 单体服务，提供统一认证接入、Agent SSE、简历/岗位/面试/任务/市场 API、RAGForge 集成和观测埋点。

## 运行要求

- JDK 21+
- Maven 3.8+
- PostgreSQL 15
- Auth Gateway：认证主链路依赖 `AUTH_GATEWAY_BASE_URL`
- Redis：生产推荐开启，用于短信限流、验证码存储和 auth 事件吊销；dev 可退回内存存储

macOS 可先切换 JDK：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
```

## 本地启动

从仓库根目录推荐使用：

```bash
scripts/dev-start.sh
```

脚本会：

- 加载根目录 `.env`
- 执行 `mvn clean package -DskipTests`
- 使用 `SPRING_PROFILES_ACTIVE=dev`
- 默认按 `.env` 的 `SERVER_PORT` 启动；未设置时使用 `8082`
- 开启本地 SMS Mock

手动启动：

```bash
cd backend
mvn spring-boot:run
```

健康检查：

```bash
curl -i http://localhost:8080/api/health
```

## 核心配置

配置入口：

- `src/main/resources/application.yml`
- `src/main/resources/application-dev.yml`
- `src/main/resources/application-prod.yml`
- 仓库根目录 `.env`
- 生产 `/opt/careermate/backend/.env.app`

关键变量：

| 变量 | 说明 |
|------|------|
| `SERVER_PORT` | 后端端口，本地常用 `8080/8082`，生产 `18080` |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | PostgreSQL |
| `AUTH_GATEWAY_BASE_URL` | Auth Gateway 地址 |
| `AUTH_GATEWAY_ISSUER` / `AUTH_GATEWAY_AUDIENCE` | JWT issuer/audience 校验 |
| `AUTH_GATEWAY_CLIENT_ID` | CareerMate 后端客户端 ID |
| `AUTH_GATEWAY_CLIENT_ASSERTION_PRIVATE_KEY` / `AUTH_GATEWAY_CLIENT_ASSERTION_KID` | client assertion 签名配置 |
| `AUTH_EVENT_HMAC_SECRET` | Auth Gateway 事件 webhook HMAC 密钥 |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | Redis |
| `LLM_PROVIDER` / `LLM_MODEL` / `LLM_ENDPOINT` / `LLM_API_KEY` | LLM Provider |
| `RAGFORGE_ENABLED` / `RAGFORGE_URL` / `RAGFORGE_*_KB_ID` | RAGForge 集成 |
| `CAREERMATE_MCP_ENABLED` | MCP endpoint 开关，默认 false |

## 认证与权限

匿名放行：

- `GET /api/health`
- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/sms/send`
- `POST /api/auth/mobile/login`
- `POST /api/auth/password-reset/sms/send`
- `POST /api/auth/password-reset/confirm`
- `POST /api/v1/events/**`、`POST /api/events/**`
- `GET /actuator/health`

其它 `/api/**` 都需要：

```http
Authorization: Bearer <access_token>
```

JWT 由 Auth Gateway 签发，后端通过 JWKS 校验 `kid` 对应公钥，并验证 issuer、audience、expiration。业务代码通过 `CurrentUserContext` 获取当前用户，所有私有数据按 `user_id` 隔离。

详细说明见 [../docs/SECURITY_AUTH.md](../docs/SECURITY_AUTH.md)。

## 测试

```bash
mvn test
```

测试 profile 使用 `src/test/resources/application-test.yml`。涉及认证、短信、RAGForge、Agent、观测等模块的测试已经按包组织在 `src/test/java/com/careermate` 下。

## 停止本地服务

从仓库根目录执行：

```bash
scripts/dev-stop.sh
```

脚本只检查 `8080`、`8081`、`8082` 端口，并且只终止命令行包含 `careermate` 的进程。
