# CareerMate 与 Keel

## 实现

CareerMate 的 Java 后端继续提供简历、岗位、任务、面试和机会等业务。`agent/` 是同一项目的 Python 运行时，使用底座 SDK 提供标准协议、模型工具循环、提示词版本、记忆、护栏、追踪、审计和评测。底座 Java starter 的模型和审计客户端目前仍为空，因此不依赖其占位实现。

```
Keel 调试 / 渠道 → CareerMate SDK → ctx.prompt / ctx.loop / ctx.llm
                                   → ctx.tools → Java 业务工具 → 数据库 / 业务检索
原有 CareerMate 前端 → Java LlmClient → SDK 私有接口 → ctx.llm → keel-llm
原有提示词入口 → SDK ctx.prompt → Langfuse 环境标签
原有工具执行入口 → SDK 同步审计确认 → 业务动作
```

接入声明 21 个现有业务工具，包含子包内的 `rag_retriever`。业务知识库的场景映射、个人简历同步、现有业务数据表与 SkyWalking 保留；不假定底座通用检索具备这些业务语义。未使用的委派和技能不为展示而声明。

`assistant` 和原有四个版本化模板由底座管理。模型生成节点关联实际模板名和版本；原生 Java 的模板证据按模板正文匹配，跨异步线程保留，最多缓存 64 份。未纳入模板注册表的动态业务提示片段仍属于 Java 业务代码。网关用量以真实账单和 Langfuse generation 为准；SDK 尚未返回到原生 Java DTO 的用量保持未知，不伪造为零。

## 认证和数据隔离

- `KEEL_BRIDGE_TOKEN` 只用于控制面调用 SDK；与底座 `KEEL_CAREERMATE_TOKEN` 一致。
- `CAREERMATE_SERVICE_TOKEN` 用于 SDK 与 Java 双向内部访问，必须与桥接凭据不同。
- agent/env 来自部署，客户端头不能改写。身份只从底座验证后的 `context.user_id` 取得，不进入工具参数；Java 按 `auth_user_id` 解析本地用户，不存在或禁用则拒绝。
- 评测流量不提供或执行个人业务工具，也不写普通用户会话记忆。
- SDK 工具按 manifest 风险审计；Java 原生工具一律在业务执行前取得同步审计确认，失败不执行业务。既有业务审计继续保留。
- 托管模式提示词不可用即失败；生产配置不启用 `KEEL_LOCAL_PROMPTS`。

## 构建与验证

`agent/vendor/` 记录小型 SDK wheel、校验和与源码证据，CI 无需跨私有仓库取包。升级 SDK 时从对应提交运行 `scripts/build-template-wheel.sh`，替换 wheel 并更新校验和与 SOURCE.json。

```bash
python -m pip install agent/vendor/keel_sdk-*.whl -r agent/requirements.lock pytest
python -m pytest agent/tests -q
scripts/build-keel-runtime.sh

# 回归库需要 pgvector，不能用不带扩展的普通 PostgreSQL。
TEST_DB_URL=jdbc:postgresql://127.0.0.1:16432/careermate_test_db mvn -f backend/pom.xml test
```

`.github/workflows/keel-runtime.yml` 校验 SDK、运行测试、构建并保存带校验和的镜像产物。`agent/evals/seed.jsonl` 包含 10 条职业咨询用例及独立评分要求，格式使用底座导入器识别的 `expectedOutput`；底座仓库的 `/evals/careermate-keel.jsonl` 是同一份首次开通种子，后续维护由项目负责。

## 部署顺序

1. 部署底座配套修复（Registrar 不再覆盖正式版本、自检使用 CareerMate 凭据、存量升级刷新语言元数据）；关闭旧 `KEEL_CAREERMATE_ENDPOINT` 自动注册。服务接口仍走原有控制台。
2. 为 `agents` 与 `careermate` 命名空间创建独立的 `careermate-service` Secret，包含相同的桥接/业务服务凭据。凭据只保存在 Secret，不写入清单。
3. 部署独立 staging 运行时和 PVC（`deploy/k8s/keel-agent/runtime.yaml`），manifest endpoint 使用 staging 的 Service；声明的部署语言必须是 python，Pod 必须有 `keel.io/agent=careermate-keel`、`keel.io/env=staging` 标签。
4. 使用底座标准注册开通。首次注册在提示词尚未同步时可以返回自检未通过；先同步提示词，再重新自检，不能把未通过当作上线。
5. 对 Java 后端先部署候选镜像，确认健康后配置 `careermate-keel-runtime` Secret（字段见下）；不要把 staging 和 prod 原生模型流量混到同一个运行时。
6. 同步所有提示词，检查 staging 标签，执行全量平台评测。生产发布只通过评测运行的 publish API，绑定完整通过证据、提示词版本和实际镜像。
7. prod 部署必须使用底座开通的 prod 凭据、正确的 Service/manifest endpoint、prod 标签和独立持久化目录。当前底座 SecretWriter 默认名称是 `keel-{agent}`，上线时必须核对 staging/prod 凭据和实际活跃环境，不能把同一份可变 Secret 同时当成两套环境凭据。环境切换和正式 endpoint 尚需线上验收确认，不能仅凭 publish API 返回成功即切换流量。原生后端最后再指向已验证的 prod SDK 运行时。
8. 在底座调试窗口验证普通咨询、多轮记忆、默认简历、任务读取和一次明确授权的任务创建并读回；核对 generation、tool、提示词版本、网关账单与白名单审计。

Java 后端 `careermate-keel-runtime` Secret 需要：

| 字段 | 配置 |
|---|---|
| CAREERMATE_KEEL_ENABLED | true |
| CAREERMATE_KEEL_URL / LLM_ENDPOINT | prod SDK Service 的根地址 |
| CAREERMATE_SERVICE_TOKEN / LLM_API_KEY | 业务服务凭据（不是厂商 Key） |
| LLM_PROVIDER / LLM_MODEL | keel / chat-default |
| AGENT_FRAMEWORK_ENABLED | false（禁止旧旁路模型直连） |
| AGENT_SECONDARY_LLM_ENABLED | false（跨模型请求也使用底座受授权的模型） |

## 控制面操作

`agent/lifecycle.py` 仅调用既有底座 API，不直接改注册数据库、门禁或生产标签。

```bash
export KEEL_CONSOLE_URL=https://keel.ragforge.net
export KEEL_CONSOLE_COOKIE_FILE=/path/to/valid-console-cookies.json
python agent/lifecycle.py register --endpoint http://careermate-keel.agents.svc.cluster.local:8000
python agent/lifecycle.py sync-prompts --git-sha "$(git rev-parse HEAD)"
python agent/lifecycle.py self-check
python agent/lifecycle.py evaluate
python agent/lifecycle.py inspect --run-id ev_RETURNED_ID
python agent/lifecycle.py publish --run-id ev_RETURNED_ID --env prod
```

回滚使用平台已有版本/提示词回滚及对应 Deployment 的镜像回滚。不要把旧最小桥接 v1 当作新 SDK 发布的回滚版本；不要通过直接 SQL 伪造门禁通过。

## 2026-10-25 线上状态

实际登记标识是 `careermate-keel`（显示名 CareerMate 职业助手）。历史 `careermate` 名称已占用，不复活已移除身份。底座调试入口为 <https://keel.ragforge.net/agents/careermate-keel/debug>，当前环境 staging、正式版本 v2。运行时两副本，Java 业务由独立 `careermate-keel-backend` 候选服务承接；原有生产主站部署未切换。

已验证提示词版本 assistant=2、其余四份=1；完整真实模型测评 10/10、得分 1.00；浏览器任务创建、读回和标记完成；模型请求与已结算费用；审计中心工具记录、字段白名单、实际哈希以及重启后保留。原生工作区型简历操作保留 CareerMate 的业务上下文要求，不把底座通用对话会话当作已有 JD 工作区。

Keel 控制台须同时读取控制面和运行时审计。`KEEL_RUNTIME_AUDIT_URL` 配置到 keel-audit，聚合分页保留两类记录。keel-audit 使用独立 `keel_audit` 数据库，`keel-audit-database` Secret 注入 JDBC URL、受限应用用户及密码，以及 `KEEL_AUDIT_MIGRATE=false`、`KEEL_AUDIT_REQUIRE_DURABLE=true`。管理员执行迁移；应用只对事件表/分区 SELECT、INSERT，链头允许 UPDATE。当前 2026-10 至 2026-12 分区已预建，运维须提前创建后续月分区；应用不自行扩大 DDL 权限。

运行时字段策略保存在既有 `keel-routing-audit-policy` Secret：careermate-keel/staging 只保留 toolName、outcome，凭据摘要绑定标准开通的审计 token。策略更新不得覆盖其他智能体。旧线上审计服务曾使用内存，首次更新前未完成存储核对，旧运行时审计无法完整恢复；原有控制面持久化记录仍保留，并从 CareerMate SDK 缓冲恢复了 8 条有原始凭据的历史事件。详见验收记录，不将新持久化追认为历史证据。
