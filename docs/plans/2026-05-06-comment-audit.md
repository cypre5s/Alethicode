# 注释优化审计计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 按 `AGENTS.md` 统一项目注释，只保留主要注释，并确保注释文本为中文。

**Architecture:** 以所有可读文本文件为审计对象，覆盖源码、配置、脚本、SQL、文档、部署和研究资料，排除依赖、构建产物、运行时数据、密钥和二进制文件。每批先扫描候选文件，再做最小注释修改，并同步 `COMMENT_AUDIT.md`。

**Tech Stack:** Java 21 / Vue 3 + Vite / Python FastAPI / Shell / Docker Compose / Helm。

---

## 执行范围

- 包含：仓库内所有自研可读文本文件，重点覆盖 `backend`、`frontend`、`services`、`contracts`、`docs`、`deploy`、`scripts`、`tools`、`research`、`nfk` 和根目录配置/文档。
- 排除：`node_modules`、`target`、`dist`、`.venv`、`__pycache__`、运行时数据、密钥、图片、压缩包和数据库 dump。

## 批次与验证

1. 扫描候选文件。
   验证：`rg` 命令不再命中运行时数据权限错误。
2. 清理前端与脚本中明显冗余或英文注释。
   验证：相关文件语法未改变，必要时运行前端 typecheck。
3. 清理 Python 微服务中公开 docstring 和关键设计注释。
   验证：运行对应 Python 语法编译或测试。
4. 清理 Java 后端中公开 Javadoc 和关键设计注释。
   验证：运行 Maven compile 或至少按改动范围编译。
5. 清理文档、部署、研究资料和根目录配置中的注释。
   验证：仅改注释文本，不改变命令、配置键、代码块逻辑或文档正文语义。
6. 更新 `COMMENT_AUDIT.md` 和 `CHANGELOG.md`。
   验证：审计记录与 `git diff --name-only` 对齐。
