# Ubuntu Competition Installer Design

**Goal:** 为 Alethicode 提供一个面向 Ubuntu/WSL2 评审环境的可执行安装包交付方案，输出单个 `.run` 文件，安装后通过 Docker Compose 启动完整 Web 系统。

**Constraints:**
- 不涉及 `app/` 移动端。
- 不改成桌面客户端，不伪装成单进程 `exe`。
- 交付物必须符合“可执行文件或者安装包”的比赛口径。
- 默认围绕现有 `deploy/docker-compose.yml` 收口，避免重构业务架构。
- 安装流程尽量不依赖 `sudo`，默认安装到用户目录。

## Chosen Approach

选择自解压 `.run` 安装包。

理由：
- 对 Ubuntu/WSL2 来说，`.run` 本身就是可执行文件，符合比赛提交口径。
- 不需要为 `.deb` 额外维护系统级卸载脚本、包依赖元数据和 root 安装路径。
- 可以直接复用仓库现有的 Docker Compose 部署链路，只把运行所需文件打进 payload。
- 能兼容两种启动模式：
  - 在线构建：安装后直接 `docker compose up -d --build`
  - 离线镜像：打包阶段可选导出镜像 tar，安装后优先 `docker load`

## Payload Layout

安装器解压后的目录固定为 `alethicode_competition/`，内部结构如下：

```text
alethicode_competition/
├── README.md
├── bin/
│   ├── start.sh
│   ├── stop.sh
│   ├── status.sh
│   └── smoke.sh
├── installer/
│   └── post_install.sh
├── offline_images/
│   └── alethicode-images.tar    # 可选
└── project/
    ├── backend/
    ├── frontend/
    ├── deploy/
    └── scripts/
```

`project/` 保持 `backend + frontend + deploy` 的原始相对位置，这样 `deploy/docker-compose.yml` 的 build context 不需要改写。

## Install Flow

`.run` 安装器负责：

1. 解析目标安装目录，默认写入 `~/.local/share/alethicode-competition`。
2. 若目标目录非空则直接失败，避免覆盖未知现场。
3. 解出 payload 到目标目录。
4. 调用 `installer/post_install.sh` 统一补执行权限。
5. 输出后续操作提示：
   - `bin/start.sh`
   - `bin/status.sh`
   - `bin/smoke.sh`
   - `bin/stop.sh`

## Runtime Flow

`bin/start.sh` 负责：

1. 校验 `docker` 与 `docker compose` 可用。
2. 若 `project/deploy/.env` 不存在，则从 `.env.example` 复制生成。
3. 校验 `DB_PASSWORD`、`REDIS_PASSWORD`、`JUDGE_SERVER_TOKEN` 非空。
4. 若存在 `offline_images/alethicode-images.tar`，先执行 `docker load`。
5. 根据是否存在离线镜像选择：
   - 有离线镜像：`docker compose up -d`
   - 无离线镜像：`docker compose up -d --build`
6. 输出访问地址 `http://127.0.0.1:18080`。
7. 在 WSL2 且存在 `wslview` 时，尝试自动打开浏览器。

`bin/stop.sh` 负责 `docker compose down`。

`bin/status.sh` 负责 `docker compose ps`。

`bin/smoke.sh` 复用现有 `project/scripts/m12/m12_smoke.sh` 做 HTTP 冒烟验证。

## Env Strategy

为保证安装后可直接启动，`project/deploy/.env.example` 需要从“只有空值模板”调整为“可本地运行的默认值 + 可选 AI 配置”：

- 必填并提供默认值：
  - `DB_PASSWORD`
  - `REDIS_PASSWORD`
  - `JUDGE_SERVER_TOKEN`
- 可选并保持空值/默认值：
  - `OPENAI_API_KEY`
  - `EMBEDDING_API_KEY`
  - `EMBEDDING_BASE_URL`
  - `EMBEDDING_MODEL`
  - `LLM_MODEL`
  - `LLM_BASE_URL`
  - `LLM_API_TIMEOUT_SECONDS`
  - `LLM_API_MAX_RETRIES`
  - `VIDEO_TTS_PROVIDER`
  - `VIDEO_RENDER_PROVIDER`

同时 `deploy/docker-compose.yml` 需要把这些 AI 相关变量透传给 backend 容器，保证安装包部署链路和日常独立部署链路行为一致。

## Build Flow

新增构建脚本 `scripts/competition/build_competition_installer.sh`：

1. 清理并创建 `release/competition_installer/`。
2. 按 payload layout 复制运行所需文件。
3. 可选执行离线镜像导出。
4. 生成 payload tar.gz。
5. 生成自解压 `.run` 安装器。
6. 输出产物路径与使用方式。

## Verification

最小验证闭环：

1. 运行 `scripts/competition/test_competition_installer.sh`
2. 该脚本应：
   - 调用构建脚本生成 `.run`
   - 执行 `.run` 完成安装
   - 断言 `bin/start.sh`、`bin/stop.sh`、`bin/status.sh`、`bin/smoke.sh` 存在且可执行
   - 校验 `start.sh --help` 可输出使用说明

另补静态验证：

- `bash -n` 检查新增 shell 脚本语法
- 复用 `scripts/m12/m12_smoke.sh` 验证运行时探活入口仍然一致
