# SkyWalking 云端接入指南（CareerMate V1）

目标：在入口服务器部署 **SkyWalking OAP + UI**，CareerMate 后端挂载 **Java Agent**，通过浏览器查看 `careermate-backend` 的 Trace（不仅是日志里的 `traceId`）。

## 1. 架构

```text
浏览器 -> Nginx /skywalking/ -> SkyWalking UI (127.0.0.1:18088)
CareerMate 后端 (18080) --gRPC 11800--> SkyWalking OAP
日志 MDC: traceId / requestId / userId / sessionId
```

- **不要**长期将 UI 暴露在 `0.0.0.0:8088`。
- UI 仅绑定 `127.0.0.1:18088`，公网访问走 Nginx：`http://8.163.63.222/skywalking/`。
- 访问控制（Basic Auth 等）仅在服务器 Nginx 本地配置，**不要**写入仓库。

## 2. 启动 SkyWalking（Docker）

在入口服务器（与 RAGForge Nginx 同机）：

```bash
# CI 只发布 jar/前端，deploy 需单独同步到 /opt/careermate/deploy/（见 deployment-careermate.md）
bash /opt/careermate/deploy/scripts/start-skywalking.sh
# 或:
docker compose -f /opt/careermate/deploy/skywalking/docker-compose.skywalking.yml up -d
```

镜像拉取（入口机 Docker Hub 超时时，在服务器执行）：

```bash
docker pull docker.m.daocloud.io/apache/skywalking-oap-server:10.2.0
docker pull docker.m.daocloud.io/apache/skywalking-ui:10.2.0
docker pull docker.m.daocloud.io/apache/skywalking-banyandb:0.8.0
docker tag docker.m.daocloud.io/apache/skywalking-oap-server:10.2.0 apache/skywalking-oap-server:10.2.0
docker tag docker.m.daocloud.io/apache/skywalking-ui:10.2.0 apache/skywalking-ui:10.2.0
docker tag docker.m.daocloud.io/apache/skywalking-banyandb:0.8.0 apache/skywalking-banyandb:0.8.0
```

> OAP 10.2 **不支持** `SW_STORAGE=h2`，需 BanyanDB 0.8+（已写入 compose）。

验证（仅服务器本机）：

```bash
curl -I http://127.0.0.1:18088/
curl -I http://127.0.0.1:12800/healthcheck
```

Compose 文件：`deploy/skywalking/docker-compose.skywalking.yml`

## 3. Nginx 反代 `/skywalking/`

将 `deploy/nginx/skywalking.locations.example` 合并到现有 ingress `server` 块。

| 场景 | `proxy_pass` 目标 |
|------|------------------|
| Nginx 在宿主机 | `http://127.0.0.1:18088/` |
| Nginx 在 Docker（ragforge-nginx） | `http://skywalking-ui:8080/`（同一 Docker 网络 `skywalking-net`）或宿主机网关 IP |

**注意**：容器内 `127.0.0.1` 指向容器自身，不是宿主机。

```bash
nginx -t && nginx -s reload
```

公网验证：

```bash
curl -I http://8.163.63.222/skywalking/
```

## 4. 安装 SkyWalking Java Agent

```bash
sudo bash deploy/scripts/install-skywalking-agent.sh
# 默认安装到 /opt/skywalking-agent
```

Agent 版本与 OAP 10.2 配套使用 **9.3.0**（脚本可设 `SKYWALKING_AGENT_VERSION`）。

## 5. CareerMate 后端挂载 Agent

### 5.1 systemd（推荐，当前生产形态）

参考 `deploy/systemd/careermate-backend.service.example`，在 `/opt/careermate/backend/.env.app` 中可补充（无密钥）：

```bash
SKYWALKING_AGENT_SERVICE_NAME=careermate-backend
SKYWALKING_COLLECTOR_BACKEND_SERVICE=127.0.0.1:11800
JAVA_TOOL_OPTIONS=-javaagent:/opt/skywalking-agent/skywalking-agent.jar -Dskywalking.agent.service_name=${SKYWALKING_AGENT_SERVICE_NAME} -Dskywalking.collector.backend_service=${SKYWALKING_COLLECTOR_BACKEND_SERVICE}
TRACING_ENABLED=false
```

说明：

- 生产以 **SkyWalking UI** 为主时，建议 `TRACING_ENABLED=false`，避免 OTLP 无 Collector 时报错。
- 本地开发可不挂 Agent，仍可用 Micrometer + 日志 `traceId`。

重启：

```bash
sudo systemctl daemon-reload
sudo systemctl restart careermate-backend
```

### 5.2 Docker 容器

见 `deploy/docker/docker-compose.careermate.example.yml`：挂载 `/opt/skywalking-agent`，`collector` 使用 `skywalking-oap:11800`，加入 `skywalking-net`。

## 6. 在 UI 中查看 Trace

1. 浏览器打开：`http://8.163.63.222/skywalking/`
2. 菜单 **General Service** → 选择服务 **`careermate-backend`**
3. 在 CareerMate Agent 对话台发起一轮对话
4. **Trace** 中搜索端点，例如：
   - `POST:/api/agent/sessions/{sessionId}/messages/stream`
5. 展开 Trace 查看 Span（HTTP、LLM、工具等由 Agent 插件采集）

关联日志：

- 日志行含 `traceId=... requestId=... userId=... sessionId=...`
- 与 UI 中 Trace ID 一致（挂 Agent 时优先 SkyWalking TraceContext）

### 6.1 业务日志查询（OAP Logs）

`logback-spring.xml` 仅将 **`com.careermate`** 业务包日志上报 SkyWalking；框架日志（Spring、Tomcat、Hikari、MyBatis 等）仍在控制台可见，默认 `FRAMEWORK_LOG_LEVEL=WARN`，**不会**进入 SkyWalking Logs。

| 过滤维度 | 示例 |
|----------|------|
| 服务 | `careermate-backend` |
| 业务标识 | `logType=business`（推荐） |
| 级别 | `level=ERROR` / `WARN` / `INFO` |
| Logger | 包名前缀 `com.careermate` |
| 链路 | Trace 详情中的 `traceId` |

环境变量：`APP_LOG_LEVEL=INFO`（业务）、`FRAMEWORK_LOG_LEVEL=WARN`（框架）。生产 profile 需含 `prod,skywalking-log`。

RAGForge 侧见 RAGForge 仓库 `docs/skywalking-business-logs.md`。

LLM 耗时：

- 日志关键字：`llm.chat provider=... model=... latencyMs=...`
- 不含 API Key、完整 prompt、完整回复

## 7. 验收清单

| 项 | 命令 / 操作 |
|----|-------------|
| 响应头 | `curl -i http://127.0.0.1:18080/api/health` 含 `X-Request-Id`、`X-Trace-Id` |
| UI 可访问 | `http://8.163.63.222/skywalking/` 返回 200 |
| 服务可见 | UI 中出现 `careermate-backend` |
| Agent 产生 Trace | 发起 Agent 对话后 Trace 列表有新记录 |
| 业务不回退 | SSE 不卡死、mock/Qwen 正常 |

## 8. RAGForge 后续

见 `docs/ragforge-skywalking-integration.md`：RAGForge 挂同一 OAP 后，Topology 应出现 `careermate-backend -> ragforge-backend`。

## 9. 风险说明

- 临时暴露 `0.0.0.0:8088` 仅用于排障，用完即关。
- SkyWalking 默认 H2 存储适合演示/小规模；生产大数据量请改用 Elasticsearch（另配存储，不在本 V1 范围）。
