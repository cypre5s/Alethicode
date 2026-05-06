# 上游 fork 出处与同步规范

`services/judge-server/` 由两份上游源码 fork 落库而成。本文档记录 fork 出处、
license 与同步上游安全补丁的 SOP。

## fork 出处

| 子目录 | 上游仓库 | 上游分支 | fork 时 commit |
| --- | --- | --- | --- |
| `judge_server/` | <https://github.com/QingdaoU/JudgeServer> | `master` | `b28aa56d60fed7358a29d9bdeb9d86fcc06e41a7`（2024-04-05） |
| `judger/` | <https://github.com/QingdaoU/Judger> | `newnew` | `d19a6dc192ebd7f41fed44ae6e091575ef22906a`（2024-01-28） |

fork 的目录结构与上游对齐：

- `judger/CMakeLists.txt`、`judger/src/`、`judger/bindings/Python/` 与上游 `Judger/newnew` 完全一致；C 源码 + binding 不修改。
- `judge_server/server.py` 等 Python 文件与上游 `JudgeServer/master` 的 `server/` 目录一致。包名由上游裸 `server/` 改为 `judge_server/`，仅是项目内 Python 包命名规范（snake_case）所要求的重命名，文件内仍然采用扁平 import（保持运行时与上游 Docker 镜像一致）。

## License

两个上游仓库均采用 [SATA (Star And Thank Author License)](https://github.com/zTrix/sata-license)，license 全文已随源码一同复制保留：

- `services/judge-server/judger/LICENSE`

按 SATA 要求，fork 必须在 license 文件中保留原版权声明与项目 URL；本仓库严格遵守。Alethicode 在使用本判题机时已 star 上游仓库并致谢。

## 同步上游安全补丁的 SOP

判题机是一个直接执行选手代码的安全敏感组件，必须建立"上游安全补丁能在合理时间内合入"的运维流程。

### 季度同步检查（建议每季度一次）

```bash
cd /tmp
git clone --depth 1 https://github.com/QingdaoU/JudgeServer.git
git clone --depth 1 -b newnew https://github.com/QingdaoU/Judger.git

# 对照本表中的「fork 时 commit」与上游 HEAD 的差异
diff -ru /tmp/JudgeServer/server/ services/judge-server/judge_server/
diff -ru /tmp/Judger/                services/judge-server/judger/
```

### 安全补丁优先级

| 类型 | 行动 |
| --- | --- |
| CVE / 沙箱逃逸 | 24h 内合入并打 patch tag |
| seccomp / 编译器版本升级 | 1 周内评估 + 合入 |
| 文档 / 注释类修改 | 跳过，不入项目 |
| 新功能 | 评估是否与本仓库 Phase 1+ 自研能力冲突，冲突时本仓库优先 |

### 合入方式

- **简单 cherry-pick**：直接复制上游 commit diff 到对应文件，commit message 注明 `chore(judge-server): sync upstream <commit>`。
- **冲突处理**：若上游补丁与本仓库 Phase 1+ 自研能力（worker pool / diagnosis / explain 等）冲突，先解决冲突，再在 `docs/release-notes.md` 记录手工合入决策。
- **不引入 git submodule**：本仓库决定 fork 落库（plan 已确认），不再以 git submodule 形式跟踪上游。

### 合入后必须做

1. 更新本文件的"fork 时 commit"列。
2. 在 `CHANGELOG.md` 记录中文条目，注明合入的上游 commit + 影响面。
3. 重跑 `make image` 与回归样本集，确认与原镜像行为一致。

## 不做事项

- 不在本仓库重写 Judger C 内核（保留为最小信任内核）。
- 不引入 git submodule（fork 落库即所有权迁移到本仓库）。
- 不让 fork 与上游永久分叉（季度对齐机制保证安全补丁不丢）。
