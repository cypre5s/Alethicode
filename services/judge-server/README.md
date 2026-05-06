# services/judge-server

Alethicode 判题机源码（项目内 fork）。

判题机包含两层：

- `judge_server/`：Python HTTP 服务（基于 Flask），暴露 `/judge` `/ping` `/compile_spj` 等端点，负责接收业务后端的判题请求、写源码、调编译器、调 `JudgeClient` 跑测试点。
- `judger/`：C 沙箱内核（`libjudger.so` + Python binding `_judger`），负责 `fork/exec` + `setrlimit` + `setuid/setgid` + `seccomp`，是真正执行选手代码的最小信任内核。

## 上游来源

| 子目录 | 上游仓库 | 上游分支 / commit |
| --- | --- | --- |
| `judge_server/` | <https://github.com/QingdaoU/JudgeServer> | `master` / `b28aa56` |
| `judger/` | <https://github.com/QingdaoU/Judger> | `newnew` / `d19a6dc` |

详细的 fork 出处、license 与同步规范见 `docs/UPSTREAM.md`。

## 与平台其他模块的关系

```text
Java backend  ──HTTP /judge──►  judge_server (Flask)  ──Python binding──►  judger (libjudger.so)
                                                                           └─ fork/exec/setrlimit/seccomp ─► 选手程序
```

- 业务后端通过 `deploy/docker-compose.yml` 与 `deploy/helm/alethicode/templates/judge-deployment.yaml` 配置的 `service_url` 调用判题机。
- 心跳通过 `BACKEND_URL=http://backend:8080/api/judge-server-heartbeat/` 反向回报，由 Java 端 [`JudgeServerController`](../../backend/src/main/java/com/alethicode/controller/JudgeServerController.java) 接收。
- 测试用例目录通过容器只读挂载 `/test_case`，对判题机来说是黑盒数据源。

## 开发模式

本地开发 backend 时**不需要**同时拉起本地判题机；`mvn spring-boot:run` 与 `npm run dev` 默认连测试环境或共享的判题容器即可。

需要在本机跑判题机时，按下面任选一种：

### 方式 A：仅启动本机判题容器（推荐）

```bash
make image                                    # 在 services/judge-server/ 内
docker compose -f deploy/docker-compose.judge-only.yml up
```

判题机会监听本机 `12358` 端口（与上游 `docker-compose.example.yml` 一致），用 `JUDGE_SERVER_TOKEN` 环境变量做鉴权。本地 backend 把 `judge.image.repository` 指向同 token 即可。

### 方式 B：用上游官方镜像（默认）

直接 `docker compose -f deploy/docker-compose.yml up judge` —— 跑的是 `registry.cn-hongkong.aliyuncs.com/oj-image/judge:1.6.1`。本仓库的 fork 暂不替换默认镜像（避免本地构建 cmake + libseccomp 的 30+ 分钟成本扩散到所有开发者）。

## Python 版本

- 生产判题镜像内固定 `python3.12`（与上游 Dockerfile 选定一致）。
- 本机开发环境只要求 `python3.10+`（host 烟测在 3.10 / 3.11 / 3.12 上都能跑），低于 3.11 时会自动通过 dev 依赖中的 `tomli` 兜底解析 `configs/default.toml`。

## 命令

```bash
make help          # 列出所有可用目标
make dev           # 启动开发依赖（仅本机环境，无 sandbox 时可用 mock 跑 ping）
make test          # 执行 pytest 单测（host 上能跑的部分；真实判题需要 docker 环境）
make image         # 构建项目内判题机镜像 alethicode/judge-server:dev
make compose-up    # docker compose -f deploy/docker-compose.judge-only.yml up
make compose-down  # docker compose -f deploy/docker-compose.judge-only.yml down -v
make clean         # 清掉 build/ 与 __pycache__/
```

## 目录结构

```text
services/judge-server/
├── README.md                  # 本文件
├── Dockerfile                 # 项目内构建（同时编译 judger C 内核 + 部署 Flask 服务）
├── Makefile                   # 开发便捷命令
├── pyproject.toml             # Python 依赖（与 backend 主链解耦）
├── .gitignore / .dockerignore
├── configs/
│   └── default.toml           # 默认配置（max_workers / AI 端点占位 / 缓存大小，后续 Phase 才生效）
├── judge_server/              # Python 服务源码（fork 自上游 server/）
│   ├── server.py              # Flask app
│   ├── judge_client.py        # 编译 + 多进程跑测试点
│   ├── compiler.py            # gcc / g++ / javac / python3 编译入口
│   ├── config.py              # UID/GID/路径常量
│   ├── exception.py
│   ├── service.py             # 心跳 + healthcheck
│   ├── utils.py               # token 校验 + server_info
│   ├── unbuffer.c             # LD_PRELOAD 强制 stdout 不缓冲
│   └── entrypoint.sh          # gunicorn 启动脚本
├── judger/                    # C 沙箱内核（fork 自上游 Judger/newnew）
│   ├── CMakeLists.txt
│   ├── src/                   # judger.c / runner.c / killer.c / logger.c / rules/*.c
│   ├── bindings/Python/       # _judger Python wrapper（subprocess 调 libjudger.so）
│   └── ...
├── tests/
│   ├── conftest.py
│   ├── test_phase0_smoke.py   # Phase 0 烟测：模块可导入 / 配置可解析
│   └── upstream/              # 上游 sample test cases（仅参考，不参与 CI）
└── docs/
    ├── UPSTREAM.md            # fork 出处 + license + 同步规范
    └── release-notes.md       # 每个 Phase 的能力变更摘要
```

## 路线图（Phase 1 起的能力扩展）

判题机将分阶段获得 AI 教学语义、流式反馈、可观测性、安全过滤等能力，详见 `docs/release-notes.md` 与仓库根目录的实施 plan。

业务端契约保持向后兼容：所有新字段都是 `data[i]` 上的可选 JSON 追加，老 Java 后端无须修改即可通过 `submission.statistic_info` (jsonb) 自动收纳新数据。
