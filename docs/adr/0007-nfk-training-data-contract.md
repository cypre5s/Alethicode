# ADR-0007: NFK 训练数据契约 schema 化

- **Status**: Accepted
- **Date**: 2026-04-29
- **Authors**: Alethicode core team
- **Related**: 优先级表 R-02；`backend/src/main/java/com/alethicode/service/nfk/NfkDataExportService.java`；`research/nfk/`；ADR-0006（Resilience Engineering，本次校验属于 fail-fast 模式之一）

## 背景

NFK（Next-Frame Knowledge / 学情建模）训练管线由两侧构成：

- **Java 导出端**：`NfkDataExportService` 通过 SQL 聚合 `submission × ai_problem_kc_mapping` 流式输出 CSV
- **Python 训练端**：`research/nfk/` 离线训练管线（位于 `research/`，不在生产路径）

历史上字段契约只写在 Java Javadoc + `research/README.md` 散点：

```
user_id, question_id, skill_id, response, timestamp
- user_id / question_id：平台原生 ID
- skill_id：同 problem_id 的多 KC 按 weight DESC 取主 KC
- response：submission.result == 0 ? 1 : 0
- timestamp：submission.create_time 的 ISO-8601 字符串（Javadoc 声称如此）
```

实际 Java 实现走 `Object#toString()`，对 `java.sql.Timestamp` 输出形如 `"2026-04-10 18:00:00.0"`：用空格分隔（非 ISO-8601）、JVM 时区相关、不带时区后缀。**Javadoc 与代码事实从一开始就 drift**。Python 训练侧消费 CSV 时也没有显式校验，任一字段类型变更 / 边界违反要等到 batch 训练时才暴露。

零基础 Python 课程小范围公测后，第一次拉真实生产数据训练 NFK 时，这种 drift 是低优先但不可接受的隐患。

## 决策

把 NFK 训练 CSV 字段契约从 "散点 Javadoc + 隐式约定" 提升为 "**单一可机读 JSON Schema 文件 + 双侧行级 fail-fast 校验**"。

### 1. 单一 source of truth：`contracts/nfk/training_dataset.schema.json`

JSON Schema 2020-12，描述单行训练样本：

| 字段 | 类型 | 约束 |
|---|---|---|
| `user_id` | integer | `≥ 1` |
| `question_id` | integer | `≥ 1` |
| `skill_id` | integer | `≥ 1` |
| `response` | integer | `∈ {0, 1}` |
| `timestamp` | string | ISO-8601 UTC，正则 `^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z$` |

`additionalProperties: false`、`required` 列出全部 5 字段。

CSV 表头单独固化在 `contracts/nfk/README.md` 与 Java/Python 常量 `CSV_HEADER`，导出 / 训练前都做严格相等比较。

### 2. 双侧实现共享 schema（不允许内联副本）

- **Java 侧**：`backend/pom.xml` 通过 `<resources>` 把仓库根 `contracts/nfk/training_dataset.schema.json` 复制到 classpath `/contracts/nfk/`，`NfkTrainingRowValidator` 用 `com.networknt:json-schema-validator` 加载并校验每一行。
- **Python 侧**：`research/nfk/data/contract_validator.py` 通过父目录探测找到 `<repo>/contracts/nfk/training_dataset.schema.json`（与 `services/tutor-graph/app/paths.py` 同模式），用 `jsonschema>=4.21` 的 `Draft202012Validator` 加载并校验。

两侧加载的是**同一份磁盘文件**，schema 字段 / 约束变化时不会 drift；只是各自的 validator 实现不同。

### 3. 时间戳契约统一到 ISO-8601 UTC

`submission.create_time` 的取值始终基于一个 `Instant`，所以契约把字符串形式固化为 `Instant.toString()` 输出：

- 始终 `T` 分隔
- 始终 `Z` 后缀（UTC）
- 小数秒可选 1-9 位（满足 PostgreSQL `TIMESTAMP` 的最高精度）

Java 侧 `NfkDataExportService.canonicalize` 把 JDBC 取到的 `Timestamp / Instant / OffsetDateTime / ZonedDateTime` 全部 `.toInstant().toString()`，本地时区污染被切断。其他类型直接 fail-fast，不容忍意外类型悄悄通过 `Object#toString()`。

### 4. 行级 fail-fast，不容忍部分 partial output

任一行违反 schema：

- Java：抛 `NfkTrainingRowValidationException`，附带 1-based 行号 + schema 第一条违规消息
- Python：抛 `NfkContractError`，附带相同语义的行号 + 消息

不写 `try-catch` 兜底，不做 "跳过非法行" 的部分输出，符合 AGENTS.md "不允许补丁性方案" 与 "failfast" 原则。

### 5. 跨语言 round-trip 锚点 fixture

`contracts/nfk/fixtures/exporter_output_sample.csv` 存放一份 3 行的"正确答案"：

- Java 单元测试 `NfkDataExportServiceTest#exportTrainingDataMatchesRoundTripFixtureByteForByte` 断言导出器输出与 fixture **byte-for-byte 一致**
- Python 单元测试 `test_round_trip_fixture_passes_python_validation` 断言 fixture 通过 `validate_csv` 校验

任何一侧改动 CSV 输出 / 校验规则但未同步另一侧，都会同时打破两个测试，强制契约变更走 PR 双侧改动 + 同时更新 fixture 的流程。

### 6. CI gate

`.github/workflows/ci.yml` 新增 `nfk-contract-python` job：

- 独立于 `tutor-graph-python` 与 `backend-java`
- pip 装 `research/nfk/requirements.txt`（仅 jsonschema + pytest，无重型训练依赖）
- 跑 `python -m pytest tests/test_contract_validator.py -v`
- `security` job 的 `needs` 加上本 job，确保安全扫描前契约已就绪

Java 侧的 `NfkDataExportServiceTest + NfkTrainingRowValidatorTest` 自动随 `backend-java` 已有 unit-test step 跑。

## 替代方案

### A. 让 pydantic v2 class 成为 source of truth

否决理由：与 R-02 "schema 化" 初衷冲突。pydantic 是 model-first（class 定义为真，schema 是导出物），而本次目标是 contract-first（人写的 schema 文件是真）。引入 pydantic 还会让 Python 端多一层间接，且 Java 端无法直接消费 Python class 定义。

### B. 在 Java 与 Python 各自内联 schema 字符串

否决理由：违反"单一 source of truth"。drift 风险靠 review 拦截不可靠，CI gate 也只能事后报警，本质上是补丁式方案。AGENTS.md 明令"不允许补丁性方案"。

### C. 不做契约化，等出问题再修

否决理由：训练数据是离线 batch 工作流，drift 暴露周期 ≥ 数小时，出问题时已经浪费了显卡时间和数据预处理。R-02 的成本（~半天）远低于一次 drift 事故的排查成本。

### D. CSV 头跟着 schema 字段顺序自动排序

否决理由：契约的稳定性高于灵活性。CSV 的字段顺序固定为 `user_id,question_id,skill_id,response,timestamp`，任何重新排序需要走 ADR 升级 + 双侧同步改动，避免历史 CSV 文件突然不兼容。

## 影响

### 行为变更

- **timestamp 格式从本地时区切换到 UTC ISO-8601**：之前导出的 CSV 文件不能直接被新 Python validator 接受。但因为 `research/nfk/data/preprocessor.py` 此前并不存在（即没有真正的下游消费者），无外部历史用户受影响；测试代码已同步更新。
- **NfkDataExportService 构造签名变更**：从 `NfkDataExportService(JdbcTemplate)` 变为 `NfkDataExportService(JdbcTemplate, NfkTrainingRowValidator)`。后者是 `@Component`，Spring 自动注入；只有手写 `new NfkDataExportService(...)` 的场景需要修改（仅 `NfkDataExportServiceTest` 需要，已修）。
- **导出非法数据时不再静默写出**：之前 `Object#toString()` 对任意类型都能产生字符串（哪怕产生的字符串是 nonsense），现在非法行会抛异常。这是改进，不是兼容性损失。

### 演化协议

任何 NFK 字段增删 / 类型修改 / 时间格式调整：

1. PR 必须同时改 `training_dataset.schema.json` + Java 导出代码 + Python 校验代码 + ADR 修订或新 ADR + CHANGELOG，不允许只改一侧
2. CI gate 跑 round-trip：Java byte-for-byte fixture + Python validate_csv fixture 必须双边都 pass
3. 如果 fixture 需要更新，PR 内同时更新 + 双侧测试

### 不在本次范围

- `research/nfk/data/preprocessor.py` 真实数据预处理实现（用 NFK CSV 喂给 PyTorch DataLoader）：本 ADR 仅做契约校验，**消费端**真正的 preprocessor 落地是后续工作。
- 训练管线本身（NFK 模型架构、训练循环、消融实验）：本 ADR 不动训练代码。

## 验证

| 检查 | 命令 | 结果 |
|---|---|---|
| Java 单元测试 | `cd backend && mvn -Dtest='NfkDataExportServiceTest,NfkTrainingRowValidatorTest' test` | 21 测试通过 0 失败 0 错误 |
| Python 单元测试 | `cd research/nfk && python -m pytest tests/test_contract_validator.py -v` | 18 测试通过 0 失败 0 错误 |
| Round-trip fixture | Java byte-for-byte 比对 + Python validate_csv | 双侧 pass，同一份 fixture |
| Maven 资源装配 | classpath `/contracts/nfk/training_dataset.schema.json` 与 `/contracts/nfk/fixtures/exporter_output_sample.csv` 可被 Java 加载 | ✅ |
| Schema 自身合法性 | `Draft202012Validator.check_schema(schema)` 在 Python `_load_validator()` 中显式调用 | ✅ |
