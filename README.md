# 考试成绩管理系统（SMS - Score Management System）

一个前后端分离的考试成绩管理系统，覆盖学生信息管理、成绩录入、成绩查询与成绩统计四大核心模块，为各模块提供完整的增删改查能力。

## 技术栈

| 层次 | 技术 |
| ---- | ---- |
| 后端 | Spring Boot 3.2.5 · Java 21 · Spring Data JPA · Bean Validation |
| 数据库 | PostgreSQL（生产，profile `pg`）/ H2 内存库（演示，默认） |
| 前端 | Vue 3 · Vite 5 · Element Plus · Axios · ECharts · Pinia · Vue Router |
| 版本控制 | Git-Flow 分支规范（main + feature/*） |

## 目录结构

```
.
├── backend/                  # Spring Boot 后端
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/exam/    # 实体/仓库/服务/控制器/配置/通用
│       └── resources/        # application.yml / application-pg.yml
├── frontend/                 # Vue 3 前端
│   ├── package.json
│   └── src/                  # main.js / App.vue / router / api / views
├── docs/                     # 一期需求规格说明 / 技术方案选型
└── push.sh                   # 远程推送脚本
```

## 核心模块

- **学生信息管理**：学生增删改查、分页、关键词搜索、逻辑删除（软删除）
- **基础数据**：班级、课程字典维护
- **成绩录入**：单条录入、批量录入、成绩修改/删除、唯一性校验、分值域校验
- **成绩查询**：按考试/课程/学生多条件筛选查询
- **成绩统计**：平均分、最高/最低分、及格率、优秀率、分数段分布，ECharts 可视化

## 快速开始

### 后端（默认 H2 演示库，自动初始化种子数据）

```bash
cd backend
mvn spring-boot:run
# 或打包运行
mvn clean package
java -jar target/*.jar
```

### 后端（PostgreSQL，profile = pg）

1. 创建数据库：

```sql
CREATE DATABASE examdb;
```

2. 以 `pg` profile 启动：

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=pg
# 或
java -jar target/*.jar --spring.profiles.active=pg
```

> PostgreSQL 连接信息见 `backend/src/main/resources/application-pg.yml`，请按环境调整账密。

### 前端

```bash
cd frontend
npm install
npm run dev     # 开发，默认 http://localhost:5173
npm run build   # 生产构建
```

前端开发服务器已配置代理转发 `/api` 到后端 `http://localhost:8080`，并已启用跨域（CORS）。

## 分支规范（Git-Flow）

- `main`：稳定主干，由各功能分支合并而来
- `feature/base-data`：基础数据模块（班级/课程）
- `feature/student-management`：学生信息管理模块
- `feature/grade-entry`：成绩录入与查询模块
- `feature/grade-statistics`：成绩统计模块

## 密钥与数据安全（请务必阅读）

- **JWT / AES 密钥**：由 `AppKeyProvider` 管理。若未通过环境变量显式注入
  （`APP_SECURITY_TOKEN_SECRET` / `APP_CRYPTO_KEY`），**首次启动会自动生成强随机密钥并落盘到
  `backend/data/app-secrets.properties`**，之后复用。
- **务必保留该密钥文件**：AES 密钥用于加密身份证号等敏感字段，**更换机器/迁移部署时须整体拷贝
  `data/app-secrets.properties`**，否则历史加密数据将无法解密（不可逆）。
- 生产环境仍建议通过环境变量显式注入强随机密钥，并通过 `ProductionGuard` 强制门禁
  （非显式注入 + PostgreSQL + 关闭播种）。
- 请勿将 `app-secrets.properties` 提交到版本库（建议加入 `.gitignore`）。

## 文档

- [一期需求规格说明](docs/一期需求规格说明.md)
- [技术方案选型](docs/技术方案选型.md)
