# CareerMate

**CareerMate** 是一个基于 **Java + Spring Boot + Vue** 的 **AI 求职 Agent 工作台**：对话式求职助手，串联简历、岗位匹配、面试练习、任务与看板，支持 Qwen 与本地 Mock，并预留 RAGForge / SkyWalking 观测能力。

本仓库为 monorepo（`backend/` + `frontend/careermate/`）。当前为 **阶段性 MVP（P0–P5）**，适合提交、演示与接力开发。

---

## 文档

| 文档 | 说明 |
|------|------|
| [docs/AI_CONTEXT.md](docs/AI_CONTEXT.md) | 续开发上下文（模块、API、表、边界） |
| [docs/ROADMAP.md](docs/ROADMAP.md) | 已完成阶段 vs 下一阶段 |
| [docs/ARCHITECTURE_SUMMARY.md](docs/ARCHITECTURE_SUMMARY.md) | 架构摘要 |
| [docs/TESTING.md](docs/TESTING.md) | 测试与 Playwright |
| [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) | 部署索引 |
| [docs/deployment-careermate.md](docs/deployment-careermate.md) | 云端详细部署 |
| [docs/skywalking-cloud-setup.md](docs/skywalking-cloud-setup.md) | SkyWalking OAP/UI + Java Agent |
| [docs/PHASE_CLOSEOUT.md](docs/PHASE_CLOSEOUT.md) | **本阶段收口报告** |
| [docs/design/CareerMate-architecture-v2.1.html](docs/design/CareerMate-architecture-v2.1.html) | **目标架构**（非全部已实现） |

桌面上的 **CareerMate-架构设计文档-V2.1.html** 与仓库内 V2.1 HTML 为目标架构；当前实现为核心应用闭环，**RAGForge 深度集成、简历上传解析、Agent 评测、Prompt 管理仍在 Roadmap**。

---

## 当前已完成功能

- **登录 / 认证**：`single-user` 与 `jwt`，前端登录与路由守卫
- **Agent SSE 对话**：流式 plan/token/message/done/error，防卡死与超时兜底
- **会话历史与恢复**：最近会话、切换会话、刷新后恢复消息
- **多轮上下文**：历史消息注入 prompt
- **求职画像记忆**：`career_profiles` 跨会话
- **文本简历管理**：CRUD、默认简历、Agent 上下文
- **岗位匹配**：JD 分析、匹配结果、Agent 工具
- **面试训练**：练习会话与答题
- **Dashboard**：统计、建议、任务概览
- **求职任务清单**：创建 / 列表 / 完成，与 Agent、看板联动
- **Agent 工具调用**：规则路由 + 工具执行 + **工具卡片**跳转
- **Qwen / mock LLM 切换**：`LlmClient`；生产 Qwen 仅配服务器 `.env.app`
- **链路追踪（工程化）**：日志 `traceId` / `requestId` / `userId` / `sessionId`；响应头 `X-Trace-Id`；可选 Micrometer OTLP；**SkyWalking** compose/Agent/文档（云端 UI 需按文档启用）

## 未完成 / Roadmap

- 简历**文件**上传与解析  
- **RAGForge** JD / 简历知识库**业务**集成  
- Agent **多步骤规划**  
- **Prompt** 管理  
- **Agent 评测**体系  
- **MCP** 预留实装  
- 云端**完整** Playwright 端到端回归固化  

详见 [docs/ROADMAP.md](docs/ROADMAP.md)。

---

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Java 17、Spring Boot 3.2、Maven、MyBatis-Plus、Flyway |
| 数据库 | PostgreSQL 15 |
| 前端 | Vue 3、Vite、Vue Router |
| LLM | mock / qwen / deepseek / openai-compatible |
| 观测 | Micrometer Tracing（可选 OTLP）、SkyWalking（推荐生产 UI） |

---

## 敏感信息

- **禁止**将真实 `LLM_API_KEY`、`JWT_SECRET`、数据库密码写入仓库  
- **禁止**提交 `/opt/careermate/backend/.env.app` 或本地含密钥的 `.env`  
- `.env.example` 仅含占位符；生产密钥只在服务器或本地环境变量  

---

## 本地启动

### 1. 环境

```bash
cp .env.example .env
# 配置 DB_URL、SECURITY_MODE、LLM_PROVIDER=mock 等
```

### 2. 数据库

```bash
docker compose up -d postgres   # 可选
createdb careermate_db          # 或按 .env 中库名
```

### 3. 后端

```bash
cd backend
mvn spring-boot:run
# http://localhost:8080/api/health
```

### 4. 前端

```bash
cd frontend/careermate
npm install
npm run dev
# http://localhost:5173
```

### 5. 验证

```bash
curl -i http://localhost:8080/api/health
cd backend && mvn test
cd frontend/careermate && npm run build
```

Playwright：见 [docs/TESTING.md](docs/TESTING.md)。

---

## 云端部署概要

| 项 | 值 |
|----|-----|
| 入口示例 | `8.163.63.222` |
| 后端端口 | `18080` |
| 前端 | `/careermate/` |
| API | `/careermate-api/` |
| SkyWalking UI | `/skywalking/`（反代到 `127.0.0.1:18088`） |

步骤：[docs/DEPLOYMENT.md](docs/DEPLOYMENT.md)、[docs/deployment-careermate.md](docs/deployment-careermate.md)。

---

## 目录结构

```text
careermate/
├── README.md
├── .env.example
├── docs/                    # AI_CONTEXT, ROADMAP, TESTING, DEPLOYMENT, ...
├── deploy/                  # nginx, systemd, skywalking compose, scripts
├── backend/
├── frontend/careermate/
└── docker-compose.yml
```

---

## 快速体验

1. 打开 `#/login`（single-user 可直接进入）  
2. **Agent 对话台**：发消息，看 SSE 流式回复与 Trace  
3. **简历 / 岗位 / 面试 / 看板** 各页使用对应 API  
4. 对 Agent 说「查看我的任务」「创建任务：…」查看工具卡片  

---

## License / 贡献

按团队仓库策略；提交前请阅读 [docs/PHASE_CLOSEOUT.md](docs/PHASE_CLOSEOUT.md) 并跑通 `mvn test` 与 `npm run build`。
