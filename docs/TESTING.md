# CareerMate 测试说明

阶段收口后的验证方式。业务迭代后应跑通以下命令再提交。

## 后端单元 / 集成测试

```bash
cd backend
mvn test
```

- 使用 `application-test.yml`，默认 `management.tracing.enabled=false`
- 需要本地 PostgreSQL 库 `careermate_test_db`（见 `application-test.yml` 注释）

## 前端生产构建

```bash
cd frontend/careermate
npm run build
```

生产构建示例：

```bash
VITE_API_BASE_URL=/careermate-api VITE_BASE_PATH=/careermate/ npm run build
```

## Playwright E2E

**约定**：页面级功能完成后，应自动跑相关 Playwright 用例（本地服务可用时）。

### 前置

- 后端可访问（本地默认 `http://localhost:8080` 或 `19080`）
- 前端 dev：`npm run dev`（`5173`），或由 Playwright `webServer` 自动启动

### 环境变量

| 变量 | 说明 |
|------|------|
| `E2E_TARGET` | `local`（默认）或 `cloud` |
| `PLAYWRIGHT_API_BASE_URL` | 如 `http://localhost:19080/api` |
| `VITE_API_PROXY_TARGET` | Vite 代理到后端 |

### 本地常用命令

```bash
cd frontend/careermate

# Agent 连续 8 轮 + 任务链路 + 会话恢复
PLAYWRIGHT_API_BASE_URL=http://localhost:19080/api \
  VITE_API_PROXY_TARGET=http://localhost:19080 \
  npx playwright test tests/e2e/agent-llm-regression.spec.js --project=local-chrome-desktop

# 追踪响应头 + 单轮对话
PLAYWRIGHT_API_BASE_URL=http://localhost:19080/api \
  VITE_API_PROXY_TARGET=http://localhost:19080 \
  npx playwright test tests/e2e/agent-tracing.spec.js --project=local-chrome-desktop

# Agent 工具卡片 UI
npm run test:e2e -- tests/e2e/agent-tool-ui.spec.js --project=local-chrome-desktop --workers=1

# 任务工具 API/流程
npx playwright test tests/e2e/agent-task-tools.spec.js --project=local-chrome-desktop

# 登录注册（jwt 环境）
npm run test:e2e:auth:local

# 多轮上下文 / 画像 / 工具 API 等
npx playwright test tests/e2e/agent-conversation-context.spec.js
npx playwright test tests/e2e/agent-career-profile.spec.js
npx playwright test tests/e2e/agent-tools.spec.js
```

### 云端

```bash
E2E_TARGET=cloud \
  PLAYWRIGHT_API_BASE_URL=http://8.163.63.222/careermate-api \
  npx playwright test tests/e2e/cloud-user-journey.spec.js --project=local-chrome-desktop --workers=1
```

云端完整回归依赖服务器已部署**最新**后端 JAR 与正确 `LLM_PROVIDER`（见 `docs/deployment-careermate.md`）。

### 用例与主题对照

|  spec 文件 | 覆盖主题 |
|------------|----------|
| `agent-llm-regression.spec.js` | mock 8 轮不卡死、任务工具、会话恢复 |
| `agent-tracing.spec.js` | 健康检查头、单轮 Agent |
| `agent-tool-ui.spec.js` | 工具卡片展示与跳转 |
| `agent-task-tools.spec.js` | 任务创建/完成 |
| `agent-chat-stress.spec.js` | 压力 / 流式 |
| `auth.spec.js` | 登录注册 |
| `careermate.spec.js` | 主流程冒烟 |

### 服务不可用

若本地未启动 PostgreSQL / 后端，请先：

```bash
cd backend && mvn spring-boot:run
# 或
SERVER_PORT=19080 SPRING_PROFILES_ACTIVE=dev java -jar target/careermate-backend-0.1.0.jar
```

再执行上述 Playwright 命令；否则在 PR/收口报告中说明原因并保留命令供后续执行。

## 手工冒烟

```bash
curl -i http://localhost:8080/api/health
# 预期：X-Request-Id、X-Trace-Id（需使用含追踪 Filter 的构建）
```
