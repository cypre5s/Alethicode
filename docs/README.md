# Docs Index

`docs/` 现在按“文档类型”而不是“临时想到就丢进来”来组织。

## 目录说明

| 目录 | 用途 |
|---|---|
| `overview/` | 项目愿景、故事、对外总体介绍 |
| `architecture/` | 架构说明、工作流、状态机与核心设计脉络 |
| `specs/` | 正式说明书、功能规格、约束规则 |
| `plans/` | 分阶段实施方案与落地计划 |
| `adr/` | 架构决策记录（ADR） |
| `reports/` | 评审、诊断、基线与专项分析报告 |
| `guides/` | 操作手册、迁移指南、测试手册 |
| `competition/` | 比赛材料、路演文案、PPT 相关资产 |
| `todos/` | 待办、技术债、路线拆解与执行清单 |
| `worklog/` | 过程记录、实验日志、阶段性工作纪要 |
| `baseline/` | 基线快照与文本导出结果 |
| `security/` | 安全专题文档 |
| `sre/` | SLO、运行稳定性与运维侧文档 |
| `assets/` | 文档配图与静态素材 |
| `archives/` | 归档包，不参与日常阅读 |

## 命名规则

- 目录名统一使用小写 `kebab-case`。
- Markdown 文件统一使用小写 `kebab-case`。
- 时间敏感文档使用 `YYYY-MM-DD-topic.md`。
- 待办文档统一使用 `todo-*.md`。
- 不再往 `docs/` 根目录直接堆新文档，先判断归属目录。

## 阅读入口

- 项目总览：[`overview/project-story.md`](./overview/project-story.md)
- 核心架构：[`architecture/agent-architecture-workflow.md`](./architecture/agent-architecture-workflow.md)
- 正式规格：[`specs/project-design-spec-zh.md`](./specs/project-design-spec-zh.md)
- 执行计划：[`plans/`](./plans/)
- 架构决策：[`adr/README.md`](./adr/README.md)
- 运行与评审报告：[`reports/`](./reports/)
- 比赛材料：[`competition/`](./competition/)
- 待办入口：[`todos/todo-master.md`](./todos/todo-master.md)

## 维护约束

- `competition/ppt/` 下的脚本、PPT 和依赖产物视为比赛材料工作区。
- `archives/` 只放归档，不在其他文档里当作主阅读入口引用。
- 重命名文档时，必须同步更新引用路径和 `CHANGELOG.md`。
