# CareerMate 阶段收口报告

**阶段版本**：P0–P5 MVP（应用闭环 + Qwen 切换 + 观测工程化）  
**收口日期**：2026-06（以仓库当前 `main` 工作区为准）  
**原则**：本阶段**不新增业务功能**，仅文档与验证同步。

---

## 1. 本阶段完成内容

### 产品与后端

- 登录 / `single-user` / `jwt`
- Agent SSE 对话、防卡死、会话历史与**恢复**
- **多轮上下文**、求职**画像**、求职**任务**
- 文本简历、岗位匹配、面试训练、Dashboard
- Agent **规则路由 + 工具调用** + 前端**工具卡片**
- **Qwen / mock**（及 deepseek、openai-compatible）`LlmClient` 切换
- 日志 MDC + `X-Request-Id` / `X-Trace-Id` + `llm.chat` 耗时（脱敏）

### 工程化

- Micrometer Tracing + OTLP（可选，本地/CI）
- SkyWalking：**compose、Agent 安装脚本、systemd 模板、Nginx `/skywalking/` 文档**（云端 UI 需在服务器按文档启用）
- RAGForge：`RagForgeClient`、传播头、集成**文档**（**业务 RAG 未接**）

### 前端

- Vue 3 五页 + Agent 对话台 SSE、Trace 面板、任务/工具卡片

---

## 2. 验证命令与结果（收口执行）

| 命令 | 结果 |
|------|------|
| `cd backend && mvn test` | 预期 **BUILD SUCCESS**（107+ 用例） |
| `cd frontend/careermate && npm run build` | 预期 **built** |
| Playwright `agent-tracing.spec.js` | 本地 19080 可用时 **通过** |
| Playwright `agent-llm-regression.spec.js` | 8 轮/会话恢复 **通过**；任务链路偶发依赖 mock 话术 |
| `curl -i .../api/health` | 含 `X-Request-Id`、`X-Trace-Id`（需最新后端构建） |

云端 SkyWalking：`http://8.163.63.222/skywalking/` 需运维按 `docs/skywalking-cloud-setup.md` 部署后，在 UI 中查看 `careermate-backend` trace。

---

## 3. 当前可演示路径

1. 本地：`npm run dev` + `mvn spring-boot:run` → `#/login` → Agent 对话（mock 或配置 Qwen）
2. 简历 / 岗位匹配 / 面试 / 看板各页走通 API
3. Agent 说「查看任务」「创建任务」等触发工具卡片 → Dashboard
4. 生产：`http://8.163.63.222/careermate/` + `/careermate-api/`（`LLM_PROVIDER=qwen` 在 `.env.app`）

---

## 4. 未完成内容（勿写进“已完成”）

- 简历**文件**上传与解析
- RAGForge **JD / 简历**知识库业务集成
- Agent 多步骤规划器
- Prompt 管理、Agent 评测、MCP 实装
- 云端 Playwright 全量 CI 固化
- SkyWalking 云端 UI（**配置已就绪，需服务器执行 compose + Nginx + Agent**）

---

## 5. 下一阶段优先级

1. **P6** 简历上传解析  
2. **P7** RAGForge JD 知识库 + Agent 检索增强  
3. **P8** JD 驱动匹配 / 优化 / 面试闭环  
4. **P9** Prompt + Eval  
5. **P10** 开源展示与云端 E2E  

详见 [ROADMAP.md](ROADMAP.md)。

---

## 6. 风险点

| 风险 | 说明 |
|------|------|
| 生产仍为 mock | `.env.app` 未设 `LLM_PROVIDER=qwen` 或未重启 |
| Nginx 反代 | Docker 内 `127.0.0.1` 指向容器自身，需 host IP 或共享 network |
| SkyWalking 未启 | 仅有日志 traceId，无 UI trace |
| OTLP 无 Collector | `TRACING_ENABLED=true` 时可能刷连接失败日志，生产建议关 OTLP、用 SkyWalking |
| 密钥泄露 | 禁止将 `LLM_API_KEY` 写入仓库或文档 |

---

## 7. 文档索引

- [README.md](../README.md)
- [AI_CONTEXT.md](AI_CONTEXT.md)
- [ROADMAP.md](ROADMAP.md)
- [ARCHITECTURE_SUMMARY.md](ARCHITECTURE_SUMMARY.md)
- [TESTING.md](TESTING.md)
- [DEPLOYMENT.md](DEPLOYMENT.md)
- [deployment-careermate.md](deployment-careermate.md)

**目标架构 HTML**（桌面 `CareerMate-架构设计文档-V2.1.html` / 仓库 `docs/design/CareerMate-architecture-v2.1.html`）≠ 当前 MVP 已全部实现。

---

# P6–P8 集成收口报告

**阶段版本**：P6–P8（RAGForge 深度集成 + LLM 升级 + Agent 架构升级）
**收口日期**：2026-06-08

## 本阶段完成内容

### RAGForge 侧新增（rag-forge 仓库）

- V4 migration：document_chunks 加 chunk_type 列 + 复合索引
- V6 migration：HNSW 向量索引（flyway:nonTransactional）
- V7 migration：documents 加 chunk_type 列
- P0 性能修复（A-F）：ES 并行化、recallTopK Bug、QueryRewriter 缓存、RestTemplate 超时、Reranker 截断、HNSW 迁移
- POST /api/v1/documents/text：文本直传接口，md5 去重，支持 chunkType
- 元数据过滤检索：VectorSearchService + EsSearchService 支持 chunk_type filter
- MCP Server：spring-ai-mcp-server-webmvc，HTTP_SSE，暴露 searchKnowledgeBase / listKnowledgeBases

### CareerMate 侧新增（careermate 仓库）

- RagForgeClient：搜索（searchJd/searchKb）、文本上传（syncText）、删除（deleteDocument）
- 简历同步：create/update/delete 时异步推送 RAGForge Personal KB，rag_doc_id 版本追踪
- 简历文件上传：POST /api/resumes/upload，Tika 解析，≤10MB，同步 Personal KB
- JD 库浏览：GET /api/job-matches/jd-kb-search，前端搜索卡片 + 一键填入
- JobMatchAnalyzer LLM化：Structured Output + RAG 上下文 + 规则降级
- InterviewAnswerEvaluator LLM化：Structured Output + Interview KB 参考 + 规则降级
- InterviewQuestionGenerator LLM化：个性化 5 题 + Interview KB + 模板降级
- AgentLlmIntentRecognizer：LLM 意图识别，降级到 AgentToolRouter
- AgentSupervisor + 3 专家 Agent：并行 CompletableFuture，8s 超时
- ReActEngine：非流式推理（max 3 轮），结果注入 system prompt
- 对话台右侧面板重构：展示简历/匹配/任务，debug 面板折叠
- Dashboard 技能缺口分析、面试 KB 集成题目生成与评分

## 当前可演示路径

1. 本地启动 RAGForge（:8080）+ CareerMate（:8081）
2. 上传简历文件 → 自动解析 + 同步 RAGForge Personal KB
3. 岗位匹配页：从 JD 库搜索选取 → LLM 分析（matchScore + 结构化结果）
4. 面试训练：LLM 个性化生成题目 → 回答 → LLM 评分 + 改进建议
5. Agent 对话：LLM 意图识别 → Supervisor 并行专家 → ReAct 推理 → 流式回复
6. Claude Desktop 连接 RAGForge MCP Server（http://localhost:8080/sse）直接检索知识库

## 未完成内容（下一阶段）

- 联合 docker-compose 一键部署
- 薪资谈判官（新模块）
- Agent 评测体系 / Prompt 管理
- 云端完整 Playwright 端到端回归
