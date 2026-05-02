# NFK Training Dataset Contract

> 本文档固化 NFK（Next-Frame Knowledge / 学情建模）训练数据集的字段契约。
> 后端导出器 `backend/.../NfkDataExportService` 与离线训练管线 `research/nfk/` 共同遵守此契约。

## 背景

NFK 训练管线由两侧组成：

- **Java 导出端**：`backend/src/main/java/com/alethicode/service/nfk/NfkDataExportService` 通过 SQL 聚合 `submission` × `ai_problem_kc_mapping` 流式输出 CSV。
- **Python 训练端**：`research/nfk/data/preprocessor.py`（待实现）消费 CSV，喂给 ONNX 训练流程。

历史上字段契约只写在 Java Javadoc 与 README 散点，**没有机器可读的 schema**。一旦任何一侧改字段类型 / 增删列 / 改时间格式，drift 要等到训练开 batch 时才会暴露。R-02 把这份契约提升为一份单一可机读 schema 文件，两侧在数据离开 / 进入边界时都做行级 fail-fast 校验。

## 契约文件

```
contracts/nfk/
├── README.md                         # 本文档
└── training_dataset.schema.json      # JSON Schema 2020-12，行级 schema
```

`training_dataset.schema.json` 是 **single source of truth**：

- Java 侧通过 Maven `<resources>` 在 `pom.xml` 把 `${project.basedir}/../contracts` 映射到 classpath `/contracts/`，运行时 `getResourceAsStream("/contracts/nfk/training_dataset.schema.json")` 加载，使用 `com.networknt:json-schema-validator` 校验。
- Python 侧在 `research/nfk/data/contract_validator.py` 通过父目录搜索 `contracts/nfk/training_dataset.schema.json` 加载（与 `services/tutor-graph/app/paths.py` 同模式），使用 `jsonschema` 包校验。

## CSV 格式

文件头**固定**为：

```
user_id,question_id,skill_id,response,timestamp
```

每一行后续数据严格满足 `training_dataset.schema.json` 描述的 5 字段对象（CSV → 行级 dict 后校验）。导出端在写入磁盘前校验，训练端在喂给模型前校验。

## 字段语义

| 字段 | 类型 | 约束 | 来源 |
|---|---|---|---|
| `user_id` | integer | `≥ 1` | `submission.user_id`（SQL 过滤 `> 0`） |
| `question_id` | integer | `≥ 1` | `submission.problem_id` |
| `skill_id` | integer | `≥ 1` | `ai_problem_kc_mapping.kc_id`（同 `problem_id` 的多 KC 按 `weight DESC, kc_id ASC` 取主 KC） |
| `response` | integer | `∈ {0, 1}` | `submission.result == 0 ? 1 : 0`（AC = 1，其它 = 0） |
| `timestamp` | string | ISO-8601 UTC，正则 `^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z$` | `submission.create_time`（Java 用 `Instant.toString()` 序列化） |

## 时间戳格式（强制约束）

历史 Java 实现走 `java.sql.Timestamp.toString()`，输出形如 `"2026-04-10 18:00:00.0"`：

- 用空格而非 `T` 分隔（非 ISO-8601）
- 受 JVM 默认时区影响（同一 UTC 时间在 UTC+8 与 UTC 上 print 不同）
- 不带时区后缀（消费端无法判断是 UTC 还是本地时间）

R-02 把契约统一到 `Instant.toString()` 输出格式：始终 UTC、`T` 分隔、`Z` 后缀。Java 侧改用 `Instant` 序列化，Python 侧用相同正则校验。任一侧违反契约（如 Java 误退化回 `Timestamp.toString()`，或 Python 误吃了带本地偏移的输入）都立即在 schema 校验阶段 fail-fast。

## 校验语义

- **行级 fail-fast**：任一行不满足 schema，导出 / 训练流程立刻抛异常并报告该行的 1-based 行号 + 第一条违规消息，不容忍部分 partial output。
- **CSV 表头校验**：导出端 / 训练端都检查首行恰好是 `user_id,question_id,skill_id,response,timestamp`，否则也 fail-fast。
- **额外字段拒绝**：schema `additionalProperties: false`，任何未列出的字段直接拒绝。

## 演化协议

任何字段增删 / 类型修改 / 时间格式调整：

1. PR 必须同时改 `training_dataset.schema.json` + Java 侧导出代码 + Python 侧消费代码 + ADR / CHANGELOG，不允许只改一侧。
2. CI gate 跑 round-trip 测试：Java 写一组样例行 → Python 读同样的样例 + schema 校验 → 必须双边都 pass。
3. 历史 CSV 文件在 disk 上的迁移不在本契约范围内，由训练侧选择「重新跑导出」或「写迁移脚本批量改格式」。

## 关联资料

- ADR-0007：[NFK 训练数据契约](../../docs/adr/0007-nfk-training-data-contract.md)
- 后端导出器：`backend/src/main/java/com/alethicode/service/nfk/NfkDataExportService.java`
- 后端校验器：`backend/src/main/java/com/alethicode/service/nfk/NfkTrainingRowValidator.java`
- Python 校验器：`research/nfk/data/contract_validator.py`
