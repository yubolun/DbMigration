# DB Migration — Heterogeneous Database Sync Platform

简体中文 | [English](README_en.md)

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-ED8B00?style=flat-square&logo=openjdk&logoColor=white" alt="Java">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.4.4-6DB33F?style=flat-square&logo=springboot&logoColor=white" alt="Spring Boot">
  <img src="https://img.shields.io/badge/Vue.js-3.x-4FC08D?style=flat-square&logo=vue.js&logoColor=white" alt="Vue">
  <img src="https://img.shields.io/badge/MyBatis--Plus-3.5.16-red?style=flat-square" alt="MyBatis Plus">
  <img src="https://img.shields.io/badge/License-MIT-blue?style=flat-square" alt="License">
  <img src="https://img.shields.io/badge/Build-Maven-C71A36?style=flat-square&logo=apachemaven&logoColor=white" alt="Build">
</p>

> An enterprise-grade **heterogeneous database online synchronization platform**. It supports full and incremental data migration among Oracle, MySQL, PostgreSQL, DM (Dameng), GaussDB, and OceanBase, providing a visual Web UI and real-time progress updates.

---

## 📖 Table of Contents

- [✨ Features](#-features)
- [🗄️ Supported Databases](#️-supported-databases)
- [🏗️ Tech Stack](#️-tech-stack)
- [🚀 Quick Start](#-quick-start)
- [⚙️ Configuration](#️-configuration)
- [📁 Project Structure](#-project-structure)
- [🔄 Sync Architecture](#-sync-architecture)
- [🤝 Contributing](#-contributing)
- [💖 Sponsor](#-sponsor)
- [📄 License](#-license)

---

## ✨ Features

| Feature | Description |
|---------|-------------|
| 🔁 **Full Sync** | Truncates target tables and completely migrates all data from source tables. |
| ⚡ **Incremental Sync** | Primary-key-based UPSERT strategy: updates existing rows and inserts new ones. |
| 🚀 **Parallel Sync** | Powered by JDK 21 Virtual Threads to sync multiple tables in parallel (default 8 concurrent threads). |
| 🗺️ **Field Mapping** | Supports custom field mapping, with automatic fallback mapping based on identical column names. |
| 📊 **Real-time Progress** | WebSocket push notifications for sync progress, QPS, and success/failure rows. |
| 🛡️ **Data Source Management** | Dynamic creation, isolated connection pools, and AES-256 encrypted password storage. |
| 🏗️ **DDL Migration** | Automatically translates source DDL into target dialects and creates tables on the fly. |
| 🔍 **View Sync** | Supports cross-database migration of View definitions. |
| 🚦 **Task Control** | Start, pause, and stop sync tasks at any time. |
| 📝 **Sync Logs** | Comprehensive logs detailing duration, QPS, and error messages for every sync. |

---

## 🗄️ Supported Databases

| Database | Version | As Source | As Target |
|----------|---------|:---------:|:---------:|
| **MySQL** | 5.7 / 8.x | ✅ | ✅ |
| **Oracle** | 11g / 19c / 21c | ✅ | ✅ |
| **PostgreSQL** | 12+ | ✅ | ✅ |
| **DM8 (Dameng)** | 8.x | ✅ | ✅ |
| **GaussDB** | 505/506 | ✅ | ✅ |
| **OceanBase** | 4.x | ✅ | ✅ |

---

## 🏗️ Tech Stack

**Backend**
- Java 21 (Virtual Threads — for table-level parallel syncing)
- Spring Boot 3.4.4 + Spring WebSocket
- MyBatis-Plus 3.5.16
- Druid 1.2.23 Connection Pool

**Frontend**
- Vue 3 + Vite
- WebSocket real-time communication

**Infrastructure**
- MySQL (Platform metadata storage)
- Maven 3.8+

---

## 🚀 Quick Start

### Prerequisites

- JDK 21+
- Maven 3.8+
- Node.js 18+ (for Frontend)
- MySQL 5.7+ (Platform meta-database)

### 1. Initialize Meta-database

```sql
CREATE DATABASE db_migration CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

Upon startup, the platform will automatically execute `src/main/resources/db/schema.sql` to initialize tables.

### 2. Update Configuration

Edit `src/main/resources/application.yml` and provide your local MySQL connection info:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/db_migration?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
    username: your_username
    password: your_password
```

> ⚠️ **Security Tip**: For production, please inject `app.aes-key` via environment variables. Do NOT commit the AES key to version control.

### 3. Start Backend

```bash
# In the project root directory
mvn spring-boot:run
```

The service listens on `http://localhost:8520` by default.

### 4. Start Frontend

```bash
cd frontend
npm install
npm run dev
```

The frontend runs at: `http://localhost:5173` by default.

---

## ⚙️ Configuration

| Key | Default | Description |
|-----|---------|-------------|
| `server.port` | `8520` | Backend service port. |
| `spring.datasource.url` | — | MySQL address for platform metadata. |
| `spring.datasource.druid.max-active` | `50` | Druid max connections. Recommended: `≥ table-concurrency × 2 + 10`. |
| `sync.table-concurrency` | `8` | Max parallel tables during full sync (via Virtual Threads). Can be adjusted up to `20`. |
| `app.aes-key` | — | Base64 encoded, 256-bit AES key for data source password encryption. |
| `mybatis-plus.configuration.log-impl` | Slf4j | SQL logging implementation. |
| `logging.level.com.dbmigration` | `DEBUG` | Application log level. Change to `INFO` in production. |

**Concurrency vs. Connection Pool Size:**

| `sync.table-concurrency` | Peak DB Connections | Recommended `max-active` |
|:---:|:---:|:---:|
| 8 (Default) | 16 | ≥ 26 |
| 12 | 24 | ≥ 34 |
| 20 (Max) | 40 | ≥ 50 ✅ |

---

## 📁 Project Structure

```text
DbMigration/
├── frontend/                   # Vue 3 Frontend Code
├── src/
│   └── main/
│       ├── java/com/dbmigration/
│       │   ├── common/         # Common Enums, Utils
│       │   ├── config/         # Spring Config (WebSocket, Security, etc.)
│       │   ├── datasource/     # Dynamic Multi-Datasource Management
│       │   ├── dialect/        # Database Dialect Adapters
│       │   └── sync/
│       │       ├── engine/     # Core Sync Engines (SyncEngine, SchemaSyncEngine)
│       │       ├── entity/     # Entities (Task, Log, Mapping)
│       │       ├── mapper/     # MyBatis-Plus Mappers
│       │       └── service/    # Task Scheduling & Business Logic
│       └── resources/
│           ├── application.yml
│           └── db/
│               └── schema.sql  # Meta-database DDL script
└── pom.xml
```

---

## 🔄 Sync Architecture

### Single Table Sync Flow (SyncEngine)

```text
Source Database
  │
  ├─ Streaming Read (Statement.setFetchSize)
  │
  ▼
SyncEngine.doSync()
  ├─ Type Conversion (oracle.sql.* → Standard JDBC Types)
  ├─ Batch Write (PreparedStatement.addBatch)
  └─ WebSocket Real-time Progress Push
  │
  ▼
Target Database
  ├─ Full Mode: TRUNCATE → INSERT
  └─ Incremental Mode: UPSERT (w/ PK) / DELETE + INSERT (w/o PK)
```

### Full-DB Parallel Architecture (Virtual Threads)

```text
Source Tables List (N Tables)
       │
       ▼ resolveTableList()
  ┌───┬───────────────┬───┐
  TabA TabB  ··· TabH    ← Max 8 tables concurrently (Semaphore controlled)
  Allocated 1 Virtual Thread each
  └───┴───────────────┴───┘
       │ First batch finishes
       ▼
  ┌───┬───────────────┬───┐
  TabI TabJ  ··· TabP    ← Second batch executes concurrently
  └───┴───────────────┴───┘
       │ ···
       ▼
 AtomicLong Aggregates Results → buildSyncLog() writes to Log
```

> **Tuning Tips**: You can directly modify `sync.table-concurrency` (default `8`, max `20`) to increase parallelism. Remember to also increase `druid.max-active` to ensure a sufficient connection pool. No recompilation is needed after modifying properties.

---

## 🤝 Contributing

Contributions are what make the open source community such an amazing place to learn, inspire, and create. Any contributions you make are greatly appreciated.

1. Fork the Project
2. Create your Feature Branch: `git checkout -b feature/AmazingFeature`
3. Commit your Changes: `git commit -m 'feat: Add some AmazingFeature'`
4. Push to the Branch: `git push origin feature/AmazingFeature`
5. Open a Pull Request

Please adhere to the [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/) specification.

---

## 💖 Sponsor

If you find this project helpful and would like to support open-source development, buy the author a cup of coffee ☕️!

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

Distributed under the [MIT License](LICENSE).
