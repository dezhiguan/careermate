# CareerMate 架构摘要（阶段性 MVP）

简洁说明当前实现；详细目标架构见 `docs/design/CareerMate-architecture-v2.1.html`（桌面同名 HTML 为目标版，勿与 MVP 混为一谈）。

## 系统分层

```text
Browser (Vue 3 + Vite)
    |  HTTPS / Nginx
    v
CareerMate Backend (Spring Boot, :8080 local / :18080 prod)
    |-- PostgreSQL (业务数据)
    |-- LLM Provider (mock / qwen / deepseek / openai-compatible)
    |-- SkyWalking Java Agent -> OAP (可选，生产 UI 观测)
    |
    +-- [下一阶段] RAGForge (外部 RAG 服务，REST + 链路头透传)
```

| 组件 | 本阶段状态 |
|------|------------|
| Frontend | ✅ Vue 对话台、简历、匹配、面试、看板 |
| CareerMate Backend | ✅ 单体 Spring Boot |
| PostgreSQL | ✅ Flyway V1–V8 |
| LLM Provider | ✅ 抽象 + mock/Qwen 配置切换 |
| SkyWalking | ✅ Agent/compose/文档；云端 UI 需运维启用 |
| RAGForge | ⏳ 客户端与文档预留，**未**接 JD/简历知识库业务 |

## Agent 当前实现

- **单主控 Agent**：一次用户消息 → 组装 system prompt → 可选工具 → LLM 流式回复
- **规则 Router + 工具调用**：关键词/模式匹配 `AgentToolRouter`，非 LLM function-calling 全自动
- **记忆**：会话消息（多轮上下文）、求职画像（`career_profiles`）、任务（`career_tasks`）、默认简历与最近岗位匹配上下文
- **SSE 流式**：`SseEmitter` + 独立 `agent-executor`；心跳、任务超时、单 session 并发限制

不展示 Chain-of-Thought；通过 **Agent Trace**（DB + 可选 SkyWalking）观察执行过程。

## 为何暂不引入 LangChain4j / Spring AI

- MVP 需要可控、可读的 Java 代码路径，便于教学与部署排障
- 工具路由、SSE、用户隔离、Trace 已用 Spring 原生能力实现
- 避免框架版本与供应商锁定；`LlmClient` 已足够切换 Qwen/mock
- 可在后续以 **Adapter** 方式接入，不进入当前核心链路

## 为何 RAGForge 作为外部基础设施

- 向量检索、ES、知识库生命周期由 RAGForge 专责
- CareerMate 聚焦 Agent 工作台与业务表；通过 REST + `TraceHeaderPropagator` / SkyWalking 跨服务追踪
- 用户私有简历**默认不进**共享知识库（见 `docs/AI_CONTEXT.md`）

## 与 V2.1 目标架构的差距

| V2.1 目标 | 当前 MVP |
|-----------|----------|
| 完整 Tool Registry / 并行工具编排 | 规则路由 + 同步工具执行 |
| RAGForge 知识工具深度集成 | 仅 Client/配置/文档预留 |
| 简历文件解析 + Chunk 管理 | 文本简历为主 |
| Prompt 版本与 Eval 体系 | 未实现 |
| MCP 正式实现 | 未实现 |
| Redis 多实例 SSE | 未引入（可选增强） |

下一阶段按 `docs/ROADMAP.md` P6–P10 推进。
