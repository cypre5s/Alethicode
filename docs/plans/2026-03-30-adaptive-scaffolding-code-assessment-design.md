# 自适应渐退脚手架与多维代码评估设计

## 目标

在不新增业务主入口的前提下，完成两条闭环能力：

1. 在现有 OJ AI workflow 的 `SCAFFOLDING` 阶段内，基于题目关联 KC 的最低 mastery 动态输出四档脚手架：`worked_example / faded_example / parsons_problem / minimal_hint`。
2. 在现有判题结果回写链路内，为 `Python3` 提交补充多维代码评估：
   - 非 AC：写入 `partial_score`
   - AC：异步写入 `code_quality`

## 约束

- 不新增 `Phase`，仍只使用现有 `SCAFFOLDING`
- 不新增独立脚手架 API，`Faded Example` 填空验证继续走 `/api/ai/workflow/event`
- 不为代码评估新增新表，继续写入 `submission.statistic_info`
- `Python3` 之外语言本轮不做代码质量评估
- fail-fast：LLM 输出不满足 schema 或评分范围时直接失败，不做兜底文案

## 后端设计

### 1. 自适应渐退脚手架

- 在 `CardType` 中新增：
  - `WORKED_EXAMPLE`
  - `FADED_EXAMPLE`
  - `MINIMAL_HINT`
- 在 `CardSchemaRegistry` / `CardSchemaValidator` 中增加三类卡片 schema
- 新建 `ScaffoldLevelResolver`
  - 输入：`masteryByKc`
  - 聚合：取最小 mastery
  - 阈值：
    - `< 0.4 -> FULL`
    - `< 0.55 -> FADED`
    - `< 0.7 -> PARSONS`
    - `>= 0.7 -> MINIMAL`
- 新建三个 generator：
  - `WorkedExampleGenerator`
  - `FadedExampleGenerator`
  - `MinimalHintGenerator`
- `AITutorWorkflowAdminServiceImpl.applyPhaseOutput(... event=SCAFFOLDING ...)` 中改为：
  - 首次进入脚手架：按 resolver 选择生成器/现有 Parsons
  - 如果 `event_data.student_blanks_answers` 非空：进入 `Faded Example` 答案校验分支
- `ai_tutor_trace` 增加 `scaffold_level`

### 2. 多维代码评估

- 新建 `CodeQualityAssessmentService`
  - 仅支持 `Python3`
  - 调用 `LlmClient` 返回 `readability / efficiency / style / overall / comments`
  - 所有分数必须在 `[1, 5]`
- 在 `SubmissionServiceImpl` 判题结果写回后：
  - 非 AC：基于 judge case 明细计算 `partial_score`
  - AC 且语言为 `Python3`：异步调用 `CodeQualityAssessmentService`，把结果写入 `submission.statistic_info.code_quality`
- 评估失败不影响原始提交结果，只记录日志并保持判题结果可见

## 前端设计

### 1. 脚手架卡片

- 新增：
  - `WorkedExampleCard.vue`
  - `FadedExampleCard.vue`
  - `MinimalHintCard.vue`
- `UnifiedAgentPanel.vue` 增加三种 `message_type` 分支
- `workflowStateMachine.js` 增加对应卡片类型映射与 `Faded Example` 提交事件处理
- `ParsonsPanel.vue` 顶部增加当前脚手架级别显示

### 2. 提交详情页

- `SubmissionDetails.vue`
  - AC + `Python3`：
    - 展示三维评分卡与综合分
    - 若评估尚未返回，显示“评估中”
  - 非 AC：
    - 展示测试点通过比例与进度条

## 测试设计

- 后端单元测试：
  - `ScaffoldLevelResolver`
  - `CardSchemaValidator`
  - `CodeQualityAssessmentService`
- 后端集成测试：
  - `SCAFFOLDING` 四档输出
  - `Faded Example` 填空验证
  - AC/非 AC 代码评估与部分分写回
- 前端契约测试：
  - 三种新脚手架卡片渲染
  - `SubmissionDetails` 中 `code_quality / partial_score` 展示

## 非目标

- 不实现全语言代码质量评估
- 不新增推荐系统
- 不新增独立 A/B 实验框架
