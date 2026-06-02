# CareerMate

CareerMate 是一个面向职业发展的智能助手平台，提供简历优化、面试辅导、职业规划等能力。本仓库为项目 monorepo，包含后端服务与前端应用。

## Documentation

- [AI Context](docs/AI_CONTEXT.md)
- [Database Design](docs/database-design.md)
- [Architecture Design V2.1](docs/design/CareerMate-architecture-v2.1.html)
- [Prototype Design](docs/design/CareerMate-prototype-design.html)

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Java 17、Spring Boot 3.2.x、Maven |
| 持久层 | MyBatis-Plus 3.5.x、PostgreSQL 15、Flyway |
| 前端 | Vue 3 + Vite（后续迁入） |
| 容器化 | Docker、Docker Compose |

## 当前阶段说明

**阶段三：认证与用户隔离（基础能力）**

当前已完成：

- Spring Boot 后端基础骨架
- 统一响应与异常处理
- 健康检查接口
- Vue 前端页面导入
- Flyway migration 基础
- 用户核心表 `users` / `user_profiles` / `security_audit_logs`
- 对应实体类与 MyBatis-Plus Mapper
- Spring Security 接入
- single-user / jwt 双模式
- 认证接口：`POST /api/auth/register`、`POST /api/auth/login`、`GET /api/auth/me`
- JWT 生成与校验
- `CurrentUserContext` 用户上下文注入
- 注册 / 登录审计日志写入 `security_audit_logs`

本阶段未包含：Agent、LLM、SSE、简历/岗位/面试等业务能力与相关接口。

### 数据库初始化

本地 PostgreSQL 启动后，后端启动时 Flyway 会自动执行 migration。

Migration 文件位于：

```
backend/src/main/resources/db/migration/
```

首次启动将执行 `V1__init_user_core_tables.sql`，创建用户核心表及 `flyway_schema_history`。

## 本地启动后端

### 前置条件

- JDK 17
- Maven 3.8+
- PostgreSQL 15（本地安装，或通过 Docker Compose 仅启动 postgres）

### 启动 PostgreSQL（可选，使用 Docker）

```bash
docker compose up -d postgres
```

### 配置环境变量

```bash
cp .env.example .env
# 按需修改 .env 中的数据库连接信息
```

### 认证模式配置

```bash
SECURITY_MODE=single-user|jwt
JWT_SECRET=change-me-in-dev-only-change-me-in-dev-only
JWT_EXPIRATION_MS=86400000
SINGLE_USER_ID=1
SINGLE_USER_NAME=local-user
```

### 编译并启动

```bash
cd backend
mvn spring-boot:run
```

或先打包再运行：

```bash
cd backend
mvn -DskipTests package
java -jar target/careermate-backend-0.1.0.jar
```

## Docker Compose 启动

在项目根目录执行：

```bash
docker compose up --build
```

这将启动 PostgreSQL 与后端服务。

## 健康检查

| 接口 | 说明 |
|------|------|
| `GET http://localhost:8080/api/health` | 应用健康检查（统一响应体） |
| `GET http://localhost:8080/actuator/health` | Spring Actuator 健康端点 |

## 认证接口

| 接口 | 说明 |
|------|------|
| `POST /api/auth/register` | 用户注册并返回 JWT |
| `POST /api/auth/login` | 用户登录并返回 JWT |
| `GET /api/auth/me` | 获取当前用户信息（single-user 或 JWT） |

示例响应（`/api/health`）：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "status": "UP",
    "service": "careermate-backend",
    "version": "0.1.0"
  },
  "traceId": null,
  "timestamp": 1710000000000
}
```

## 目录结构

```
careermate/
├── README.md
├── docs/
│   ├── AI_CONTEXT.md
│   ├── database-design.md
│   └── design/
│       ├── CareerMate-architecture-v2.1.html
│       └── CareerMate-prototype-design.html
├── docker-compose.yml
├── .env.example
├── backend/
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/
│       ├── java/com/careermate/
│       │   ├── CareerMateApplication.java
│       │   ├── common/          # 公共组件（响应体、异常）
│       │   ├── config/          # 配置类
│       │   ├── health/          # 健康检查
│       │   ├── mapper/          # MyBatis-Plus Mapper
│       │   └── model/entity/    # 数据库实体
│       └── resources/
│           ├── application.yml
│           ├── application-dev.yml
│           └── db/migration/    # Flyway migration 脚本
└── frontend/careermate/         # Vue 3 + Vite 前端
```

## 后续阶段计划

1. ~~**数据库迁移**：创建用户核心表，开启 Flyway migration~~（阶段二已完成）
2. ~~**认证授权**：接入 Spring Security，实现登录注册~~（阶段三基础能力已完成）
3. ~~**前端迁入**：将 Vue 3 前端迁移至 `frontend/` 目录~~（已完成）
4. **Agent Runtime**：实现智能体运行时与 Tool Registry
5. **LLM / RAG 集成**：对接大语言模型与 RAGForge 知识库
6. **业务表扩展**：简历、岗位、面试等剩余业务表
