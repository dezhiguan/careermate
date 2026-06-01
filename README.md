# CareerMate

CareerMate 是一个面向职业发展的智能助手平台，提供简历优化、面试辅导、职业规划等能力。本仓库为项目 monorepo，包含后端服务与前端应用。

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Java 17、Spring Boot 3.2.x、Maven |
| 持久层 | MyBatis-Plus 3.5.x、PostgreSQL 15、Flyway |
| 前端 | Vue 3 + Vite（后续迁入） |
| 容器化 | Docker、Docker Compose |

## 当前阶段说明

**阶段一：项目基础骨架**

本阶段已完成：

- 标准 monorepo 目录结构
- Spring Boot 后端工程骨架
- 统一响应体 `ApiResponse`
- 全局异常处理 `GlobalExceptionHandler`
- 健康检查接口 `GET /api/health`
- Docker Compose（PostgreSQL + Backend）
- `frontend/` 目录预留（本阶段不实现前端页面）

本阶段未包含：登录认证、业务表、Agent Runtime、LLM/RAG 集成。

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
| `GET http://localhost:8081/api/health` | 应用健康检查（统一响应体） |
| `GET http://localhost:8081/actuator/health` | Spring Actuator 健康端点 |

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
│       │   └── health/          # 健康检查
│       └── resources/
│           ├── application.yml
│           └── application-dev.yml
└── frontend/                    # 前端预留目录（Vue 3 + Vite 后续迁入）
    └── .gitkeep
```

## 后续阶段计划

1. **数据库迁移**：创建 19 张业务表，开启 Flyway migration
2. **认证授权**：接入 Spring Security，实现登录注册
3. **前端迁入**：将 Vue 3 前端迁移至 `frontend/` 目录
4. **Agent Runtime**：实现智能体运行时与 Tool Registry
5. **LLM / RAG 集成**：对接大语言模型与 RAGForge 知识库
