# DB Migration — 异构数据库同步平台

[English](README_en.md) | 简体中文

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-ED8B00?style=flat-square&logo=openjdk&logoColor=white" alt="Java">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.4.4-6DB33F?style=flat-square&logo=springboot&logoColor=white" alt="Spring Boot">
  <img src="https://img.shields.io/badge/Vue.js-3.x-4FC08D?style=flat-square&logo=vue.js&logoColor=white" alt="Vue">
  <img src="https://img.shields.io/badge/MyBatis--Plus-3.5.16-red?style=flat-square" alt="MyBatis Plus">
  <img src="https://img.shields.io/badge/License-MIT-blue?style=flat-square" alt="License">
  <img src="https://img.shields.io/badge/Build-Maven-C71A36?style=flat-square&logo=apachemaven&logoColor=white" alt="Build">
</p>

> 一个面向企业的**异构数据库在线同步平台**，支持 Oracle、MySQL、PostgreSQL、达梦、GaussDB、OceanBase 之间的全量 / 增量数据迁移，提供可视化 Web 界面和实时进度推送。

---

## 📖 目录

- [✨ 功能特性](#-功能特性)
- [🗄️ 支持的数据库](#️-支持的数据库)
- [🏗️ 技术栈](#️-技术栈)
- [🚀 快速启动](#-快速启动)
- [⚙️ 配置说明](#️-配置说明)
- [📁 项目结构](#-项目结构)
- [🔄 同步架构](#-同步架构)
- [🤝 贡献指南](#-贡献指南)
- [💖 支持与赞助](#-支持与赞助)
- [📄 License](#-license)

---

## ✨ 功能特性

| 功能 | 说明 |
|------|------|
| 🔁 **全量同步** | 清空目标表后完整迁移源表全部数据 |
| ⚡ **增量同步** | 基于主键的 UPSERT 策略，存在则更新、不存在则插入 |
| 🚀 **并行同步** | JDK 21 Virtual Threads 并行同步多张表，可配置并发度（默认 8 张并行） |
| 🗺️ **字段映射** | 支持自定义字段映射，自动按同名列匹配兜底 |
| 📊 **实时进度** | WebSocket 推送同步进度、QPS、成功/失败行数 |
| 🛡️ **多数据源管理** | 动态创建、连接池隔离，密码 AES-256 加密存储 |
| 🏗️ **建表迁移** | 自动将源库 DDL 转换为目标库方言并建表 |
| 🔍 **视图同步** | 支持视图定义的跨库迁移 |
| 🎯 **灵活的 Schema 配置** | 支持在同步任务中指定源/目标 Schema/数据库名，独立于数据源配置 |
| 🚦 **任务控制** | 支持启动、暂停、停止同步任务 |
| 📝 **同步日志** | 完整记录每次同步的耗时、QPS 和错误信息 |

---

## 🗄️ 支持的数据库

| 数据库 | 版本 | 作为源库 | 作为目标库 |
|--------|------|:--------:|:----------:|
| **MySQL** | 5.7 / 8.x | ✅ | ✅ |
| **Oracle** | 11g / 19c / 21c | ✅ | ✅ |
| **PostgreSQL** | 12+ | ✅ | ✅ |
| **达梦 DM8** | 8.x | ✅ | ✅ |
| **华为 GaussDB** | 505/506 | ✅ | ✅ |
| **OceanBase** | 4.x | ✅ | ✅ |

---

## 🏗️ 技术栈

**后端**
- Java 21（Virtual Threads — 用于表级并行同步）
- Spring Boot 3.4.4 + Spring WebSocket
- MyBatis-Plus 3.5.16
- Druid 1.2.23 连接池

**前端**
- Vue 3 + Vite
- WebSocket 实时通信

**基础设施**
- MySQL（平台元数据存储）
- Maven 3.8+

---

## 🚀 快速启动

### 前置条件

- JDK 21+
- Maven 3.8+
- Node.js 18+（前端）
- MySQL 5.7+（平台元数据库）

### 1. 初始化元数据库

```sql
CREATE DATABASE db_migration CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

平台启动时会自动执行 `src/main/resources/db/schema.sql` 完成建表。

### 2. 修改配置

编辑 `src/main/resources/application.yml`，填写本地 MySQL 连接信息：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/db_migration?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
    username: your_username
    password: your_password
```

> ⚠️ **安全提示**：生产环境请通过环境变量注入 `app.aes-key`，切勿将密钥提交到版本库。

### 3. 启动后端

```bash
# 项目根目录下
mvn spring-boot:run
```

服务默认监听 `http://localhost:8520`

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端默认地址：`http://localhost:5173`

---

## 📝 使用说明

### 数据源与 Schema 配置

平台支持灵活的数据库/Schema 配置，适应不同数据库的架构差异：

#### 1. 数据源配置

在「数据源管理」中配置数据库连接信息：

- **MySQL/OceanBase**：`数据库名` 字段填写默认数据库名（如 `mydb`）
- **Oracle**：`数据库名` 字段填写服务名（如 `ORCL`）
- **PostgreSQL/GaussDB/达梦**：`数据库名` 字段填写数据库名（如 `postgres`）

#### 2. 同步任务配置

创建同步任务时，可以指定 **源 Schema** 和 **目标 Schema**：

| 数据库类型 | Schema 含义 | 示例 |
|-----------|-------------|------|
| **MySQL/OceanBase** | Schema = 数据库名 | `mydb`、`test_db` |
| **Oracle** | Schema = 用户/命名空间 | `SCOTT`、`HR` |
| **PostgreSQL/GaussDB/达梦** | Schema = 命名空间 | `public`、`app_schema` |

**配置优先级**：
- 同步任务中配置的 Schema/数据库名 **优先于** 数据源配置中的数据库名
- 如果同步任务未指定 Schema，则使用数据源配置中的数据库名

**典型场景**：
```text
场景 1：Oracle → PostgreSQL
  数据源配置：Oracle 服务名 = ORCL
  同步任务：源 Schema = SCOTT，目标 Schema = public
  实际连接：Oracle 连接到 ORCL 后切换到 SCOTT，PostgreSQL 连接到配置的数据库后切换到 public

场景 2：MySQL → MySQL（跨库同步）
  数据源配置：源库数据库名 = db1，目标库数据库名 = db2
  同步任务：源 Schema = source_db，目标 Schema = target_db
  实际连接：源库连接到 source_db，目标库连接到 target_db（覆盖数据源配置）
```

---

## ⚙️ 配置说明

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `server.port` | `8520` | 后端服务端口 |
| `spring.datasource.url` | — | 平台元数据 MySQL 地址 |
| `spring.datasource.druid.max-active` | `50` | Druid 最大连接数，建议 ≥ `table-concurrency × 2 + 10` |
| `sync.table-concurrency` | `8` | 全库同步时最大并行表数（Virtual Threads），最大可调至 `20` |
| `app.aes-key` | — | 数据源密码加密密钥（Base64 编码，256-bit） |
| `mybatis-plus.configuration.log-impl` | Slf4j | SQL 日志实现 |
| `logging.level.com.dbmigration` | `DEBUG` | 应用日志级别，生产环境建议改为 `INFO` |

**并发度与连接池对照**：

| `sync.table-concurrency` | 峰值 DB 连接 | 推荐 `max-active` |
|:---:|:---:|:---:|
| 8（默认）| 16 | ≥ 26 |
| 12 | 24 | ≥ 34 |
| 20（上限）| 40 | ≥ 50 ✅ |

---

## 📁 项目结构

```text
DbMigration/
├── frontend/                   # Vue 3 前端代码
├── src/
│   └── main/
│       ├── java/com/dbmigration/
│       │   ├── common/         # 公共枚举、工具类
│       │   ├── config/         # Spring 配置（WebSocket、安全等）
│       │   ├── datasource/     # 动态多数据源管理
│       │   ├── dialect/        # 各数据库方言适配层
│       │   └── sync/
│       │       ├── engine/     # 核心同步引擎（SyncEngine、SchemaSyncEngine）
│       │       ├── entity/     # 实体类（同步任务、日志、字段映射）
│       │       ├── mapper/     # MyBatis-Plus Mapper
│       │       └── service/    # 任务调度与服务层
│       └── resources/
│           ├── application.yml
│           └── db/
│               └── schema.sql  # 平台元数据建表脚本
└── pom.xml
```

---

## 🔄 同步架构

### 核心特性

#### 1. Schema/数据库名的灵活处理

平台针对不同数据库类型实现了智能的 Schema 处理逻辑：

- **Oracle**：使用服务名连接后，通过 `Connection.setSchema()` 切换到指定用户/Schema
- **MySQL/OceanBase**：Schema 即数据库名，直接替换连接 URL 中的数据库名
- **PostgreSQL/GaussDB/达梦**：使用配置的数据库名连接后，通过 `Connection.setSchema()` 切换到指定 Schema

#### 2. DDL 自动转换

支持跨数据库的表结构迁移，自动处理方言差异：

- **数据类型映射**：`NUMBER` → `NUMERIC`、`VARCHAR2` → `VARCHAR` 等
- **函数兼容性转换**：`SYSDATE` → `CURRENT_TIMESTAMP`、`NVL` → `COALESCE`、`RAISE_APPLICATION_ERROR` → `RAISE EXCEPTION` 等
- **约束处理**：自动转换主键、外键、唯一约束、检查约束
- **索引迁移**：支持普通索引和唯一索引的跨库迁移

#### 3. 表同步策略

| 策略 | 说明 | 适用场景 |
|------|------|----------|
| **CREATE_IF_NOT_EXISTS** | 表不存在时创建，存在则跳过 | 首次迁移或增量添加新表 |
| **DROP_AND_CREATE** | 删除已存在的表后重新创建（使用 CASCADE 级联删除外键约束） | 需要完全重建表结构 |
| **SKIP_IF_EXISTS** | 表已存在时跳过，不创建 | 仅同步数据，不修改表结构 |

### 单表同步流程（SyncEngine）

```text
源数据库
  │
  ├─ 流式读取（Statement.setFetchSize）
  │
  ▼
SyncEngine.doSync()
  ├─ 类型转换（oracle.sql.* → 标准 JDBC 类型）
  ├─ 批量写入（PreparedStatement.addBatch）
  └─ WebSocket 进度实时推送
  │
  ▼
目标数据库
  ├─ 全量模式：TRUNCATE → INSERT
  └─ 增量模式：UPSERT（有主键）/ DELETE + INSERT（无主键）
```

### 全库并行同步架构（基于 Virtual Threads）

```text
源库表列表（N 张）
       │
       ▼ resolveTableList()
  ┌───┬───────────────┬───┐
  表A  表B  ···  表H    ← 最多 8 张表并行（Semaphore 控制）
  各分配一个 Virtual Thread
  └───┴───────────────┴───┘
       │ 第一批完成
       ▼
  ┌───┬───────────────┬───┐
  表I  表J  ···  表P    ← 第二批并发执行
  └───┴───────────────┴───┘
       │ ···
       ▼
 AtomicLong 汇总结果 → buildSyncLog() 写入日志
```

> **调优建议**：您可以直接修改 `sync.table-concurrency`（默认 `8`，最大 `20`）以提升并行度，同时请调大 `druid.max-active` 确保连接池充足。修改配置后无需重新编译。

---

## 🤝 贡献指南

欢迎各位开发者共同参与完善此项目！

1. Fork 本仓库
2. 创建您的特性分支：`git checkout -b feature/AmazingFeature`
3. 提交您的更改：`git commit -m 'feat: Add some AmazingFeature'`
4. 推送至分支：`git push origin feature/AmazingFeature`
5. 开启一个 Pull Request

提交信息请遵循 [Conventional Commits](https://www.conventionalcommits.org/zh-hans/) 规范。

---

## 💖 支持与赞助

如果这个项目对您有帮助，并且您愿意支持开源开发，请作者喝杯咖啡 ☕️！

<details>
  <summary><b>点击展开 / 查看微信支付二维码</b></summary>
  <br>
  <div align="left">
    <img src="https://raw.githubusercontent.com/yubolun/obsidian-mysnippets-plugins/master/assets/wx_pay.jpg" width="250" title="微信支付">
  </div>
  <p>如果您觉得这个插件好用，欢迎打赏支持！</p>
</details>

---

## 📄 License

本项目基于 [MIT License](LICENSE) 许可进行开源。
