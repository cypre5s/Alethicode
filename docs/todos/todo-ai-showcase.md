# AI 教学系统竞赛展示实施计划

> 融合自 todo_show.md + agentic-ai-spec.md，消除重叠，以视觉冲击力为优先级排序。
>
> 核心叙事：Alethicode 不是"带 AI 功能的 OJ"，而是"有学生数字孪生、自适应教学、Agent 透明治理的 AI 教学系统"。
>
> 产品形态：融入现有页面，不新建孤立 Showcase 页面。
> 开关策略：`REACT_ENABLED` 默认关闭，比赛演示时由管理员开启。

---

## 视觉冲击力清单（演示顺序）

| # | 展示画面 | 冲击力 | 技术点 | 来源 |
|---|---------|--------|--------|------|
| 1 | 知识星图上弱节点脉动 + 当前题 KC 高亮 | ★★★★★ | KC 掌握度图谱 | todo_show |
| 2 | 做题页 Learning Twin 面板：预测卡点 + 记忆引用 + 相似错误 | ★★★★★ | 数字孪生聚合 | todo_show |
| 3 | Agent 个性化欢迎：「上次你在循环边界卡了...」 | ★★★★ | 长期记忆 + LLM | agentic-ai |
| 4 | 错误诊断卡片展开推理链（5 步可视化） | ★★★★★ | Reasoning Chain | 两者共有 |
| 5 | 卡片内工具调用时间线：code_runner → error_matcher | ★★★★ | ReAct 工具调用 | 两者共有 |
| 6 | 策略反馈：「这个解释风格适合我 👍 / 换种方式 👎」 | ★★★ | 自适应学习风格 | agentic-ai |
| 7 | 后台 Agent 概览看板：调用量 + 延迟 + 失败率 | ★★★★ | 运营指标 | 两者共有 |
| 8 | 后台 8 维质量雷达图 + 趋势线 | ★★★★★ | LLM-as-Judge | agentic-ai |
| 9 | Trace 回放甘特图：span 级决策链路 | ★★★★★ | Agent 追踪 | 两者共有 |
| 10 | 工具/脚手架/Memory 行为统计 | ★★★ | 行为分析 | agentic-ai |
| 11 | "全班 23% 同学犯了同类 off-by-one 错误" | ★★★★ | AST 错误聚类 | 新增 |
| 12 | Prompt 变体 ELO 对比："类比驱动式最受欢迎" | ★★★★ | RLHF 教学优化 | 新增 |

---

## 已有基础

| 组件 | 位置 | 能力 |
|------|------|------|
| OrchestratorAgent | `service/aitutor/agent/OrchestratorAgent.java` | 路由调度 + 流水线 |
| DiagnosticsAgent / GuideAgent / MetacognitiveAgent / TransferAgent | `service/aitutor/agent/` | ReAct 多轮工具调用 |
| LearnerMemoryService | `service/aitutor/profile/LearnerMemoryService.java` | pgvector 语义去重 + Ebbinghaus 衰减 |
| MasteryService | `service/aitutor/profile/MasteryService.java` | KC 掌握度 |
| TutorEvalHarness | `service/aitutor/eval/TutorEvalHarness.java` | 8 维 LLM-as-Judge |
| ToolTraceEntry | `service/aitutor/react/ToolTraceEntry.java` | 工具调用追踪 |
| EvidencePack | `service/aitutor/evidence/EvidencePack.java` | 证据组装 |
| UnifiedAgentPanel.vue | `pages/oj/views/problem/` | Agent 面板 |
| KnowledgeStarMap.vue | `pages/oj/components/skillProfile/` | 知识星图 |
| ObservabilityDashboard.vue | `pages/admin/views/general/` | 可观测性面板 |

---

## 实施总原则

- 不做独立 AI 展示页，所有展示从正常产品入口进入。
- 不把 `REACT_ENABLED` 全站默认开启。
- 数据缺失时 fail-fast，不静默拼假数据。
- 新 API 使用资源化路径。
- 不新建业务表，复用 `ai_workflow_event` + `ai_learner_memory` 的 JSONB 扩展。
- 前端只展示服务端确认过的数据，不在浏览器端推断学习结论。

---

## Phase 1：基线冻结 + 种子数据（1 天）

### 目标
确保现有功能不被破坏，准备演示用种子数据。

### 后端

- [x] 确认 `REACT_ENABLED` 可切换且默认关闭
- [x] 确认 `ai_workflow_event.trace_id` 已存在
- [x] 创建 `scripts/seed/seed_ai_showcase.sh`（幂等）：
  - 演示学生账号 + 绑定语言包
  - 历史 WA/AC 提交样本
  - `ai_learner_memory` 错误模式记忆
  - `learner_kc_mastery` 高低掌握度
  - 少量 `ai_workflow_event` trace 样本

### 数据库

- [ ] 新增迁移 `V52__ai_showcase_observability_indexes.sql`：
  - `idx_ai_workflow_event_type_time (event_type, created_at DESC)`
  - `idx_ai_workflow_event_agent_name ((event_data->>'agent_name'))`
  - `idx_ai_workflow_event_tool_name ((event_data->>'tool_name'))`

### 验收
- 种子脚本跑完后，知识星图有明显强弱节点

---

## Phase 2：学生侧数字孪生 + 个性化欢迎（3-4 天）

### 目标
学生进入题目页，立刻看到 AI 对自己的理解。

### 后端

**Learning Twin 聚合服务**

- [x] 新增 `LearningTwinService.java`
- [x] `GET /api/ai/learning-twins/current?language_pack_id=X&problem_id=Y`
  - 从 `learner_kc_mastery` 聚合课程总掌握度
  - 从 `ai_problem_kc_mapping` 查当前题关联 KC
  - 从 `ai_learner_memory` 读取 active memory refs
  - 从错题本聚合相似错误
  - 输出 `predicted_blockers`（基于已有数据，不调 LLM）
  - 输出 `recommended_actions`（固定动作集合）

**个性化欢迎**

- [x] 新增 `AITutorWelcomeService.java`
- [x] `GET /api/ai-tutor/welcome?problemId=X`
  - 调用 Memory + 历史结论 + 学习风格
  - LLM 生成个性化 greeting（异步加载，先显示默认欢迎）
  - 根据当前状态排序 `starter_actions`

### 前端

- [x] 新增 `LearningTwinPanel.vue`：相关 KC + 风险等级 + 历史错误 + 推荐动作
- [x] 改造 `UnifiedAgentPanel.vue` 欢迎态：个性化 greeting + 记忆标签

### 前端（知识星图联动）

- [x] `KnowledgeStarMap.vue`：弱 KC 脉动 + 当前题关联 KC 高亮
- [ ] 节点详情：掌握度 + 历史错误数 + 推荐复习动作（部分完成：掌握度已有，复习动作待补）

### 验收
- 种子学生进入题目页看到完整 Learning Twin
- 知识星图对应节点高亮并脉动
- 欢迎态显示个性化文案

---

## Phase 3：Agent 透明教学（3-4 天）

### 目标
学生提交错误代码后，Agent 的思考过程和工具使用完全透明。

### 后端

**Reasoning Chain**

- [x] `GuideAgent`、`DiagnosticsAgent`、`MetacognitiveAgent` 的 prompt 扩展输出 `reasoning_chain`（5 步：观察→假设→验证→结论→建议）
- [ ] `CardSchemaRegistry` 新增 `reasoning_chain` 字段定义
- [x] 校验失败时降级为不显示 reasoning_chain（不阻断工作流）

**Tool 调用透传**

- [x] 各 ReAct Agent 输出时附加 `tool_calls` 数组（来自 `ToolTraceEntry`）
- [x] 字段：tool_name、description、latency_ms、result_summary

**策略反馈**

- [x] `POST /api/ai-tutor/strategy-feedback`（含 `strategy_type` + `rating`）
- [x] 写入 `ai_learner_memory` 的 `teaching_strategy_preference`

### 前端

- [x] 新增 `ReasoningChain.vue`：5 步折叠展示，图标 + 内容
- [x] 新增 `ToolCallTimeline.vue`：竖排时间线，工具名 + 耗时 + 结果
- [x] 新增 `EvidenceRefs.vue`：课件页 + 相似错误 + 记忆引用
- [x] `ErrorDiagnosisCard.vue` 卡片底部集成以上三个组件 + 策略反馈按钮
- [x] Agent 回复下方添加策略反馈按钮

### 验收
- 错误诊断卡片展开后显示完整推理链 + 工具调用 + 证据
- 前端能正常折叠/展开
- 策略反馈能写入后端

---

## Phase 4：Trace Recorder + 管理端驾驶舱（4-5 天）

### 目标
管理员一页看到 Agent 系统全貌，能追踪任意交互的决策链路。

### 后端

**Trace Recorder**

- [ ] 新增 `AgentTraceRecorder.java`
- [ ] 在 Agent 执行关键节点插入 span：`DISPATCH → EVIDENCE_ASSEMBLY → MEMORY_RECALL → LLM_CALL → [TOOL_CALL × N] → GUARDRAIL → OUTPUT`
- [ ] 写入 `ai_workflow_event`（`event_type='trace_span'`）

**Admin APIs**

- [ ] `GET /api/admin/ai/agents/overview?range=today|7d|30d`
  - 总调用、唯一用户、失败率、Memory 命中率、hourly_trend、by_agent 分布
- [ ] `GET /api/admin/ai/traces/{trace_id}/timeline`
  - span 时间线 + output_cards + eval_result
- [ ] `GET /api/admin/ai/evaluations/dashboard?range=7d`
  - 8 维评分 + 趋势 + 失败桶
- [ ] `GET /api/admin/ai/behavior-analytics?range=7d`
  - 工具使用排行 + 脚手架分布 + Memory 影响对比

### 前端

- [ ] `ObservabilityDashboard.vue` 新增 4 个 Tab：

**Tab 1: Agent 概览**
- 顶部 4 stat 卡片（调用量、唯一用户、失败率、质量评分）
- Agent 调用分布饼图 + 延迟柱状图
- 24 小时趋势线

**Tab 2: 质量评测**
- 8 维雷达图（FACTUAL_CORRECTNESS, PEDAGOGICAL_FIT, ANSWER_LEAKAGE, KC_ALIGNMENT...）
- 总分趋势线
- 失败桶分布

**Tab 3: 行为分析**
- 工具调用排行（水平柱状图）
- 脚手架层级分布（饼图）
- Memory 有/无对比

**Tab 4: Trace 回放**
- 搜索栏：trace_id 或时间范围
- 横向甘特图：每个 span 一条色带，LLM_CALL 高亮
- TOOL_CALL 可展开看输入/输出

### 验收
- 管理员能从菜单进入 AI 观测驾驶舱
- 4 个 Tab 数据与 `ai_workflow_event` 一致
- trace_id 回放能看到完整 span 链

---

## Phase 5：自适应学习风格（2 天，可选增强）

### 目标
Agent 根据学生历史交互自动识别学习风格偏好。

### 后端

- [ ] `LearnerMemoryService.inferLearningStyle(userId)`
  - 4 种风格：STEP_BY_STEP / EXAMPLE_FIRST / ANALOGY_DRIVEN / MINIMAL_HINT
  - 基于策略反馈评分投票，新用户默认 STEP_BY_STEP
- [ ] `LearnerState` 新增 `learningStyle` 字段
- [ ] 各 Agent system prompt 注入 `【教学策略偏好】当前学生偏好: {style.label()}`

### 验收
- 新用户默认 STEP_BY_STEP
- 有 20+ 次交互的用户能推断出非默认风格
- Agent 输出风格可感知变化

---

## Phase 6：代码 AST 错误模式聚类（3 天，可选增强）

### 目标
自动分析学生代码的结构错误，将相似错误聚类并生成人类可读标签。

### 后端

- [ ] 新增 `AstErrorAnalysisService.java`，通过 ProcessBuilder 调用 Python AST 分析
- [ ] 新增 Python 脚本 `backend/nfk/ast_analyzer.py`：
  - `ast.parse()` 解析学生代码
  - 与参考解法做 AST diff，提取差异特征
  - DBSCAN 聚类同一道题的错误代码
  - 为每个聚类生成特征描述
- [ ] DiagnosticsAgent 的 EvidencePack 注入错误聚类信息：
  - 当前错误属于哪个聚类
  - 该聚类在全班的占比
  - 聚类的人类可读标签（首次由 LLM 生成，后续缓存）
- [ ] 写入 `ai_learner_notebook.error_taxonomy`（自动标注替代手工）

### 前端

- [ ] `UnifiedAgentPanel.vue` 错误诊断卡片新增"错误模式"标签
- [ ] 显示"全班 X% 的同学也犯了同类错误"

### 与 Phase 2 的协同
AST 分析结果作为 Learning Twin 的 `predicted_blockers` 数据源：如果学生历史上反复出现某类 AST 错误模式，在新题的 Learning Twin 中标记为"高风险"。

### 验收
- WA 提交后能自动识别错误类型（off-by-one / 缩进逻辑 / 变量覆盖等）
- 同一道题相似错误被聚为一组

---

## Phase 7：RLHF 教学风格优化（2.5 天，可选增强）

### 目标
用学生反馈作为信号，自动优化 Agent 的教学策略——越用越好。

### 方案
简化 RLHF：Prompt 候选池 + ELO 评分 + UCB1 探索-利用。不微调 LLM，只选择最优 prompt。

### 后端

- [ ] 为每个 Agent 准备 3-5 个 system_prompt 变体（不同教学风格的 prompt）
- [ ] 新增 `PromptVariantSelector.java`：
  - 维护每个 prompt 变体的 ELO 评分
  - 用 UCB1 算法选择：exploitation（评分最高）+ exploration（尝试低频变体）
  - 学生反馈（👍/👎）更新 ELO 评分
- [ ] 评分存储：复用 `ai_learner_memory`（memory_type='prompt_variant_score'）
- [ ] Agent 调用时通过 `PromptVariantSelector` 选择 system_prompt

### 与 Phase 3/5 的协同
- Phase 3 的策略反馈（`POST /api/ai-tutor/feedback`）是 RLHF 的输入信号
- Phase 5 决定学生偏好的风格类别，RLHF 在该类别内选择最优 prompt 变体
- 关系：Phase 5 = 粗粒度路由，Phase 7 = 细粒度优化

### 前端（管理端）

- [ ] Phase 4 行为分析 Tab 新增"Prompt 变体对比"面板
- [ ] 展示每个 Agent 的各 prompt 变体 ELO 评分和使用次数

### 验收
- 有 20+ 条反馈后，Prompt 变体评分出现分化
- UCB1 能自动平衡探索和利用

---

## 数据库变更汇总

**不新建任何表。** 所有新增数据复用 `ai_workflow_event` + `ai_learner_memory` 的 JSONB 扩展。

### 新增索引

| 索引 | 列 |
|------|-----|
| `idx_ai_workflow_event_type_time` | `(event_type, created_at DESC)` |
| `idx_ai_workflow_event_agent_name` | `((event_data->>'agent_name'))` |
| `idx_ai_workflow_event_tool_name` | `((event_data->>'tool_name'))` |

### ai_workflow_event 新增 event_type

| event_type | 用途 |
|------------|------|
| `trace_span` | Agent Trace 细粒度 span |
| `strategy_feedback` | 学生对教学策略的反馈 |

### ai_learner_memory 新增 memory_type

| memory_type | 用途 |
|-------------|------|
| `teaching_strategy_used` | Agent 使用的策略 |
| `teaching_strategy_preference` | 学生偏好的策略 |

---

## API 契约汇总

| API | 方法 | Phase | 权限 | 用途 |
|-----|------|-------|------|------|
| `/api/ai/learning-twins/current` | GET | 2 | 登录用户（只能查自己，管理员可查他人） | 学习数字孪生快照 |
| `/api/ai-tutor/welcome` | GET | 2 | 登录用户 | 个性化欢迎态 |
| `/api/ai-tutor/feedback` | POST | 3 | 登录用户 | 策略反馈 |
| `/api/admin/ai/agents/overview` | GET | 4 | 管理员 | Agent 运营概览 |
| `/api/admin/ai/traces/{id}/timeline` | GET | 4 | 管理员 | Trace 时间线 |
| `/api/admin/ai/evaluations/dashboard` | GET | 4 | 管理员 | 质量评测看板 |
| `/api/admin/ai/behavior-analytics` | GET | 4 | 管理员 | 行为统计 |

### 权限校验规则

- 未登录 → 401
- 学生查他人 `user_id` → 403
- `/api/admin/*` 非管理员 → 403
- `language_pack_id` 缺失 → 400
- `problem_id` 不属于该语言包 → 400
- `range` 非法 → 400
- 无数据时返回空集合和 0 指标，不返回假样本

### 响应 JSON Schema（关键 API）

**`GET /api/ai/learning-twins/current`**

```json
{
  "mastery_summary": { "overall_mastery": 0.62, "weak_count": 4, "mastered_count": 8 },
  "current_problem_overlay": {
    "related_kcs": [{ "kc_id": 12, "kc_name": "for 循环边界", "mastery": 0.31, "risk_level": "high" }],
    "predicted_blockers": [{ "type": "misconception", "label": "range 上界不包含 n", "confidence": 0.86 }]
  },
  "memory_refs": [{ "memory_type": "error_pattern", "memory_summary": "多次在循环上界多写 1", "confidence": 0.82 }],
  "similar_errors": [{ "problem_id": 952, "title": "统计偶数", "error_taxonomy": "loop_boundary" }],
  "recommended_actions": [{ "action": "ask_diagnostics_agent", "label": "让 AI 看看错误", "reason": "历史相似错误命中" }]
}
```

**`GET /api/admin/ai/agents/overview`**

```json
{
  "total_calls": 128, "unique_users": 12, "avg_latency_ms": 1160, "failure_rate": 0.02, "memory_hit_rate": 0.71,
  "by_agent": [{ "agent_name": "DiagnosticsAgent", "call_count": 42, "avg_latency_ms": 1420, "failure_rate": 0.03 }],
  "hourly_trend": [{ "hour": "09:00", "call_count": 8 }]
}
```

**`GET /api/admin/ai/traces/{id}/timeline`**

```json
{
  "trace_id": "abc123", "total_duration_ms": 2340,
  "spans": [
    { "span_type": "DISPATCH", "duration_ms": 2, "summary": "→ DiagnosticsAgent" },
    { "span_type": "LLM_CALL", "duration_ms": 1800, "summary": "deepseek-chat, 1200 tokens" },
    { "span_type": "TOOL_CALL", "duration_ms": 45, "tool_name": "code_runner", "summary": "IndexError line 8" }
  ],
  "output_cards": [{ "card_type": "error_diagnosis", "summary": "循环上界多取了一位" }],
  "eval_result": { "overall_score": 0.87, "answer_leakage": 0.99, "pedagogical_fit": 0.84 }
}
```

**`GET /api/admin/ai/evaluations/dashboard`**

```json
{
  "latest_eval": {
    "avg_overall_score": 0.84, "sample_count": 20,
    "dimension_scores": { "FACTUAL_CORRECTNESS": 0.91, "PEDAGOGICAL_FIT": 0.82, "ANSWER_LEAKAGE": 0.99, "KC_ALIGNMENT": 0.86, "GUIDANCE_QUALITY": 0.76, "SCAFFOLD_LEVEL_MATCH": 0.85, "COMPREHENSIBILITY": 0.88, "ENCOURAGEMENT": 0.70 }
  },
  "score_trend": [{ "date": "2026-04-15", "avg_score": 0.84 }],
  "failure_buckets": [{ "bucket": "pedagogy_mismatch", "count": 2 }]
}
```

---

## 种子数据规格

`scripts/seed/seed_ai_showcase.sh` 必须写入以下数据（幂等，INSERT ON CONFLICT DO NOTHING）：

| 数据 | 数量 | 说明 |
|------|------|------|
| 演示学生账号 | 1 | 绑定 1 个语言包 |
| 提交记录 | 15-20 | 含 5+ WA 和 10+ AC，覆盖 3 个知识点 |
| KC 掌握度 | 8-10 | 含 2 弱（<0.4）+ 3 强（>0.8）+ 其余中等 |
| 错误记忆 | 3-5 | `memory_type='error_pattern'`，confidence 0.6-0.9 |
| 策略记忆 | 2 | `memory_type='teaching_strategy_preference'` |
| Trace 样本 | 2 | 含完整 span 链（DISPATCH→OUTPUT），确保后台能回放 |
| Eval 样本 | 3 | 含 8 维评分，确保雷达图有数据 |

---

## 测试 TODO

### 后端

- [ ] `LearningTwinServiceTest`：聚合掌握度、记忆、相似错误、推荐动作
- [ ] `AITutorWelcomeServiceTest`：有 Memory → 个性化 greeting；无 Memory → 默认欢迎
- [ ] `AgentTraceRecorderTest`：span 写入、失败 span、trace_id 贯穿
- [ ] `AgentObservabilityServiceTest`：overview、timeline、eval dashboard、behavior 查询
- [ ] `AdminAiObservabilityControllerContractTest`：权限、range 校验、trace 路径

```bash
cd backend
mvn -Dtest=LearningTwinServiceTest,AITutorWelcomeServiceTest,AgentTraceRecorderTest test
mvn -Dtest=AgentObservabilityServiceTest,AdminAiObservabilityControllerContractTest test
```

### 前端

- [ ] `learning-twin-panel-contract.spec.js`
- [ ] `reasoning-chain-contract.spec.js`
- [ ] `tool-call-timeline-contract.spec.js`
- [ ] `admin-ai-observability-dashboard-contract.spec.js`

```bash
cd frontend
npm test -- learning-twin-panel-contract.spec.js reasoning-chain-contract.spec.js
npm test -- tool-call-timeline-contract.spec.js admin-ai-observability-dashboard-contract.spec.js
```

### E2E

- [ ] `frontend/tests/e2e/ai-showcase.spec.js`
  - 登录演示学生 → 打开目标题 → 验证 Learning Twin → 提交错误代码 → 触发 AI 诊断 → 展开推理链 → 切换管理员 → 回放 Trace

---

## 工期与优先级

| Phase | 天数 | 优先级 | 视觉冲击力 |
|-------|------|--------|-----------|
| Phase 1: 基线 + 种子 | 1 | P0 | — |
| Phase 2: 数字孪生 + 欢迎 | 3-4 | P0 | ★★★★★ |
| Phase 3: Agent 透明教学 | 3-4 | P0 | ★★★★★ |
| Phase 4: 管理端驾驶舱 | 4-5 | P1 | ★★★★★ |
| Phase 5: 自适应风格 | 2 | P2 | ★★★ |
| Phase 6: AST 错误聚类 | 3 | P2 | ★★★★ |
| Phase 7: RLHF 教学优化 | 2.5 | P2 | ★★★★ |

**P0 必做：11-13 天（Phase 1-3）**
**P0+P1：15-18 天（Phase 1-4）**
**全量：22.5-27.5 天（Phase 1-7）**

---

## 演示脚本

1. 管理员开启 `REACT_ENABLED`
2. **知识星图**：学生主页 → 弱节点脉动，强弱分明
3. **数字孪生**：进入目标题 → Learning Twin 面板显示 KC 风险 + 历史错误 + 推荐动作
4. **个性化欢迎**：Agent 面板显示「上次你在循环边界卡了...」
5. **错误诊断**：粘贴错误代码 → 提交 → 判题失败 → 点击 AI 诊断
6. **推理链展开**：观察→假设→验证→结论→建议，5 步透明
7. **工具调用**：code_runner + error_matcher 的调用结果时间线
8. **策略反馈**：「这个解释风格适合我 👍」
9. **管理端**：切换管理员 → AI 观测驾驶舱
10. **概览看板**：调用量 + 失败率 + Agent 分布
11. **质量雷达图**：8 维评分 + 趋势
12. **Trace 回放**：点击刚才的 trace_id → 甘特图展示完整 span 链

通过标准：
- 全流程不离开正常产品页面
- 不声称"AI 一定正确"，只声称"可追踪、可评测、可治理"
- 模型不可用时仍可用种子 trace 展示后台驾驶舱
