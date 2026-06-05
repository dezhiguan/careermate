# CareerMate 部署说明（索引）

详细运维步骤见 **[deployment-careermate.md](deployment-careermate.md)**。三层架构迁移见 RAGForge 仓库 `docs/deployment-migration-runbook.md`。SkyWalking 见 **[skywalking-cloud-setup.md](skywalking-cloud-setup.md)**。

## 三层架构（生产）

| 层级 | 公网 IP | 内网 IP | 组件 |
|------|---------|---------|------|
| Server 1 数据层 | 8.163.30.216 | 172.25.90.183 | PostgreSQL、ES、Redis、RocketMQ |
| Server 2 入口层 | 8.163.63.222 | 172.19.40.32 | Nginx、RAGForge 前端、CareerMate 前端 |
| Server 3 应用层 | 8.138.191.228 | 172.25.90.184 | CareerMate backend、RAGForge backend、爬虫 |

请求链路：

```text
用户 → Server 2 Nginx
  /careermate/      → CareerMate 静态资源
  /careermate-api/  → Server 3 :18080/api/
```

## 本地部署

### 依赖

- JDK 17、Maven 3.8+
- PostgreSQL 15
- Node 18+（前端）

### 配置

```bash
cp .env.example .env
# 编辑 DB_URL、SECURITY_MODE、LLM_* 等（勿提交真实 Key）
```

Spring 实际读取：`DB_URL`、`DB_USERNAME`、`DB_PASSWORD`（文档中偶称 DATABASE_*，含义相同）。

### 启动

```bash
docker compose up -d postgres   # 可选

cd backend && mvn spring-boot:run

cd frontend/careermate && npm run dev
```

- 后端：`http://localhost:8080`
- 前端：`http://localhost:5173`

## 云端部署（概要）

| 项 | 约定 |
|----|------|
| 入口 | `8.163.63.222`（Server 2） |
| 后端 | `8.138.191.228`（Server 3），端口 `18080` |
| 前端路径 | `/careermate/` |
| API 路径 | `/careermate-api/` → 反代到 `172.25.90.184:18080/api/` |
| SkyWalking UI | `/skywalking/` → `127.0.0.1:18088`（勿公网裸露 8088） |
| 密钥 | 仅 `/opt/careermate/backend/.env.app`（Server 3），**不入库** |

构建：

```bash
cd backend && mvn -DskipTests package
cd frontend/careermate && VITE_API_BASE_URL=/careermate-api VITE_BASE_PATH=/careermate/ npm run build
```

Nginx 片段：`deploy/nginx/careermate.locations.example`、`deploy/nginx/skywalking.locations.example`

## 环境变量（占位符）

| 变量 | 说明 |
|------|------|
| `SECURITY_MODE` | `single-user` \| `jwt` |
| `SERVER_PORT` | 本地 `8080`，生产 `18080` |
| `DB_URL` | `jdbc:postgresql://172.25.90.183:5432/careermate_db` |
| `DB_USERNAME` / `DB_PASSWORD` | 数据库账号 |
| `JWT_SECRET` | jwt 模式密钥（生产必换） |
| `LLM_PROVIDER` | `mock` \| `qwen` \| `deepseek` \| `openai-compatible` |
| `LLM_MODEL` | 如 `qwen-plus`、`mock-chat` |
| `LLM_ENDPOINT` | Qwen: DashScope 兼容 endpoint |
| `LLM_API_KEY` | **仅服务器本地**，勿写入仓库 |
| `RAGFORGE_URL` | Server 3 上启用集成时用 `http://127.0.0.1:8080` |
| `TRACING_ENABLED` | Micrometer OTLP；生产用 SkyWalking 时建议 `false` |
| `SKYWALKING_AGENT_SERVICE_NAME` | 默认 `careermate-backend` |
| `SKYWALKING_COLLECTOR_BACKEND_SERVICE` | 如 `127.0.0.1:11800` 或 `skywalking-oap:11800` |
| `JAVA_TOOL_OPTIONS` | `-Xms512m -Xmx2g -javaagent:.../skywalking-agent.jar` 等 |

模板：`deploy/env/careermate-backend.env.example`、根目录 `.env.example`

## 验证

```bash
# Server 3 本机
curl -fsS http://127.0.0.1:18080/api/health

# 公网入口
curl -fsS http://8.163.63.222/careermate-api/health
curl -I http://8.163.63.222/skywalking/    # 需已部署 OAP/UI + Nginx
```

## 相关文档

- [deployment-careermate.md](deployment-careermate.md) — 目录、CI/CD、排障
- RAGForge 三层部署：`rag-forge/docs/deployment-three-tier.md`
- 统一迁移 Runbook：`rag-forge/docs/deployment-migration-runbook.md`
- [skywalking-cloud-setup.md](skywalking-cloud-setup.md)
- [ragforge-skywalking-integration.md](ragforge-skywalking-integration.md)
