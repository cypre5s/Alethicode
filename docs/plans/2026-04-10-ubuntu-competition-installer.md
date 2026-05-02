# Ubuntu Competition Installer Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 为 Ubuntu/WSL2 评审环境生成 Alethicode 的 `.run` 可执行安装包，并提供安装后的一键启动、停止、状态查看与冒烟验证能力。

**Architecture:** 继续以 `deploy/docker-compose.yml` 为运行核心，在打包阶段复制运行所需目录形成 payload，再用自解压 shell 包装成单文件 `.run` 安装器。运行时脚本只负责环境校验、可选离线镜像加载和 compose 生命周期控制，不引入新的服务编排层。

**Tech Stack:** Bash、tar、Docker Compose、现有 Spring Boot backend、现有 Vue frontend、现有 `scripts/m12/m12_smoke.sh`

---

### Task 1: 写 installer 验收脚本

**Files:**
- Create: `scripts/competition/test_competition_installer.sh`

**Step 1: Write the failing test**

写一个 shell 验收脚本，约束以下行为：
- `scripts/competition/build_competition_installer.sh` 能生成 `Alethicode-Installer.run`
- `.run` 执行后能解出 `alethicode_competition/`
- `bin/start.sh`、`bin/stop.sh`、`bin/status.sh`、`bin/smoke.sh` 都存在且可执行
- `bin/start.sh --help` 能输出帮助说明

**Step 2: Run test to verify it fails**

Run: `bash scripts/competition/test_competition_installer.sh`

Expected: FAIL，因为构建脚本和运行时脚本尚不存在。

**Step 3: Commit**

先不提交，继续实现直到红绿闭环完成。

### Task 2: 实现安装器构建脚本

**Files:**
- Create: `scripts/competition/build_competition_installer.sh`

**Step 1: Write minimal implementation**

实现以下最小能力：
- 创建 `release/competition_installer/`
- 复制 payload 所需目录与文件
- 生成 payload tar.gz
- 生成自解压 `.run` 安装器

**Step 2: Run focused test**

Run: `bash scripts/competition/test_competition_installer.sh`

Expected: 仍可能 FAIL，但失败点应从“缺文件”前进到更具体的 payload 或安装流程问题。

### Task 3: 实现安装后运行脚本

**Files:**
- Create: `packaging/competition_installer/README.md`
- Create: `packaging/competition_installer/post_install.sh`
- Create: `packaging/competition_installer/start.sh`
- Create: `packaging/competition_installer/stop.sh`
- Create: `packaging/competition_installer/status.sh`
- Create: `packaging/competition_installer/smoke.sh`

**Step 1: Write minimal implementation**

完成以下职责：
- `post_install.sh`：补执行权限
- `start.sh`：help、依赖校验、`.env` 初始化、可选镜像加载、compose 启动
- `stop.sh`：compose down
- `status.sh`：compose ps
- `smoke.sh`：转调 `project/scripts/m12/m12_smoke.sh`
- `README.md`：写明安装后入口与默认访问地址

**Step 2: Run focused test**

Run: `bash scripts/competition/test_competition_installer.sh`

Expected: PASS

### Task 4: 对齐独立部署环境变量

**Files:**
- Modify: `deploy/.env.example`
- Modify: `deploy/docker-compose.yml`

**Step 1: Write the failing expectation**

通过 installer 设计约束，要求：
- 默认 `.env.example` 可直接复制使用
- deploy backend 容器能收到 AI 相关环境变量

**Step 2: Write minimal implementation**

在 `.env.example` 中补默认本地 secrets 和可选 AI 变量；在 compose backend service 中补环境透传。

**Step 3: Run focused verification**

Run: `bash scripts/competition/test_competition_installer.sh`

Expected: PASS，且安装后的 payload 中 `project/deploy/.env.example` 可直接用作首次启动模板。

### Task 5: 更新交付文档

**Files:**
- Modify: `deploy/README.md`
- Modify: `docs/competition/project-factsheet.md`

**Step 1: Write minimal implementation**

补充 Ubuntu/WSL2 `.run` 安装包的构建与使用说明，确保比赛文档中的“安装说明”与实际交付一致。

**Step 2: Run focused verification**

Run: `bash scripts/competition/test_competition_installer.sh`

Expected: PASS

### Task 6: 最终验证、审查、变更记录

**Files:**
- Modify: `CHANGELOG.md`

**Step 1: Run installer test**

Run: `bash scripts/competition/test_competition_installer.sh`

Expected: PASS

**Step 2: Run syntax verification**

Run: `bash -n scripts/competition/build_competition_installer.sh scripts/competition/test_competition_installer.sh packaging/competition_installer/*.sh`

Expected: PASS

**Step 3: Review changed files**

按 `code-reviewer` 规则检查安全性、正确性、可维护性和测试缺口。

**Step 4: Update changelog**

在 `CHANGELOG.md` 追加中文变更记录，说明新增 Ubuntu/WSL2 比赛安装包构建链路与运行脚本。
