# RAGForge 分布式追踪对接说明

CareerMate 已通过 Micrometer Tracing + OpenTelemetry OTLP 输出 trace，并在调用 RAGForge 时透传 W3C Trace Context。若 RAGForge 与 CareerMate 分仓部署，请在 RAGForge 侧按本文完成对齐。

## 1. 依赖（Spring Boot 3.5+）

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>
```

## 2. 配置示例

```yaml
management:
  tracing:
    enabled: ${TRACING_ENABLED:true}
    sampling:
      probability: ${TRACING_SAMPLING_PROBABILITY:1.0}
  otlp:
    tracing:
      endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4318/v1/traces}

logging:
  config: classpath:logback-spring.xml
```

日志 pattern 需包含：`traceId`、`spanId`（由 OTel bridge 写入 MDC）。

## 3. 入站 Header 约定

| Header | 说明 |
|--------|------|
| `traceparent` | W3C Trace Context，必须延续父 trace |
| `tracestate` | W3C 可选状态 |
| `X-Request-Id` | CareerMate 请求 ID，回写响应 |
| `X-CareerMate-Session-Id` | Agent 会话 ID（可选） |

## 4. 出站 / 响应 Header

- `X-Trace-Id`：当前 traceId
- `X-Request-Id`：与入站一致或新生成

## 5. RAG 检索 Span 命名

建议在检索主流程增加：

| Span | 说明 |
|------|------|
| `rag.search` | 检索 API 总耗时 |
| `rag.embedding` | 向量化 |
| `rag.vector_search` | 向量检索 |
| `rag.keyword_search` | 关键词检索 |
| `rag.rerank` | 重排 |

Tag 示例（禁止写入完整 query 原文、API Key）：

- `rag.kb_id`
- `rag.top_k`
- `rag.search_type`
- `rag.latency_ms`
- `rag.result_count`

## 6. CareerMate 侧已实现

- `TraceHeaderPropagator`：注入 `traceparent` / `tracestate` / `X-Request-Id` / `X-CareerMate-Session-Id`
- `RagForgeClient`：在 `ragforge.search` / `ragforge.upload_document` / `ragforge.delete_document` 上报 span

下一阶段 RAGForge 业务代码应**仅通过** `RagForgeClient`（或复用 `TraceHeaderPropagator`）发起 HTTP，勿绕过传播逻辑。

## 7. 联调验证

1. CareerMate 发起带 `traceparent` 的检索请求。
2. RAGForge 日志中 `traceId` 与 CareerMate 一致。
3. OTLP Collector / Jaeger 中可见跨服务父子 span。
