# CareerMate Database Design

## 1. Overview

CareerMate 使用 PostgreSQL 15 作为权威持久化数据库，JSONB 用于保存用户画像、偏好、结构化分析结果等灵活字段。所有数据库结构变更通过 Flyway migration 管理。

当前文档只记录已进入实现阶段的数据表，不一次性展开完整 19 张表。后续每完成一个阶段，再追加对应表设计。

**当前阶段范围：**

- 用户基础身份
- 用户画像
- 安全审计日志

**当前阶段状态：** 阶段二已完成；`V1__init_user_core_tables.sql` 已落地，对应 Entity / Mapper 已创建，尚未实现登录注册与审计写入逻辑。

## 2. Migration Policy

- 所有 DDL 通过 Flyway 管理。
- Migration 文件目录：

  ```text
  backend/src/main/resources/db/migration/
  ```

- Migration 文件命名：

  ```text
  V{version}__{description}.sql
  ```

- 已应用的 migration 文件**禁止修改**。
- 后续表结构变更必须**新增** migration 文件。
- 本阶段 migration：

  ```text
  V1__init_user_core_tables.sql
  ```

## 3. Naming Conventions

- 表名使用 snake_case 复数或业务名词。
- 字段名使用 snake_case。
- 主键统一使用 `id BIGSERIAL`。
- 用户私有数据必须包含 `user_id`。
- 时间字段统一使用 `created_at` / `updated_at`。
- 时间类型优先使用 `TIMESTAMPTZ`。
- JSON 字段优先使用 `JSONB`。
- 索引命名格式：
  - `idx_{table}_{column}`
  - `idx_{table}_{column1}_{column2}`
- 唯一约束命名格式：
  - `uk_{table}_{column}`

## 4. Tables

当前阶段仅包含以下三张表。

### 4.1 users

**说明**

`users` 表保存 CareerMate 用户基础身份信息。当前阶段只建表，不实现登录注册。密码字段保存 BCrypt 或 Argon2 哈希，**不允许**保存明文密码。

**表名：** `users`

| Column | Type | Nullable | Default | Description |
|--------|------|----------|---------|-------------|
| id | BIGSERIAL | NO | - | Primary key |
| username | VARCHAR(64) | NO | - | 用户名，唯一 |
| password_hash | VARCHAR(255) | NO | - | 密码哈希，BCrypt 或 Argon2 |
| email | VARCHAR(128) | YES | NULL | 邮箱，唯一但允许为空 |
| role | VARCHAR(32) | NO | `'USER'` | 用户角色，USER / ADMIN |
| status | VARCHAR(32) | NO | `'ACTIVE'` | 用户状态，ACTIVE / DISABLED / DELETED |
| created_at | TIMESTAMPTZ | NO | NOW() | 创建时间 |
| updated_at | TIMESTAMPTZ | NO | NOW() | 更新时间 |

**约束**

- `username` 唯一（`uk_users_username`）。
- `email` 唯一，但允许 NULL（`uk_users_email`）。
- 当前阶段不加 `role` / `status` check constraint，后续由代码控制枚举值。

**索引**

| Index | Columns | Description |
|-------|---------|-------------|
| uk_users_username | username | 用户名唯一约束 |
| uk_users_email | email | 邮箱唯一约束 |
| idx_users_username | username | 用户名查询索引 |
| idx_users_email | email | 邮箱查询索引 |
| idx_users_status | status | 按状态筛选用户 |

### 4.2 user_profiles

**说明**

`user_profiles` 表保存用户画像，是 Agent Memory 中 Profile Memory 的主要持久化表。它保存结构化技能、工作年限、目标岗位、目标公司和求职偏好。

**不要**在该表中保存完整简历原文。完整简历解析结果后续保存在 `resumes` 表。

**表名：** `user_profiles`

| Column | Type | Nullable | Default | Description |
|--------|------|----------|---------|-------------|
| id | BIGSERIAL | NO | - | Primary key |
| user_id | BIGINT | NO | - | 用户 ID，关联 `users.id` |
| skills | JSONB | NO | `'[]'::jsonb` | 技能标签列表 |
| experience_years | NUMERIC(4,1) | YES | NULL | 工作年限 |
| target_positions | JSONB | NO | `'[]'::jsonb` | 目标岗位列表 |
| target_companies | JSONB | NO | `'[]'::jsonb` | 目标公司列表 |
| preferences | JSONB | NO | `'{}'::jsonb` | 求职偏好，如城市、薪资、行业 |
| created_at | TIMESTAMPTZ | NO | NOW() | 创建时间 |
| updated_at | TIMESTAMPTZ | NO | NOW() | 更新时间 |

**约束**

- `user_id` 唯一（`uk_user_profiles_user_id`）。
- `user_id` 外键关联 `users(id)`，`ON DELETE CASCADE`。

**索引**

| Index | Columns | Description |
|-------|---------|-------------|
| uk_user_profiles_user_id | user_id | 一个用户对应一份画像 |
| idx_user_profiles_user_id | user_id | 按用户 ID 查询画像 |

**设计说明**

- `skills` 使用 JSONB 数组，例如：

  ```json
  ["Java", "Spring Boot", "PostgreSQL"]
  ```

- `preferences` 使用 JSONB 对象，例如：

  ```json
  {"city":["北京","上海"],"salary":"25-40K","jobType":"backend"}
  ```

- `user_profiles` 是长期记忆，不保存对话过程。
- 用户可以查看、编辑、重置该画像。

### 4.3 security_audit_logs

**说明**

`security_audit_logs` 表保存敏感操作审计记录。当前阶段只建表，后续认证、简历上传、记忆清空、会话删除等操作会写入审计日志。

**表名：** `security_audit_logs`

| Column | Type | Nullable | Default | Description |
|--------|------|----------|---------|-------------|
| id | BIGSERIAL | NO | - | Primary key |
| user_id | BIGINT | YES | NULL | 操作用户 ID，用户删除后保留日志 |
| action_type | VARCHAR(64) | NO | - | 操作类型 |
| action_detail | TEXT | YES | NULL | 操作摘要，不保存敏感原文 |
| resource_type | VARCHAR(64) | YES | NULL | 资源类型，如 RESUME / SESSION / PROFILE |
| resource_id | VARCHAR(64) | YES | NULL | 资源 ID |
| success | BOOLEAN | NO | TRUE | 操作是否成功 |
| failure_reason | TEXT | YES | NULL | 失败原因 |
| ip_address | VARCHAR(64) | YES | NULL | 请求 IP |
| user_agent | VARCHAR(512) | YES | NULL | User-Agent |
| created_at | TIMESTAMPTZ | NO | NOW() | 创建时间 |

**约束**

- `user_id` 外键关联 `users(id)`，`ON DELETE SET NULL`。

**索引**

| Index | Columns | Description |
|-------|---------|-------------|
| idx_security_audit_logs_user_id | user_id | 查询用户审计日志 |
| idx_security_audit_logs_action_type | action_type | 按操作类型查询 |
| idx_security_audit_logs_created_at | created_at | 按时间查询 |
| idx_security_audit_logs_resource | resource_type, resource_id | 按资源查询 |

**action_type 规划值（REGISTER / LOGIN 已进入实现阶段）**

- LOGIN
- LOGOUT
- REGISTER
- UPLOAD_RESUME
- DELETE_RESUME
- CLEAR_MEMORY
- EXPORT_PROFILE
- DELETE_SESSION

## 5. Data Isolation Rules

- 所有用户私有业务表必须包含 `user_id`。
- 后续所有查询、更新、删除必须带 `user_id` 条件。
- 前端**不能**传 `userId`。
- LLM 工具参数**不能**传 `userId`。
- `userId` 后续从 `CurrentUser` / `ToolContext` 注入。
- CareerMate 负责终端用户权限，RAGForge 不感知终端用户。

## 6. Privacy Rules

- 不保存明文密码。
- 不在日志中打印密码、token、简历原文。
- `user_profiles` 不保存完整简历原文。
- `security_audit_logs.action_detail` 只保存摘要，不保存敏感原文。
- 后续 `resumes.parsed_content` 可配置加密存储。
- Trace 和评测样本默认不保存简历原文，只保存摘要或脱敏字段。

## 7. Next Tables To Be Added

下一阶段之后将逐步追加（**仅列名，不展开字段**）：

- agent_sessions
- agent_messages
- agent_tool_calls
- agent_task_states
- resumes
- job_posts
- job_matches
- interview_sessions
- interview_questions
- interview_answers
- career_dashboard_snapshots
- prompt_templates
- prompt_versions
- agent_eval_cases
- agent_eval_runs
- agent_eval_results
