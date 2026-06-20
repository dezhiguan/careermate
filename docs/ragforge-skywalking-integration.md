# RAGForge × SkyWalking 联动说明

CareerMate 与 RAGForge 共用 **同一套 SkyWalking OAP**（`deploy/skywalking/docker-compose.skywalking.yml`），在 UI **Topology** 中展示跨服务调用链。

## 1. RAGForge 后端挂载 Java Agent

在 RAGForge 后端进程（systemd 或 Docker）增加（勿将密钥写入仓库）：

```bash
-javaagent:/opt/skywalking-agent/skywalking-agent.jar
-Dskywalking.agent.service_name=ragforge-backend
-Dskywalking.collector.backend_service=skywalking-oap:11800
```

| 部署方式 | `collector.backend_service` 示例 |
|----------|----------------------------------|
| 与 OAP 同 Docker 网络 | `skywalking-oap:11800` |
| 宿主机进程 + OAP 映射 localhost | `127.0.0.1:11800` |

重启 RAGForge 后端后，在 SkyWalking UI **General Service** 中应看到 **`ragforge-backend`**。

## 2. 链路上下文透传

CareerMate 调用 RAGForge 时必须延续 trace：

1. **推荐**：两侧均挂 SkyWalking Agent，HTTP 客户端插件自动传播 `sw8` header（`java.net.http` / Spring `RestTemplate` 等）。
2. **补充**：CareerMate 已实现 `TraceHeaderPropagator`（W3C `traceparent` + `X-Request-Id` + `X-CareerMate-Session-Id`）。RAGForge 接入 Agent 后，应同时识别入站 `sw8` / `traceparent`。

业务代码应通过 **`RagForgeClient`**（`com.careermate.ragforge`）发起 HTTP，避免绕过传播逻辑。

## 3. 预期 Topology

在 SkyWalking UI → **Topology**：

```text
careermate-backend  -->  ragforge-backend
```

触发方式：CareerMate 开启 `RAGFORGE_ENABLED=true` 并执行会调用 RAG 检索的 Agent/接口。

## 4. 本仓库 V1 范围

- ✅ CareerMate Agent + `RagForgeClient` 传播头与 span（`ragforge.search` 等）
- ✅ 共用 OAP 的部署模板与文档
- ⏳ RAGForge 进程侧 Java Agent 挂载需按 RAGForge 仓库和目标环境单独执行

## 5. 验收

1. UI 中同时存在 `careermate-backend`、`ragforge-backend`
2. 发起一次 CareerMate → RAGForge 请求
3. Topology 出现跨服务边；Trace 详情含 RAG 相关 Span（`rag.search` 等由 RAGForge 侧命名）

与 W3C/OTLP 文档的关系：见 `docs/ragforge-tracing-integration.md`（OTLP/Micrometer）；生产 UI 以 **SkyWalking** 为准时可关闭 CareerMate `TRACING_ENABLED`。
