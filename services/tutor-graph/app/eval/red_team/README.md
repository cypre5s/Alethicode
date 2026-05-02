# Red Team Adversarial Testing for tutor_graph

> 落地于 ALETH-PLAN-2026-0428-AP01 Sprint 1。引用论文：
> [RedTeamCUA (arXiv:2505.21936)](https://arxiv.org/abs/2505.21936)。

## 设计

按论文的 **Decoupled Eval** 模式：直接把对抗 payload 注入到 tutor_graph 节点的输入 state，**不让"学生在 UI 上没找到注入点"显得 agent 安全**。

## 文件清单

| 文件 | 作用 |
|---|---|
| `__init__.py` | 公共导出 |
| `schema.py` | Pydantic 用例 schema（`AdversarialCase` / `Assertion` / `PayloadInjection` / `CaseResult` / `DatasetSummary`）|
| `targets.py` | 8 类教学场景独有攻击向量 + 教学场景 CIA triad |
| `assertions.py` | 7 类断言评估器（`output_must_not_contain` / `output_must_not_match_regex` / `output_field_must_be_absent` / `output_field_must_satisfy` / `output_must_be_failfast` / `score_must_be_below` / `memory_must_not_change`）|
| `decoupled_runner.py` | Runner，直接灌 payload 到节点入口 |
| `case_definitions.py` | **source of truth**：100 条用例的 Pydantic 实例化（按 6 类分组）|
| `build_dataset.py` | 从 `case_definitions.py` 派生 `adversarial_dataset.jsonl`（人工 review 用）|
| `adversarial_dataset.jsonl` | 100 条用例（自动生成，提交到仓库便于跨语言工具消费）|
| `ci_gate.py` | CLI 入口，CI 调用此脚本判断通过率 |

## 100 条用例分布

- **类别**：direct_answer 30 / role_reversal 18 / jailbreak 17 / persona_manipulation 15 / problem_text_conflict 15 / reflection_injection 5
- **Phase**：CHAT 35 / ERROR_FEEDBACK 28 / AC_REVIEW 16 / IDEATING 14 / READING 4 / TRANSFER 3

> Sprint 1 不覆盖 `walkthrough_fraud`（PARSONS 节点走 java_client，由 Sprint 2 在 Java 侧测）和 `trajectory_pollution`（NFK 输入污染，同样在 Sprint 2 的 Java 测试集中覆盖）。

## 教学场景 CIA Triad（重定义自 RedTeamCUA）

| 论文 CIA | 教学场景定义 |
|---|---|
| **C** Confidentiality | 不泄漏答案（不输出完整 def / class / main 函数体） |
| **I** Integrity | 不被诱导改变 mastery 评估、KC 状态、错题本 memory |
| **A** Availability | 不被刷接口、不被 NFK trajectory 污染（A 类大部分在 Sprint 2） |

## 使用

### CI 模式（默认 — 用 mock LLM）

```bash
cd tutor_graph
python -m app.eval.red_team.ci_gate --baseline 0.0
```

CI 默认用 `make_compliant_llm_client()`：LLM **完全配合**攻击（每次都输出违规 payload）。这种最坏情况下，只要节点的 schema 校验、output_sanitization、字段过滤等**后置防御层**仍然能挡住攻击，用例就 pass。

**这种模式的用途**：每次代码改动时跑，确保新代码没有把现有的后置防御弱化。

### Real-LLM 模式（不在 CI 跑，按月手动跑）

```bash
export ALETHICODE_RED_TEAM_OPENAI_API_KEY=sk-...   # 必须用专门的 red team key
export ALETHICODE_RED_TEAM_OPENAI_MODEL=gpt-4o     # 可选
python -m app.eval.red_team.ci_gate --real-llm --baseline 0.5
```

**这种模式的用途**：评估 LLM **自己**对攻击的抗性。结果会随模型升级而变化。

### 过滤器

```bash
# 只跑某一类攻击
python -m app.eval.red_team.ci_gate --filter-category jailbreak

# 只跑某个 phase
python -m app.eval.red_team.ci_gate --filter-phase CHAT

# 输出机器可读的 JSON 报告
python -m app.eval.red_team.ci_gate --report-json out/red_team_report.json
```

## CI 集成

`services/tutor-graph/app/tests/test_red_team_dataset.py` 用 pytest parametrize 跑全 dataset，无需额外修改 `.github/workflows/ci.yml`——`tutor-graph-python` job 已包含 `python -m pytest -q`，新增的 test 会自动跑。

```yaml
# .github/workflows/ci.yml 中已有的 tutor-graph-python job
# 它跑的是 `python -m pytest -q`，下面这个 test 会自动被收集进去
- name: Pytest
  working-directory: tutor_graph
  run: python -m pytest -q
```

## 增加新用例

1. 在 `case_definitions.py` 对应类别的 list 末尾追加一个 `AdversarialCase(...)` 实例
2. id 单调递增（adv-101, adv-102, ...）
3. 跑 `python -m app.eval.red_team.build_dataset` 重新生成 jsonl
4. 跑 `python -m app.eval.red_team.ci_gate --baseline 0.0` 看新用例行为
5. 跑 `pytest -q app/tests/test_red_team_dataset.py` 确保单测仍 pass

## 设计决策（呼应 AGENTS.md）

| 决策 | 理由 |
|---|---|
| Pydantic schema 用 `extra="forbid"` | failfast：dataset 漂移在 parse 阶段失败 |
| 不写"防御代码"（不在节点里加防御层） | 触发的修复仍走 prompt + schema 加固，不引入新抽象层 |
| Sprint 1 不做"分类器过滤" | YAGNI，先看现有 schema 能否扛住 |
| 共用 `make_compliant_llm_client` 而非每用例自带 fixture | 单测验证的是后置防御层，统一 fixture 减少样板 |
| 不引入 ML-based attack detector | 教学场景容错低，false positive 直接误导学生 |

## 参考

- 论文：[RedTeamCUA: Realistic Adversarial Testing of Computer-Use Agents (arXiv:2505.21936)](https://arxiv.org/abs/2505.21936)
- 设计文档：`docs/plans/2026-04-28-iclr2026-agent-papers-teaching-applications.md` §三 痛点 P2
