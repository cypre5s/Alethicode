# 作品情况表

**参赛编号**：\_\_\_\_\_\_\_\_\_（网上报名时产生）

| 项目 | 内容 |
|------|------|
| **作品名称** | Alethicode——面向初学者的 LLM 驱动智能教育平台 |
| **作品类型** | ☑ 程序设计应用（含移动应用） ☑ 人工智能与大数据应用 ☑ Web 应用与开发 |

---

## 作者信息

|  | 学校 | 作者一 | 作者二 | 作者三 |
|---|------|--------|--------|--------|
| **姓名** | ×××× | ××× | ××× | ××× |
| **身份证** | - | ×××××× | ×××××× | ×××××× |
| **院系** | - | ×××× | ×××× | ×××× |
| **专业** | - | ×××× | ×××× | ×××× |
| **年级** | - | 20XX级 | 20XX级 | 20XX级 |
| **邮箱** | - | ×××@×××.com | ×××@×××.com | ×××@×××.com |
| **电话** | - | 1×××××××××× | 1×××××××××× | 1×××××××××× |

## 指导教师信息

|  | 指导教师一 | 指导教师二 |
|---|-----------|-----------|
| **姓名** | ××× | ××× |
| **院系** | ×××× | ×××× |
| **邮箱** | ×××@×××.edu.cn | ×××@×××.edu.cn |
| **电话** | 1×××××××××× | 1×××××××××× |

---

## 系统环境要求和安装说明

### 1. 硬件环境和操作系统

| 项目 | 要求 |
|------|------|
| CPU | 4 核及以上（推荐 8 核） |
| 内存 | 8GB 及以上（推荐 16GB） |
| 存储 | 30GB 及以上可用空间 |
| 网络 | 首次安装依赖、拉取容器镜像与访问外部 LLM/Embedding API 时需要联网 |
| 操作系统 | Linux（Ubuntu 22.04+ 推荐）/ macOS / Windows（WSL2 推荐） |

### 2. 开发平台（含开源/第三方工具）

| 工具 | 版本 | 用途 | 说明 |
|------|------|------|------|
| Java JDK | 21+ | 后端运行时 | 与 `backend/pom.xml` 保持一致 |
| Maven | 3.9+ | 后端构建与启动 | 运行 Spring Boot 后端 |
| Node.js | 20.19.0+ | 前端运行时 | `start.sh` 会校验最低版本 |
| npm | 10.8.2+ | 前端包管理 | 与 `frontend/package.json` engines 一致 |
| Docker / Docker Compose | 24+ | PostgreSQL、Redis、Judge 容器 | 本地推荐使用 |
| PostgreSQL | 16（容器） | 主数据库 | `deploy/docker-compose.yml` 使用 `pgvector/pgvector:pg16` |
| Redis | 7（容器） | 缓存与 Session | `deploy/docker-compose.yml` 使用 `redis:7` |
| Git | 2.x | 版本管理 | 用于拉取源码 |

### 3. 运行环境和安装说明

**方式一：使用 Ubuntu/WSL2 比赛安装包（推荐提交形态）**

先在打包机器上生成安装包：

```bash
cd /path/to/Alethicode
bash scripts/competition/build_competition_installer.sh
```

交付评审环境的文件：

- `release/competition_installer/Alethicode-Installer.run`

评审环境安装与启动：

```bash
chmod +x Alethicode-Installer.run
./Alethicode-Installer.run
cd ~/.local/share/alethicode-competition/alethicode_competition
./bin/start.sh
```

验证与停止：

```bash
./bin/status.sh
./bin/smoke.sh
./bin/stop.sh
```

默认访问地址：

- 前端主页：`http://127.0.0.1:18080`

**方式二：使用仓库自带 `start.sh` 一键启动（适合开发）**

```bash
# 1. 克隆项目
 git clone <repo-url>
 cd Alethicode

# 2. 准备环境变量
 cp deploy/.env.example deploy/.env
 cp backend/.env.example backend/.env

# 3. 编辑 deploy/.env，至少填写以下内容
 # DB_PASSWORD=...
 # REDIS_PASSWORD=...
 # JUDGE_SERVER_TOKEN=...

# 4. 编辑 backend/.env，至少填写以下内容
 # DB_PASSWORD=...
 # REDIS_PASSWORD=...
 # JUDGE_SERVER_TOKEN=...
 # OPENAI_API_KEY=...
 # EMBEDDING_API_KEY=...

# 5. 启动
 ./start.sh
```

启动完成后默认访问地址：

- 前端主页：`http://127.0.0.1:8080`
- 后端接口：`http://127.0.0.1:8081`
- API 文档：`http://127.0.0.1:8081/api/docs`

`start.sh` 会完成以下工作：

- 启动 PostgreSQL 与 Redis 容器
- 本地启动 Spring Boot 后端
- 启动 Judge Server 容器并等待心跳注册成功
- 启动 Vue 3 前端开发服务器

**方式三：手动分步启动（适合调试）**

```bash
# 1. 启动基础设施
 docker compose -f deploy/docker-compose.yml up -d postgres redis

# 2. 启动后端
 cd backend
 set -a
 source .env
 set +a
 mvn -q spring-boot:run -Dmaven.test.skip=true \
   -Dspring-boot.run.profiles=dev \
   -Dspring-boot.run.arguments="--server.port=8081"
```

另开终端启动 Judge Server：

```bash
cd /path/to/Alethicode
docker run -d \
  --name java-oj-judge-local \
  --restart unless-stopped \
  --add-host=host.docker.internal:host-gateway \
  -p 12358:8080 \
  -v $(pwd)/deploy/data/test_case:/test_case:ro \
  -v $(pwd)/deploy/data/judge_server/log:/log \
  -v $(pwd)/deploy/data/judge_server/run:/judger \
  -e "SERVICE_URL=http://127.0.0.1:12358" \
  -e "BACKEND_URL=http://host.docker.internal:8081/api/judge-server-heartbeat/" \
  -e "TOKEN=$JUDGE_SERVER_TOKEN" \
  registry.cn-hongkong.aliyuncs.com/oj-image/judge:1.6.1
```

再开一个终端启动前端：

```bash
cd /path/to/Alethicode
cd frontend
npm ci
PORT=8080 API_TARGET=http://127.0.0.1:8081 npm run dev
```

### 4. 配置完成后的验证方式

- 访问 `http://127.0.0.1:8080`，应能打开系统首页与登录页
- 访问 `http://127.0.0.1:8081/api/website`，应返回站点配置 JSON
- 访问 `http://127.0.0.1:8081/api/docs`，应显示 Swagger UI
- 后台可查看 Judge Server 状态，题目提交后可获得判题结果

---

## 超链接

### 1. 作品展示演示视频

> 【待填写】上传演示视频后填入链接。

### 2. Web 作品在线访问地址

> 【待填写】部署完成后填入地址；若评审使用本地运行方式，可在答辩材料中说明推荐采用 `./start.sh` 启动。
