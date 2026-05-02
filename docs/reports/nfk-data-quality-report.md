# NFK 数据质量诊断报告（Phase A）

> 本报告对应 `todos-three-remaining` 阶段 3.1 的 A1-A4 SQL 诊断，用于在接入
> `NfkInferenceService` 前体检每个语言包的数据可用性。
>
> SQL 来源：`backend/src/main/resources/db/diagnostics/nfk_data_quality_diagnostics.sql`
>
> 执行方式：
> 1. 在生产/演示数据库 psql 下逐段执行；
> 2. 把结果粘贴到对应章节的「实测结果」表；
> 3. 在「结论」写出是否进入阶段 3.2（可通过 / 需先修数据）。

## 读数约定

- 覆盖率以 `covered_problems / total_problems` 表示；
- 时间跨度以秒计算，> 1 小时视为"真实做题轨迹"；
- KC 映射 1:N 分布里 N 表示一道题绑定的 KC 数量；
- AC 判定统一以 `submission.result = 0` 为准。

## A1 题目 KC 覆盖率

**目的**：覆盖率太低意味着 NFK 的 `skill_id` 序列稀疏，训练质量不保。推荐阈值：
- ≥ 0.70 → HOT，可直接进入 NFK；
- 0.40 ~ 0.70 → WARM，可跑但需关注退化；
- < 0.40 → COLD，建议先补 KC 映射。

**实测结果**（运行时补充）：

| language_pack_id | name | total_problems | covered_problems | coverage | 判定 |
| --- | --- | --- | --- | --- | --- |
| _待填_ | _待填_ | _待填_ | _待填_ | _待填_ | _待填_ |

**结论**：_待填写_

## A2 `submission.result` 枚举分布

**目的**：核对项目约定 `result = 0` 为 AC。如果 result 分布里 0 并非最大或主要类别，需要
进一步确认 ResultEnum 的实际语义（可能与代码里 ResultEnum 不一致）。

**实测结果**（运行时补充）：

| result | cnt | ratio | 含义 |
| --- | --- | --- | --- |
| _待填_ | _待填_ | _待填_ | _待填_ |

**结论**：_待填写_

## A3 同学生 submission `create_time` 抽样

**目的**：如果某学生的所有 submission 集中在几分钟内，且 duplicate_ts > 0，说明是批量
导入的脏数据，NFK 的时间间隔特征 `delta_t` 会被污染。

**实测结果**（运行时补充）：

| user_id | samples | first_ts | last_ts | span_seconds | distinct_ts | duplicate_ts |
| --- | --- | --- | --- | --- | --- | --- |
| _待填_ | _待填_ | _待填_ | _待填_ | _待填_ | _待填_ | _待填_ |

**结论**：_待填写_

## A4 `ai_problem_kc_mapping` 1:N 分布

**目的**：决定在阶段 3.2 的 `NfkDataExportService` 里是否需要按 `weight DESC` 取主 KC。

经验判断：
- 若 kc_count = 1 占比 ≥ 80% → 可以直接 JOIN 取所有 KC；
- 若 kc_count ≥ 2 占比 ≥ 20% → **必须**按 `weight DESC LIMIT 1` 取主 KC，否则同一题会被拆成多条样本。

**实测结果**（运行时补充）：

| kc_count | problem_count | ratio |
| --- | --- | --- |
| _待填_ | _待填_ | _待填_ |

**结论**：_待填写_

## 总体验收

| 验收项 | 通过标准 | 实测 | 通过? |
| --- | --- | --- | --- |
| A1 任一 WARM 课程包覆盖率 ≥ 0.4 | 至少一个 pack coverage ≥ 0.4 | _待填_ | _待填_ |
| A2 result=0 比例与日常直觉一致 | 不为 0% 也不 > 95% | _待填_ | _待填_ |
| A3 抽样的 top user span_seconds > 1 小时 且 duplicate_ts = 0 | 真实做题轨迹 | _待填_ | _待填_ |
| A4 主 KC 选择策略已明确 | 按 weight DESC | _待填_ | _待填_ |

## 阶段 3.2 的假设

此处落地的 `NfkDataExportService` 将沿用：

- `submission.result = 0` 作为 NFK 训练集中的 response=1 正样本，其它值为 0；
- `create_time` 是 TIMESTAMPTZ，直接作为 `timestamp` 字段导出；
- 同一 `problem_id` 取 `ai_problem_kc_mapping.weight` 最大的 KC 作为 `skill_id`；
- 仅导出 `language_pack_problem_mapping` 中登记的题；
- CSV 字段顺序：`user_id, question_id, skill_id, response, timestamp`（与 NFK 训练侧对齐）。

如 A1-A4 的实际结果推翻上述任一假设，请在阶段 3.2 动工前更新此文件的「假设调整」段并同步
`NfkDataExportService` 的 SQL 查询。

### 假设调整（空表示没有偏差）

- [ ] _待填_
