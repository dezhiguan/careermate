# CareerMate Database Design

本文是当前数据库概要。精确 DDL 以 `backend/src/main/resources/db/migration/` 为准；已应用的 Flyway migration 禁止修改，后续结构变化必须新增 migration。

## 1. 数据库原则

- PostgreSQL 15 是 CareerMate 权威业务库。
- 所有 schema 变更由 Flyway 管理。
- 主键统一使用自增 `BIGSERIAL` / `BIGINT`。
- 用户私有数据必须包含 `user_id`，或通过父表归属校验间接隔离。
- 时间字段统一使用 `created_at` / `updated_at`，优先 `TIMESTAMPTZ`。
- 灵活结构使用 `JSONB`。
- 不在数据库保存明文密码、验证码、token、LLM API Key。
- 审计和 Trace 只保存摘要，不保存完整 prompt、简历、JD 或模型回复。

## 2. Migration 现状

当前 migration 到 V27：

| 版本 | 主题 |
|------|------|
| V1 | 用户、用户画像、安全审计 |
| V2 | Agent 会话、消息、工具调用、任务状态 |
| V3-V5 | 简历表与默认简历唯一约束 |
| V6 | 面试练习表 |
| V7 | 求职画像 `career_profiles` |
| V8 | 求职任务 `career_tasks` |
| V9-V12 | RAG 文档 ID、Agent session/message 元数据 |
| V13-V14 | 简历版本、用户展示名和头像 |
| V15 | 移除 legacy local user |
| V16 | 手机号字段 |
| V17-V18 | Agent workspace 元数据、artifact |
| V19-V20 | Agent memory 扩展、pending actions |
| V21-V25 | 简历版本目标元数据、命名和唯一序列 |
| V26 | 统一认证用户字段：`platform_role`、`session_version` |
| V27 | Auth Gateway 用户映射：`auth_user_id` |

## 3. 核心表

### 3.1 users

CareerMate 本地用户表。`id` 是本系统业务用户 ID；`auth_user_id` 映射 Auth Gateway 用户 ID。

| 字段 | 说明 |
|------|------|
| `id` | CareerMate 用户 ID |
| `username` / `email` / `phone` | 账号标识 |
| `password_hash` | 历史/兼容字段，不保存明文 |
| `display_name` / `avatar_url` | 前端展示资料 |
| `auth_user_id` | Auth Gateway 用户 ID，唯一且允许为空 |
| `phone_verified` / `phone_verified_at` | 手机验证状态 |
| `role` | 本地业务角色，默认 `USER` |
| `platform_role` | 统一认证平台角色 |
| `session_version` | 会话版本/吊销扩展 |
| `status` | `ACTIVE` 用户才可认证通过 |

### 3.2 user_profiles

用户基础画像，包含技能、经验、目标岗位、目标公司和偏好。该表不保存完整简历原文。

### 3.3 security_audit_logs

安全审计日志，记录注册、登录、短信、密码重置、资料更新等敏感操作摘要。

### 3.4 agent_sessions

Agent 会话主表。`session_id` 是对外暴露 ID，`id` 是内部主键。保存状态、标题、意图、模型、耗时、工具调用数和 workspace metadata。

### 3.5 agent_messages

会话消息表，按 session + sequence 保存 user / agent / system / tool 消息。后续上下文从这里读取。

### 3.6 agent_tool_calls

Agent Trace 与工具调用记录，保存工具名、层级、请求摘要、响应摘要、耗时、RAG 耗时、降级标记和错误码。

### 3.7 agent_task_states

Agent 运行状态表，用于任务状态、超时和恢复。

### 3.8 resumes

用户简历表，保存解析后的文本内容、默认简历标记、source type，以及 RAGForge 同步字段 `rag_doc_id`。上传 PDF/Word/Markdown 后由 Tika 解析为文本再入库。

### 3.9 resume_versions

针对岗位生成的简历版本表，支持目标 JD 元数据、版本命名、变更摘要、PDF/DOCX 导出。

### 3.10 job_posts / job_matches

岗位与匹配结果。匹配结果保存 LLM 结构化输出：得分、匹配技能、缺口、优势、风险等。

### 3.11 interview_sessions / interview_questions

面试练习会话与题目/回答记录。题目生成和回答评估可结合 LLM 与 Interview KB。

### 3.12 career_profiles

求职长期画像，服务 Agent memory，保存目标岗位、技能、经验和偏好等结构化信息。

### 3.13 career_tasks

求职任务清单，供 Dashboard 与 Agent 工具读写。

### 3.14 agent_artifacts

Agent 产物表，保存可复用的结构化产物摘要。

### 3.15 agent_pending_actions

高风险或需要用户确认的 pending action，服务 workspace action 确认流程。

## 4. 用户隔离

所有列表、详情、更新、删除都应以当前登录用户为边界：

```java
Long userId = CurrentUserContext.getUserId();
```

不要信任前端传入的 `userId`。跨表操作时先校验父资源归属，再操作子资源。

## 5. RAGForge 数据边界

CareerMate 数据库只保存业务元数据和 `rag_doc_id`，不保存向量和 RAG 索引。知识库、文档切分、向量检索由 RAGForge 管理。

| 数据 | CareerMate | RAGForge |
|------|------------|----------|
| 简历原文/解析文本 | `resumes.content` | Personal KB 文档 |
| JD 文本/岗位知识 | 可缓存业务字段 | JD KB |
| 面试参考知识 | 不做权威存储 | Interview KB |
| 向量索引 | 不存 | 存 |

## 6. 后续变更规则

1. 新字段/新表新增 Flyway migration。
2. Entity、Mapper、Service、测试同步更新。
3. 涉及用户私有数据必须补用户隔离测试。
4. 涉及认证字段必须同步更新 `docs/SECURITY_AUTH.md`。
5. 涉及部署变量必须同步 `.env.example` 和 `deploy/env/*.example`。
