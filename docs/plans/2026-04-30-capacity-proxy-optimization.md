# Alethicode 承载能力优化实施计划（Proxy + 资源调优）

> **目标**：在不升级硬件（16 GB RAM / 8 核 / WSL2）的前提下，将系统从 ~40 并发提升到 200+ 并发用户。
>
> **日期**：2026-04-30
>
> **前置条件**：已阅读并理解 `deploy/docker-compose.yml` 全部服务配置。

---

## 目录

1. [当前基础设施现状](#1-当前基础设施现状)
2. [Phase 0：清理无效容器（5 分钟）](#2-phase-0清理无效容器5-分钟)
3. [Phase 1：PgBouncer 数据库连接池代理（30 分钟）](#3-phase-1pgbouncer-数据库连接池代理30-分钟)
4. [Phase 2：Nginx API 响应缓存（20 分钟）](#4-phase-2nginx-api-响应缓存20-分钟)
5. [Phase 3：Memgraph 内存扩容（5 分钟）](#5-phase-3memgraph-内存扩容5-分钟)
6. [Phase 4：JVM 内存调优（10 分钟）](#6-phase-4jvm-内存调优10-分钟)
7. [Phase 5：HikariCP 连接池调优（10 分钟）](#7-phase-5hikaricp-连接池调优10-分钟)
8. [Phase 6（可选）：LLM 请求代理层（30 分钟）](#8-phase-6可选llm-请求代理层30-分钟)
9. [验证清单](#9-验证清单)
10. [回滚方案](#10-回滚方案)
11. [预期效果汇总](#11-预期效果汇总)

---

## 1. 当前基础设施现状

### 1.1 硬件

| 资源 | 总量 | 备注 |
|------|------|------|
| RAM | 16 GB | WSL2 共享宿主机内存 |
| CPU | 8 核 | 虚拟化开销约 5-10% |
| 磁盘 | 1 TB（已用 342 GB） | ext4 on VHDX |

### 1.2 运行中的服务与内存分配

| 容器 | 内存限制 | 实际使用 | 使用率 | 备注 |
|------|---------|---------|--------|------|
| postgres | 384 MB | 169 MB | 44% | max_connections=40 |
| redis | 96 MB | 3.6 MB | 4% | maxmemory=64mb |
| nats | 128 MB | 7.6 MB | 6% | JetStream 启用 |
| temporal | 512 MB | 159 MB | 31% | auto-setup 镜像 |
| memgraph | 512 MB → 实际 2g | 412 MB | **80%** | 接近 OOM |
| alethicode-rag | 768 MB | 172 MB | 22% | LightRAG + FastAPI |
| backend | 1536 MB | - | - | 当前未运行 |
| frontend | 32 MB | - | - | Nginx 静态托管 |
| judge | 256 MB | 137 MB | 54% | 判题沙箱 |
| tutor-graph | 512 MB | 69 MB | 14% | LangGraph + FastAPI |
| jaeger | 512 MB | 9.7 MB | 2% | 全链路追踪 |
| prometheus | 64 MB | 30 MB | 47% | 监控采集 |
| grafana | 96 MB | 40 MB | 42% | 仪表盘 |

### 1.3 核心瓶颈

1. **PostgreSQL max_connections=40**：backend HikariCP 池 8 + tutor-graph + alethicode-rag + Temporal，可用余量极少。
2. **Memgraph 80% 内存使用**：RAG 图谱持续增长会导致 OOM 崩溃。
3. **无 API 响应缓存**：所有 GET 请求直穿到 backend → DB，读写比约 10:1 的 OJ 场景下浪费严重。
4. **3 个旧 OJ 容器无限重启**：`deploy-oj-ws-1`、`deploy-oj-worker-1`、`deploy-oj-web-1` 不断消耗 CPU 和 cgroup 开销。

### 1.4 数据库连接分配现状

| 服务 | 连接方式 | 连接数 |
|------|---------|--------|
| backend (Spring Boot) | HikariCP | maximum-pool-size=8, minimum-idle=2 |
| tutor-graph (Python) | psycopg / asyncpg | 默认连接池约 5 |
| alethicode-rag (Python) | psycopg via LightRAG | 默认连接池约 5 |
| temporal | 直连 PG | 约 10 |
| Flyway 迁移 | 临时连接 | 1 |
| 运维（pgAdmin / psql） | 手动 | 1-3 |
| **合计** | - | **约 30-32** |
| **PG max_connections** | - | **40** |
| **剩余余量** | - | **仅 8-10** |

---

## 2. Phase 0：清理无效容器（5 分钟）

### 2.1 是什么

`deploy-oj-ws-1`、`deploy-oj-worker-1`、`deploy-oj-web-1` 是旧的 Python OJ 前端/后端容器，在当前部署中已被 Java 后端替代，但它们仍在 docker-compose 配置中并不断重启失败（exit code 127），浪费 CPU 周期。

### 2.2 为什么要做

- 每次重启尝试消耗 CPU 和 cgroup 管理开销
- 日志噪音干扰问题排查
- 占用 docker 网络和 IP 资源

### 2.3 怎么做

**步骤 1**：找到旧 OJ 的 compose 文件

```bash
# 在 deploy 目录下查找所有 compose 文件
ls deploy/docker-compose*.yml

# 你会看到类似以下文件：
# deploy/docker-compose.yml          ← 新的 Java OJ（我们的主力）
# deploy/docker-compose-oj.yml       ← 旧的 Python OJ（要停掉的）
```

**步骤 2**：停止旧 OJ 容器

```bash
cd deploy

# 如果旧 OJ 的 compose 文件是 docker-compose-oj.yml：
docker compose -f docker-compose-oj.yml down

# 如果不确定哪个文件，直接按容器名停止：
docker stop deploy-oj-ws-1 deploy-oj-worker-1 deploy-oj-web-1 deploy-oj-judge-1 deploy-oj-redis-1 deploy-oj-postgres-1
docker rm deploy-oj-ws-1 deploy-oj-worker-1 deploy-oj-web-1 deploy-oj-judge-1 deploy-oj-redis-1 deploy-oj-postgres-1
```

**步骤 3**：验证

```bash
# 确认这些容器不再出现
docker ps -a | grep deploy-oj

# 应该无输出
```

### 2.4 影响

- 释放 CPU 时间片
- 释放 `deploy-oj-redis-1` 和 `deploy-oj-postgres-1` 占用的约 64 MB 内存
- 端口 5435、6380 释放

---

## 3. Phase 1：PgBouncer 数据库连接池代理（30 分钟）

### 3.1 是什么

PgBouncer 是一个轻量级的 PostgreSQL 连接池代理。它坐在应用和 PostgreSQL 之间，接受大量应用连接，但只用少量真实连接去连 PostgreSQL。

**类比**：把它想象成一个「号码叫取机」。餐厅（PostgreSQL）只有 40 个座位（max_connections=40），但外面可以排 200 个人（应用连接）。PgBouncer 就是那个叫号机——你拿到号就排队，座位空了叫你坐下（获取真实连接），吃完饭就让出座位给下一个人（释放连接）。

### 3.2 为什么要做

- 当前 PG 40 个连接，backend 8 个 + tutor-graph 5 个 + alethicode-rag 5 个 + temporal 10 个 = 28 个，剩 12 个
- 如果 backend 扩容到 2 个副本，光 HikariCP 就要 16 个连接，直接超限
- PgBouncer 用 transaction pooling 模式：一个应用连接只在执行 SQL 事务时才占用真实 PG 连接，事务结束立刻归还。这样 200 个应用连接可能只需要 20 个真实 PG 连接

### 3.3 怎么做

#### 步骤 1：在 `deploy/docker-compose.yml` 中添加 PgBouncer 服务

在 `redis:` 服务定义**之后**、`nats:` 服务定义**之前**，添加以下内容：

```yaml
  pgbouncer:
    image: bitnami/pgbouncer:1.23.1
    container_name: java-oj-pgbouncer
    restart: unless-stopped
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -h 127.0.0.1 -p 6432 -U onlinejudge"]
      interval: 10s
      timeout: 5s
      retries: 3
      start_period: 10s
    deploy:
      resources:
        limits:
          memory: 32m
    environment:
      # PgBouncer 连接到哪个 PostgreSQL
      POSTGRESQL_HOST: postgres
      POSTGRESQL_PORT: 5432
      POSTGRESQL_USERNAME: onlinejudge
      POSTGRESQL_PASSWORD: ${DB_PASSWORD}
      POSTGRESQL_DATABASE: alethicode

      # 连接池配置
      PGBOUNCER_POOL_MODE: transaction
      PGBOUNCER_DEFAULT_POOL_SIZE: 20
      PGBOUNCER_MIN_POOL_SIZE: 5
      PGBOUNCER_RESERVE_POOL_SIZE: 5
      PGBOUNCER_RESERVE_POOL_TIMEOUT: 3
      PGBOUNCER_MAX_CLIENT_CONN: 200
      PGBOUNCER_MAX_DB_CONNECTIONS: 35
      PGBOUNCER_SERVER_IDLE_TIMEOUT: 300
      PGBOUNCER_QUERY_WAIT_TIMEOUT: 30
      PGBOUNCER_LOG_CONNECTIONS: 0
      PGBOUNCER_LOG_DISCONNECTIONS: 0
      PGBOUNCER_STATS_PERIOD: 60
    depends_on:
      postgres:
        condition: service_healthy
    ports:
      - "127.0.0.1:6432:6432"
```

**参数说明**（逐个解释）：

| 参数 | 值 | 含义 |
|------|-----|------|
| `PGBOUNCER_POOL_MODE` | `transaction` | 事务级复用：每个事务结束就归还连接，是最推荐的模式 |
| `PGBOUNCER_DEFAULT_POOL_SIZE` | `20` | 每个数据库/用户对的默认连接池大小 |
| `PGBOUNCER_MIN_POOL_SIZE` | `5` | 池中最少保持 5 个空闲连接，避免冷启动延迟 |
| `PGBOUNCER_RESERVE_POOL_SIZE` | `5` | 当普通池耗尽时，额外提供 5 个应急连接 |
| `PGBOUNCER_RESERVE_POOL_TIMEOUT` | `3` | 等 3 秒后才动用应急池 |
| `PGBOUNCER_MAX_CLIENT_CONN` | `200` | PgBouncer 最多接受 200 个应用连接 |
| `PGBOUNCER_MAX_DB_CONNECTIONS` | `35` | PgBouncer 最多向 PG 发起 35 个真实连接（给 PG 留 5 个余量） |
| `PGBOUNCER_SERVER_IDLE_TIMEOUT` | `300` | 空闲 5 分钟的真实连接会被关闭 |
| `PGBOUNCER_QUERY_WAIT_TIMEOUT` | `30` | 排队超过 30 秒的查询会报错，防止无限等待 |

#### 步骤 2：修改 backend 的数据源连接指向 PgBouncer

在 `deploy/docker-compose.yml` 中，找到 `backend:` 服务的 `environment:` 段，修改：

```yaml
      # 修改前（直连 PostgreSQL）：
      # SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/alethicode

      # 修改后（经过 PgBouncer）：
      SPRING_DATASOURCE_URL: jdbc:postgresql://pgbouncer:6432/alethicode
```

同时修改 backend 的 `depends_on`，加上 pgbouncer：

```yaml
    depends_on:
      postgres:
        condition: service_healthy
      pgbouncer:                          # ← 新增
        condition: service_healthy        # ← 新增
      redis:
        condition: service_healthy
      nats:
        condition: service_healthy
      temporal:
        condition: service_healthy
      tutor-graph:
        condition: service_healthy
```

#### 步骤 3：修改 tutor-graph 的数据库连接

在 `deploy/docker-compose.yml` 中，找到 `tutor-graph:` 服务的 `environment:` 段，修改：

```yaml
      # 修改前：
      # TUTOR_GRAPH_DATABASE_URI: ${TUTOR_GRAPH_DATABASE_URI:-postgresql://onlinejudge:${DB_PASSWORD}@postgres:5432/alethicode}

      # 修改后：
      TUTOR_GRAPH_DATABASE_URI: ${TUTOR_GRAPH_DATABASE_URI:-postgresql://onlinejudge:${DB_PASSWORD}@pgbouncer:6432/alethicode}
```

同时修改 tutor-graph 的 `depends_on`，加上 pgbouncer：

```yaml
    depends_on:
      postgres:
        condition: service_healthy
      pgbouncer:                          # ← 新增
        condition: service_healthy        # ← 新增
```

#### 步骤 4：修改 alethicode-rag 的数据库连接

在 `deploy/docker-compose.yml` 中，找到 `alethicode-rag:` 服务的 `environment:` 段。
因为 alethicode-rag 使用 `network_mode: host`，它不能用 docker 内部 DNS `pgbouncer`，要用 host 暴露的端口：

```yaml
      # 修改前：
      # POSTGRES_HOST: 127.0.0.1
      # POSTGRES_PORT: ${POSTGRES_HOST_PORT:-5436}

      # 修改后（走 PgBouncer 的 host 端口 6432）：
      POSTGRES_HOST: 127.0.0.1
      POSTGRES_PORT: 6432
```

#### 步骤 5：Temporal 保持直连（不改）

**重要**：Temporal 使用 prepared statements 和 advisory locks，这些在 transaction pooling 模式下**不兼容**。所以 Temporal 必须继续直连 PostgreSQL，**不要改它的配置**。

#### 步骤 6：降低 PostgreSQL max_connections

PgBouncer 接管后，PG 自身不再需要那么多连接。修改 `deploy/docker-compose.yml` 中 postgres 的 command 段：

```yaml
      # 修改前：
      # - max_connections=40

      # 修改后：
      - max_connections=30
```

连接分配变成：PgBouncer 占 20（MAX_DB_CONNECTIONS=35 但实际常驻 20） + Temporal 10 = 30。

#### 步骤 7：重启服务

```bash
cd deploy
docker compose down
docker compose up -d
```

#### 步骤 8：验证 PgBouncer 工作正常

```bash
# 1. 检查 PgBouncer 容器状态
docker ps | grep pgbouncer
# 应该显示 (healthy)

# 2. 连接 PgBouncer 管理控制台
docker exec -it java-oj-pgbouncer psql -h 127.0.0.1 -p 6432 -U onlinejudge pgbouncer -c "SHOW POOLS;"
# 应该看到 pool_mode=transaction, sv_active（活跃服务器连接数）

# 3. 连接 PgBouncer 检查实际连接数
docker exec -it java-oj-pgbouncer psql -h 127.0.0.1 -p 6432 -U onlinejudge pgbouncer -c "SHOW STATS;"

# 4. 检查 PostgreSQL 实际连接数是否下降
docker exec -it java-oj-postgres psql -U onlinejudge -d alethicode -c "SELECT count(*) FROM pg_stat_activity;"
```

### 3.4 Transaction Pooling 注意事项

Transaction pooling 模式下，以下 PostgreSQL 特性**不可用**：

| 特性 | 是否影响 Alethicode | 说明 |
|------|---------------------|------|
| LISTEN/NOTIFY | 不影响 | Alethicode 用 NATS 做事件分发 |
| Prepared Statements | 需注意 | HikariCP 默认不用 server-side prepared statements |
| SET 命令 | 需注意 | `connection-init-sql` 中的 `SET statement_timeout` 仍生效（PgBouncer 支持 `server_reset_query`） |
| Advisory Locks | 不影响 | Temporal 保持直连不走 PgBouncer |
| TEMP Tables | 不影响 | Alethicode 不使用临时表 |

### 3.5 HikariCP 兼容性配置

在 Spring Boot 中，如果用了 server-side prepared statements，需要关闭它们。在 `backend/src/main/resources/application-prod.yml` 的 `spring.datasource.hikari` 段确认或添加：

```yaml
spring:
  datasource:
    hikari:
      # PgBouncer transaction pooling 兼容：禁用 server-side prepared statements
      data-source-properties:
        prepareThreshold: 0
```

**解释**：PostgreSQL JDBC 驱动默认在执行 5 次相同 SQL 后自动创建 server-side prepared statement。在 transaction pooling 下，这会导致「prepared statement does not exist」错误，因为连接可能是从池中借到的另一个。设置 `prepareThreshold: 0` 完全禁用此行为。

---

## 4. Phase 2：Nginx API 响应缓存（20 分钟）

### 4.1 是什么

在 Nginx 反向代理层对 API 响应做缓存。当多个用户请求相同的 API（比如题目列表），Nginx 直接从缓存返回，不再转发到 backend。

**类比**：快餐店的取餐架。厨房做好一份「今日套餐」放到架上，之后 10 个人来点同样的套餐，服务员直接从架上拿，不用再问厨房。

### 4.2 为什么要做

OJ 平台读写比约 **10:1**（10 次查看题目，才有 1 次提交代码）。以下 API 是高频读取且变更极少的：

| API | 变更频率 | 建议缓存时间 |
|-----|---------|-------------|
| `GET /api/problem` （题目列表） | 几小时一次 | 60 秒 |
| `GET /api/problem/{id}` （题目详情） | 几天一次 | 60 秒 |
| `GET /api/announcement` （公告） | 一天几次 | 30 秒 |
| `GET /api/website` （站点配置） | 极少 | 300 秒 |
| `GET /api/language` （语言列表） | 几乎不变 | 300 秒 |
| `GET /api/submission` （提交列表） | 每次提交后 | 10 秒 |

### 4.3 怎么做

#### 步骤 1：修改 `deploy/frontend-nginx.conf`

**完整替换为以下内容**：

```nginx
# --- Proxy Cache 配置 ---
# 定义一个名为 api_cache 的缓存区域
# - /var/cache/nginx/api: 缓存文件存储在磁盘的这个目录
# - levels=1:2: 两级目录结构，避免单目录文件过多
# - keys_zone=api_cache:10m: 在内存中用 10MB 存储缓存键（约 8 万个键）
# - max_size=256m: 磁盘上最多占 256MB
# - inactive=10m: 10 分钟没人访问的缓存自动删除
proxy_cache_path /var/cache/nginx/api
    levels=1:2
    keys_zone=api_cache:10m
    max_size=256m
    inactive=10m;

# --- 定义 backend 上游 ---
upstream alethicode_backend {
    server backend:8080;
    keepalive 16;
}

server {
    listen 80;
    server_name _;
    client_max_body_size 256m;
    client_body_timeout 300s;

    access_log off;

    # --- Gzip 压缩 ---
    gzip on;
    gzip_min_length 1024;
    gzip_comp_level 4;
    gzip_vary on;
    gzip_proxied any;
    gzip_types text/plain text/css application/json application/javascript
               text/xml application/xml application/xml+rss text/javascript
               image/svg+xml;

    # --- 读多写少的 API：走缓存 ---
    # 匹配题目列表、题目详情、公告、语言、站点配置
    location ~ ^/api/(problem|announcement|website|language)(/|$) {
        proxy_pass http://alethicode_backend;

        # 只缓存 GET 请求
        proxy_cache api_cache;
        proxy_cache_methods GET;
        proxy_cache_valid 200 60s;
        proxy_cache_valid 404 10s;
        proxy_cache_use_stale error timeout updating http_500 http_502 http_503;
        proxy_cache_lock on;
        proxy_cache_lock_timeout 5s;
        proxy_cache_key "$request_method$uri$is_args$args";

        # 带 Authorization 头的请求（已登录用户）也缓存，因为题目列表对所有人相同
        # 如果未来有权限差异化内容，改用 proxy_cache_bypass
        # proxy_cache_bypass $http_authorization;

        # 在响应头中标记缓存命中状态（HIT/MISS/EXPIRED），方便调试
        add_header X-Cache-Status $upstream_cache_status always;

        proxy_connect_timeout 60s;
        proxy_send_timeout 300s;
        proxy_read_timeout 300s;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header Connection "";
        proxy_http_version 1.1;
    }

    # --- 提交列表：短缓存 ---
    location ~ ^/api/submission(/|$) {
        proxy_pass http://alethicode_backend;

        proxy_cache api_cache;
        proxy_cache_methods GET;
        proxy_cache_valid 200 10s;
        proxy_cache_use_stale error timeout;
        proxy_cache_key "$request_method$uri$is_args$args";
        add_header X-Cache-Status $upstream_cache_status always;

        proxy_connect_timeout 60s;
        proxy_send_timeout 300s;
        proxy_read_timeout 300s;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header Connection "";
        proxy_http_version 1.1;
    }

    # --- 写操作、管理后台、AI 导学等：不缓存，直接透传 ---
    location /api/ {
        proxy_pass http://alethicode_backend;
        proxy_connect_timeout 60s;
        proxy_send_timeout 3600s;
        proxy_read_timeout 3600s;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header Connection "";
        proxy_http_version 1.1;
    }

    # --- WebSocket ---
    location /ws/ {
        proxy_pass http://alethicode_backend;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
    }

    # --- 头像等上传资源 ---
    location ^~ /public/avatar/ {
        proxy_pass http://alethicode_backend;
        proxy_connect_timeout 60s;
        proxy_send_timeout 300s;
        proxy_read_timeout 300s;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # --- Grafana 反向代理 ---
    location /grafana/ {
        proxy_pass http://alethicode_backend;
        proxy_connect_timeout 60s;
        proxy_send_timeout 300s;
        proxy_read_timeout 300s;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # --- 静态资源：.mjs 文件 ---
    location ~* \.mjs$ {
        root /usr/share/nginx/html;
        default_type application/javascript;
        expires 30d;
        add_header Cache-Control "public, immutable";
        access_log off;
    }

    # --- 静态资源：常见类型 ---
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)$ {
        root /usr/share/nginx/html;
        expires 30d;
        add_header Cache-Control "public, immutable";
        access_log off;
    }

    # --- 管理后台 SPA ---
    location /admin/ {
        root /usr/share/nginx/html;
        try_files $uri $uri/ /admin/index.html;
    }

    # --- 前端 SPA 兜底 ---
    location / {
        root /usr/share/nginx/html;
        try_files $uri $uri/ /index.html;
    }
}
```

#### 步骤 2：确保 Nginx 缓存目录存在

在 `deploy/frontend.Dockerfile` 中添加一行创建缓存目录：

```dockerfile
FROM node:20.19-bullseye AS build
WORKDIR /app
COPY frontend/package*.json ./
RUN npm ci --legacy-peer-deps
COPY frontend ./
RUN npm run build

FROM nginx:1.27-alpine
RUN mkdir -p /var/cache/nginx/api
COPY deploy/frontend-nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
```

唯一改动：在 `COPY` 之前加了 `RUN mkdir -p /var/cache/nginx/api`。

#### 步骤 3：重建 frontend 镜像并重启

```bash
cd deploy
docker compose build frontend
docker compose up -d frontend
```

#### 步骤 4：验证缓存生效

```bash
# 第一次请求（MISS）
curl -sI http://127.0.0.1:18080/api/problem | grep X-Cache
# 应该输出：X-Cache-Status: MISS

# 第二次请求（HIT）
curl -sI http://127.0.0.1:18080/api/problem | grep X-Cache
# 应该输出：X-Cache-Status: HIT

# POST 请求不会被缓存
curl -sI -X POST http://127.0.0.1:18080/api/submission | grep X-Cache
# 应该没有 X-Cache-Status 头
```

### 4.4 缓存失效策略

Nginx `proxy_cache_valid 200 60s` 意味着缓存 60 秒后自动过期。对于 OJ 场景：

- **管理员修改题目后**：学生最多等 60 秒就能看到新内容，可接受
- **如果需要立即清除缓存**：在 Nginx 容器中删除缓存目录

```bash
docker exec java-oj-frontend rm -rf /var/cache/nginx/api/*
docker exec java-oj-frontend nginx -s reload
```

### 4.5 预期效果

- 读 API 请求量降低 **80-90%**（因为 60 秒内的重复请求全部命中缓存）
- backend CPU 和数据库查询负载大幅下降
- 用户感知的页面加载速度提升（Nginx 缓存命中时响应 <1ms，vs backend 处理 50-200ms）

---

## 5. Phase 3：Memgraph 内存扩容（5 分钟）

### 5.1 是什么

Memgraph 是 LightRAG 用来存储知识图谱的图数据库。当前内存使用 412/512 MB（80%），随着课件 RAG 图谱增长会 OOM。

### 5.2 怎么做

修改 `deploy/docker-compose.yml` 中 `memgraph:` 服务的内存限制：

```yaml
    deploy:
      resources:
        limits:
          # 修改前：
          # memory: ${MEMGRAPH_MEMORY_LIMIT:-2g}
          # 实际观察 docker stats 显示上限 512MB，说明 .env 中设了 512m

          # 修改后（直接写死，不依赖 .env 变量）：
          memory: 1536m
```

同时检查 `.env` 文件，如果有 `MEMGRAPH_MEMORY_LIMIT=512m`，改为 `MEMGRAPH_MEMORY_LIMIT=1536m`。

```bash
# 找到 .env 并修改
grep MEMGRAPH_MEMORY_LIMIT deploy/.env
# 如果存在，改为：
# MEMGRAPH_MEMORY_LIMIT=1536m

# 重启 memgraph
cd deploy
docker compose restart memgraph
```

### 5.3 验证

```bash
docker stats --no-stream java-oj-memgraph
# 应该看到 LIMIT 从 512MiB 变为 1.5GiB
# MEM% 应该从 80% 降到 ~27%
```

### 5.4 影响

- 增加约 1 GB 内存分配（但 Memgraph 不会立即使用，只是提高上限防止 OOM）
- 16 GB 总内存 → 当前使用约 4.3 GB → 仍有约 10 GB 空闲，完全承受得起

---

## 6. Phase 4：JVM 内存调优（10 分钟）

### 6.1 是什么

Spring Boot backend 是最大的内存消费者（-Xmx1024m + Metaspace + 线程栈 + 堆外），优化 JVM 参数可以在不影响性能的前提下降低内存使用。

### 6.2 怎么做

修改 `deploy/docker-compose.yml` 中 `backend:` 服务的 `JAVA_OPTS` 环境变量：

```yaml
      # 修改前：
      # JAVA_OPTS: ${JAVA_OPTS:--Xms256m -Xmx1024m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:MaxMetaspaceSize=256m -XX:ReservedCodeCacheSize=96m -XX:+UseCompressedOops -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/heapdump.hprof -Xlog:gc*:file=/tmp/gc.log:time,uptime,level,tags:filecount=3,filesize=10m}

      # 修改后：
      JAVA_OPTS: >-
        -Xms256m
        -Xmx1024m
        -XX:+UseG1GC
        -XX:MaxGCPauseMillis=200
        -XX:MaxMetaspaceSize=192m
        -XX:ReservedCodeCacheSize=64m
        -XX:+UseCompressedOops
        -XX:+UseStringDeduplication
        -XX:+HeapDumpOnOutOfMemoryError
        -XX:HeapDumpPath=/tmp/heapdump.hprof
        -Xlog:gc*:file=/tmp/gc.log:time,uptime,level,tags:filecount=3,filesize=10m
```

**改了什么**：

| 参数 | 修改前 | 修改后 | 说明 |
|------|--------|--------|------|
| `MaxMetaspaceSize` | 256m | 192m | OJ 后端的类数量有限，192m 足够；节省 64 MB |
| `ReservedCodeCacheSize` | 96m | 64m | JIT 编译缓存；OJ 热路径不多，64m 够用；节省 32 MB |
| `UseStringDeduplication` | 无 | 新增 | G1GC 下自动去重相同 String 内容，减少堆使用 10-15% |

### 6.3 验证

```bash
# backend 启动后检查参数
docker exec java-oj-backend jcmd 1 VM.flags | grep -E "Metaspace|CodeCache|StringDedup"

# 检查堆使用
docker exec java-oj-backend jcmd 1 GC.heap_info
```

### 6.4 预期效果

节省约 96 MB 的非堆内存 + 堆内 String 去重节省约 50-100 MB = 总计节省 **~150-200 MB**。

---

## 7. Phase 5：HikariCP 连接池调优（10 分钟）

### 7.1 是什么

HikariCP 是 Spring Boot 默认的数据库连接池。当前 `maximum-pool-size=8`，配合 PgBouncer 后可以进一步调优。

### 7.2 怎么做

修改 `backend/src/main/resources/application-prod.yml`：

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 8
      minimum-idle: 2
      connection-timeout: 3000
      validation-timeout: 2000
      idle-timeout: 120000
      max-lifetime: 600000
      leak-detection-threshold: 30000
      connection-init-sql: "SET statement_timeout = 30000"
      # PgBouncer transaction pooling 兼容：禁用 server-side prepared statements
      data-source-properties:
        prepareThreshold: 0
```

**解释新增的 `prepareThreshold: 0`**：

PostgreSQL JDBC 驱动默认在相同 SQL 执行 5 次后创建 server-side prepared statement。在 PgBouncer transaction pooling 模式下：

1. 连接 A 在 PgBouncer 上创建了 prepared statement `S_1`
2. 下一个事务可能被分配到连接 B
3. 连接 B 上没有 `S_1`，导致 `prepared statement "S_1" does not exist` 错误

设置 `prepareThreshold: 0` 完全禁用 server-side prepared statements，改用 client-side 参数绑定，性能差异在 OJ 场景下可忽略。

### 7.3 验证

```bash
# 启动后通过 Actuator 检查 HikariCP 指标
curl -s http://127.0.0.1:8081/actuator/metrics/hikaricp.connections | jq .

# 关注：
# - hikaricp.connections.active  应该 < maximum-pool-size
# - hikaricp.connections.idle    应该 >= minimum-idle
# - hikaricp.connections.pending 应该 = 0（如果 > 0 说明池太小）
```

---

## 8. Phase 6（可选）：LLM 请求代理层（30 分钟）

### 8.1 是什么

在 DeepSeek API 前放一个代理（如 one-api 或 litellm），实现：
- **语义缓存**：相同/近似 prompt 复用缓存结果
- **请求排队**：突发请求排队等待，避免超出 API rate limit
- **多 provider 路由**：DeepSeek 不可用时自动切到备用 LLM

### 8.2 为什么是可选的

Alethicode 已有以下 LLM 韧性机制：
- Resilience4j circuit breaker（`llmProvider` 实例）
- Retry with exponential backoff
- Bulkhead（`max-concurrent-calls: 30`）
- Fallback prefixes（`ALETHICODE_AI_FALLBACK_PREFIXES`）

这些已经覆盖了大部分故障场景。LLM 代理层的增量价值主要在**缓存**和**成本优化**。

### 8.3 如何评估是否需要

检查 LLM API 调用频率和成本：

```bash
# 如果 Langfuse 已配置，查看 trace 统计
# 或检查 Prometheus 指标：
curl -s http://127.0.0.1:8081/actuator/metrics/resilience4j.ratelimiter.available.permissions | jq .
```

如果每日 LLM 调用 < 1000 次，代理层的投入产出比不高，建议跳过。

### 8.4 如果决定做，推荐方案

使用 **one-api**（国产开源，对 DeepSeek 兼容性最好）：

```yaml
  one-api:
    image: justsong/one-api:latest
    container_name: java-oj-one-api
    restart: unless-stopped
    deploy:
      resources:
        limits:
          memory: 128m
    environment:
      SQL_DSN: postgresql://onlinejudge:${DB_PASSWORD}@pgbouncer:6432/alethicode
      SESSION_SECRET: ${ONE_API_SESSION_SECRET:-alethicode-oneapi-2026}
    ports:
      - "127.0.0.1:3100:3000"
    depends_on:
      pgbouncer:
        condition: service_healthy
```

然后将所有 LLM 调用的 `base_url` 指向 `http://one-api:3000`。

---

## 9. 验证清单

每个 Phase 完成后，按以下清单逐项验证：

### 9.1 Phase 0 验证

- [ ] `docker ps -a | grep deploy-oj` 无输出
- [ ] `docker stats --no-stream` 不再显示 deploy-oj 容器

### 9.2 Phase 1 验证（PgBouncer）

- [ ] `docker ps | grep pgbouncer` 显示 (healthy)
- [ ] `SHOW POOLS;` 显示 `pool_mode=transaction`
- [ ] `SELECT count(*) FROM pg_stat_activity;` 连接数 ≤ 30
- [ ] backend 日志无 `prepared statement does not exist` 错误
- [ ] tutor-graph 日志无数据库连接错误
- [ ] alethicode-rag 日志无数据库连接错误
- [ ] 在前端正常提交一道题，确认 judge 流程正常

### 9.3 Phase 2 验证（Nginx Cache）

- [ ] `curl -sI http://127.0.0.1:18080/api/problem | grep X-Cache` 第一次 MISS，第二次 HIT
- [ ] POST 请求无 X-Cache-Status 头
- [ ] 管理后台修改题目后，60 秒内前端能看到新内容
- [ ] WebSocket 连接正常（`/ws/` 不受缓存影响）

### 9.4 Phase 3 验证（Memgraph）

- [ ] `docker stats --no-stream java-oj-memgraph` 的 LIMIT ≥ 1.5GiB
- [ ] alethicode-rag 的 `/health` 返回 200

### 9.5 Phase 4 验证（JVM）

- [ ] backend 正常启动，日志无 OOM 或 Metaspace 错误
- [ ] `jcmd 1 GC.heap_info` 显示合理的堆使用

### 9.6 Phase 5 验证（HikariCP）

- [ ] `hikaricp.connections.pending` = 0
- [ ] 无 `prepared statement does not exist` 异常

---

## 10. 回滚方案

如果任何 Phase 出现问题，按以下步骤回滚：

### 10.1 PgBouncer 回滚

1. 将 backend、tutor-graph、alethicode-rag 的数据源 URL 改回直连 PostgreSQL
2. 删除 `pgbouncer:` 服务定义
3. 将 PostgreSQL `max_connections` 改回 40
4. `docker compose down && docker compose up -d`

### 10.2 Nginx Cache 回滚

1. 将 `deploy/frontend-nginx.conf` 恢复为 git 中的版本：`git checkout deploy/frontend-nginx.conf`
2. 删除 Dockerfile 中的 `RUN mkdir -p /var/cache/nginx/api`
3. `docker compose build frontend && docker compose up -d frontend`

### 10.3 Memgraph 回滚

1. 将 `MEMGRAPH_MEMORY_LIMIT` 改回 512m
2. `docker compose restart memgraph`

### 10.4 JVM 回滚

1. 将 `JAVA_OPTS` 恢复为原值
2. `docker compose restart backend`

---

## 11. 预期效果汇总

| Phase | 措施 | 并发提升 | 额外内存 | 耗时 |
|-------|------|---------|---------|------|
| 0 | 清理无效容器 | +5%（回收 CPU） | -64 MB | 5 min |
| 1 | PgBouncer | **5-10x 并发** | +8 MB | 30 min |
| 2 | Nginx API Cache | **-80% 后端请求** | +10 MB | 20 min |
| 3 | Memgraph 扩容 | 防 OOM 崩溃 | +1024 MB | 5 min |
| 4 | JVM 调优 | 降低内存占用 | -150 MB | 10 min |
| 5 | HikariCP 调优 | PgBouncer 兼容 | 0 | 10 min |
| 6 | LLM 代理（可选） | 降 API 成本 | +128 MB | 30 min |

### 最终效果

| 指标 | 优化前 | 优化后 |
|------|--------|--------|
| 最大并发用户 | ~40 | **200+** |
| API 读请求到达 backend | 100% | **~20%** |
| PG 连接使用 | 30/40（75%） | **15/30（50%）** |
| Memgraph 内存使用 | 80% | **~27%** |
| 总内存消耗 | ~5.8 GB（backend 运行时） | ~6.6 GB |
| 空闲内存 | ~10 GB | ~9.4 GB |

**结论**：Phase 0-2 是核心优化，完成后系统可以轻松应对一个班级 40-60 人的同时在线场景，甚至支撑 2-3 个班级同时上课（120-180 并发）。Phase 3-5 是安全加固和兼容性保障，建议一并完成。Phase 6 根据实际 LLM 调用量决定。
