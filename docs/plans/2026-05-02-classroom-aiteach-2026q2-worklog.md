# Classroom AI 教学闭环 2026Q2 工作日志

来源 plan：`~/.cursor/plans/classroom_aiteach_2026q2_017a145c.plan.md`

执行人：assistant（2026-05-02 起）

---

## 审计结论（开工前）

| Plan 标注 | 真实落地 | 备注 |
| ----- | ----- | ----- |
| Phase 0 重命名 ZPD → MasteryAdaptiveProblemSelector | ❌ 未落地 | `ZpdProblemSelectorService.java` 仍在原位置 |
| Phase A.1 V81 迁移 | ❌ 未落地 | 当前最新迁移 V78 |
| Phase A.2 ClassroomKcResolver | ❌ 未落地 | 文件不存在 |
| Phase A.3 出题策略分流 | ❌ 未落地 | `aiGeneratedProblemCreate` 仍是字符串 KC |
| Phase A.4 发布反写 KC mapping | ❌ 未落地 | 仅写 `language_pack_problem_mapping` |
| Phase A.5 前端 KC 级联 | ❌ 未落地 | `AIGeneratedProblems.vue` 仍是 allow-create |
| Phase B/C 全部 | ❌ 未落地 | 全新需求 |

可复用的现成基础设施：

- `BeginnerSupplementPlannerService.buildPlan(userId, trigger, lpId, problemId, submissionId, errorTaxonomy, count)`
- `MasteryService.projectMasteryByLanguagePack(userId, lpId)`
- `ErrorReviewPackageService.createPackage(userId, errorTaxonomy, lpId, problemId, trigger)`
- `LearningEventPublisher.publishReviewPackageUpdated / publishLearnerMemoryUpdated`
- `LanguagePackExampleQueryService` / `MisconceptionInferenceService`

关键 schema 现状：

- `ai_problem_kc_mapping(id, problem_id, kc_id, weight, language_pack_id)`：V9 起 `kc_id` 引用 `ai_knowledge_component(id)`，但 BSP/AdminLanguagePackController 等多处直接 join `language_pack_kc.id = m.kc_id`。本次 Phase A 反写时统一采用「`m.kc_id = language_pack_kc.id`」语义，并在 V81 中 drop 旧 FK 约束以放宽 kc_id 取值。
- `classroom_assignment` 已有 `anti_cheating_enabled` / `allow_ai_tutor` 字段（无需额外加）。
- `classroom_assignment_problem_submission` 当前无 `error_taxonomy` / `review_package_id`，需要 V82 扩展。

---

## 实施顺序

按 Phase 0 → A → B → C 串行实施。每完成一个 Phase 在下文追加摘要。

### Phase 0 — 重命名 ZPD 服务（已完成）

变更点：

- 新增 `backend/.../service/aitutor/path/MasteryAdaptiveProblemSelector.java`：完全委托 `BeginnerSupplementPlannerService.buildPlan(userId, "warmup", lpId, null, null, null, 1)` 抽出 `coding_problem`/`objective_problem` 卡组装回原 `selectNextProblem` 接口的返回结构（含 `weakest_kc / selection_strategy='adaptive_boundary' / target_difficulty / candidates / recommended`）。
- 删除 `ZpdProblemSelectorService.java`。
- `CourseProgressController` 字段、构造、import 同步切到 `MasteryAdaptiveProblemSelector`。
- `AITutorServiceImpl.recommendProblems`：`valid` 列表 `zpd → adaptive`；`recommendByMastery` 内 `orderClause` / `reasonText` / 输出字段 `zpd_score → adaptive_score` 同步；`recommendFallback` 中 reason 文案同步。
- `frontend/src/pages/oj/views/problem/LearningPathMap.vue`：`STRATEGY_LABELS.zpd_boundary` 改 `adaptive_boundary`；`target_difficulty` 显示由数字百分比改为枚举标签（Low/Mid/High → 简单/中等/较难）。
- `frontend/src/pages/oj/components/skillProfile/ProblemRecommendations.vue`：模板字段 `rec.zpd_score → rec.adaptive_score`；strategy 描述里的 `zpd` 改为 `adaptive`。
- 新增测试 `MasteryAdaptiveProblemSelectorTest`：覆盖空计划、coding 卡命中、objective 卡兜底、入参非法。

`BeginnerSupplementPlannerService` 内部不存在 `zpd_boundary` 字符串，无需改动。

### Phase A.3 — KC-aware 出题分流（已完成）

`ClassroomAiProblemService.aiGeneratedProblemCreate` 大改：

- 入参 `target_kc_names` 仅打 deprecation 日志，不再注入 prompt。
- 新增入参 `target_kc_ids` (List)、`prefer_strategy` (`lp_first|llm_first|lp_only|llm_only`，默认 `lp_first`)。
- 解析 `language_pack_id`：优先走 `ClassroomKcResolver.resolveLanguagePackId`，无绑定时 `prefer_strategy` 退化为 `llm_only` 并打 warn 日志（不强制阻断老班级生成）。
- `target_kc_ids` 必须真属于该 LP，否则 failfast。
- 出题流程：
  - `coding` 题：当 prefer_strategy 允许 LP 命中时，先调 `pickFromLanguagePackPool` 按 KC + 难度评分（Low=0.3, Mid=0.5, High=0.75）从 `ai_problem_kc_mapping × problem` 池里选题，命中后反向序列化为 `ai_generated_problem` 行（`source_strategy='lp_kc_pick'` `validation_status='passed'`）。
  - `choice` / `fill_blank`：同样支持 LP 池命中（基于 `statistic_info.objective_question.question_type`）。
  - 不足部分按 `prefer_strategy` 决定是否走 LLM 兜底；`lp_only` 不足直接 short circuit。
- LLM 兜底分支保留原 `generateProblemFromCourseware`，但 prompt 注入 KC 名（来自 `ClassroomKcResolver.loadKcNameMap`）+ `target_kc_ids` 同步进库。
- 每条 `ai_generated_problem` 行写 `target_kc_ids JSON` 和 `source_strategy`。
- 返回值新增 `lp_picked / llm_generated / source_strategy / language_pack_id / target_kc_ids` 字段供前端展示。

新增端点：

- `GET /api/classroom/{classroomId}/ai/generated-problems/kc-options`：返回 `{ classroom_id, language_pack_id, chapters: [{chapter_id, chapter_index, chapter_title, kcs:[...]}] }`，由 `ClassroomKcResolver.listKcOptionsTree` 返回。

### Phase B.1 — V82 迁移（已完成）

新文件 `backend/src/main/resources/db/migration/V82__classroom_assignment_smart_compose.sql`：

- `classroom_assignment` 增 `compose_strategy VARCHAR(20) NOT NULL DEFAULT 'manual'`（CHECK manual/smart_kc）+ `target_kc_ids JSONB NOT NULL DEFAULT '[]'`。
- `classroom_assignment_problem_submission` 增 `error_taxonomy VARCHAR(64)` + `review_package_id VARCHAR(64)`。
- `target_kc_ids` 加 GIN 索引；submission 上加 BTREE 索引。

### Phase B.2 — ClassroomAssignmentSmartComposer（已完成）

新文件 `backend/.../service/classroom/ai/ClassroomAssignmentSmartComposer.java`：

- 拉班级所有 student 的 mastery（聚合平均），自动识别薄弱 KC TOP-K（教师未传 KC 时）。
- 选「平均距离最小」的代表生委托 `BeginnerSupplementPlannerService.buildPlan(trigger=daily_review)` 拿 `coding_problem` / `objective_problem` 卡。
- 跨 KC 去重、按 `total_problem_budget` 截断；按 KC 名分组成 sections。
- 提供 `resolveClassroomProblemIdsByProblemId` 把 `problem_id` 解为 `classroom_problem_id`，给 assignment 写入用。

### Phase B.3 — LearningEventPublisher.publishAssignmentSubmissionGraded（已完成）

- `LearningEventPublisher` 接口新增 `publishAssignmentSubmissionGraded(userId, assignmentId, problemId, isCorrect, errorTaxonomy, languagePackId, submissionDetailId)`，`NOOP` 哨兵实现同步补齐。
- `NatsLearningEventPublisher` 新增 `assignmentProblemSubmittedSubject`（默认 `alethicode.classroom.assignment.problem.submitted`）并加入 `requiredSubjects()`。
- `NoopLearningEventPublisher` 同步补 noop 实现。

### Phase B.4 — ClassroomAssignmentEventSubscriber（已完成）

新文件 `backend/.../service/aitutor/events/ClassroomAssignmentEventSubscriber.java`：

- AC：调 `MasteryService.applyEvidence(...)` 写 `ai_learning_event(event_type='submission_ac', source='classroom_assignment')`。
- WA + 命中 `ErrorTaxonomy`：调 `ErrorReviewPackageService.createPackage(...)` 建复习包 + 把 `package_id`、`error_taxonomy` 回写到 `classroom_assignment_problem_submission`。
- WA 但 taxonomy 缺失：仅写 `error_taxonomy` 字段标记，不创建复习包。
- 任何步骤失败 try/catch + log，不阻断 classroom 主提交链路；订阅者自身使用 `REQUIRES_NEW` 隔离事务。

`MasteryService` 新增公开方法 `applyEvidence(userId, problemId, isCorrect, source, errorTaxonomy)`，作为 `ai_learning_event` 写入入口（mastery 推理仍 lazy 在 read 路径）。

### Phase B.5 — 评分页学情上下文（已完成）

`ClassroomAssignmentDomainServiceImpl.assignmentSubmissions` 输出每条 detail 上挂：

- `error_taxonomy`：本次提交识别到的错误分类（订阅者写入）。
- `recent_misconceptions`：按学生 + 该题 KC 维度从 `ai_learner_notebook` 聚合 TOP-5 错误标签。
- `linked_review_package`：复习包 ID 反查 `ai_error_review_package`，含 `error_taxonomy / mastery_reached / due_at`。

### Phase C.1 — 后端桥接 tutor-graph（已完成）

- `TutorGraphClient` 新增 `createThread(... , Map<String,Object> context)` 重载，把 context 透传到 tutor-graph `/internal/graph/threads`。
- `TutorWorkflowController.createSession` 接受 request body 中的 `context`，当 `source==classroom_assignment` 时强制要求 `anti_cheating` 显式传，否则 422。
- 新增端点 `GET /api/classroom/{classroomId}/assignments/{assignmentId}/problems/{classroomProblemId}/tutor-context`：返回作业级元数据 + 直接可用的 `tutor_context` 字典（source/classroom_id/assignment_id/problem_id/anti_cheating），供前端创建 session 时填充。

### Phase C.2 — tutor-graph anti_cheating 降 hint（已完成）

- `services/tutor-graph/app/graph/state.py` 增 `context: dict` 字段。
- `services/tutor-graph/app/main.py`：`CreateThreadRequest` 接受 `context`，缓存到内存 `_thread_contexts`，`anti_cheating` 缺失时 422；`_execute_run` 把 thread context 注入 input_state。
- `services/tutor-graph/app/nodes/reading.py`：在 `context.source==classroom_assignment` 且 `context.anti_cheating==True` 时，额外追加 `ANTI_CHEATING_GUARD` system prompt（hint 等级降为 1，仅给概念提示）。
- `services/tutor-graph/app/nodes/diagnosis.py`：同上，对错误诊断卡 hint 强制概念化、禁止给可复制片段。
- 两节点 langfuse metadata 新增 `anti_cheating` 标签便于评测。

### NFK 训练数据格式正确性确认（已完成）

数据收集与训练管线现状：

| 链路 | 文件 | 状态 |
| --- | --- | --- |
| 后端流式导出 5 字段 CSV | `NfkDataExportService` | ✅ 已存在，行级 `NfkTrainingRowValidator` failfast |
| JSON Schema 契约 | `contracts/nfk/training_dataset.schema.json` | ✅ 5 字段 + ISO-8601 UTC 严格 |
| Python 校验器 | `research/nfk/data/contract_validator.py` | ✅ 与后端共用 schema |
| 训练入口 alethicode 数据路径 | `nfk/run_local.py --dataset alethicode --data-path X.csv` | 🆕 本次新增 |
| Alethicode CSV preprocessor | `nfk/data/preprocessor_alethicode.py.AlethicodeCsvPreprocessor` | 🆕 本次新增 |
| 7 用例 pytest | `nfk/tests/test_preprocessor_alethicode.py` | 🆕 本次新增，全绿 |
| 端到端 smoke | `validate_csv` + `AlethicodeCsvPreprocessor` 同一 CSV 一致 | ✅ 5 行 CSV → 5 rows pass + 2 student sequences |

字段映射（再次确认）：

- `user_id` → 整数 ≥ 1（excluded `<= 0` 系统行）
- `question_id` → 整数 ≥ 1（`alethicode.problem.id`）
- `skill_id` → 整数 ≥ 1（按 `ai_problem_kc_mapping.weight DESC, kc_id ASC` 取主 KC）
- `response` → 0/1（`submission.result == 0` ? 1 : 0）
- `timestamp` → ISO-8601 UTC（`Instant.toString()`，`YYYY-MM-DDTHH:MM:SS[.fff]Z`）

数据收集触发点：所有学生 OJ 提交（含 classroom 作业内的 coding 题，因为统一走 `submission` 表）。Phase A.4 强制反向写 `ai_problem_kc_mapping` 让新发题目都有主 KC，避免训练数据稀疏。

### anti_cheating LLM-as-judge 评测脚本（已完成）

误读用户初次回复「真实的 LLM 用 deepseek v4」后曾把全局默认改成 `deepseek-v4`，已**全部回滚**到 `deepseek-v4-flash`（原状）。本节是修正后的正确实现：plan 5.6 SLO `anti_cheating LLM-judge ≥ 0.9` 需要专用评测脚本，让它用 DeepSeek V4 + 现有 OPENAI_API_KEY。

新增 `services/tutor-graph/app/eval/anti_cheating_judge.py`：

- 读 JSONL 样本：`{id, node, anti_cheating, card}`。
- 用 DeepSeek V4 当 judge，按四个维度（代码泄露 / 过度提示 / 仅概念引导 / 不空话）打 0..1 分。
- 输出 JSON 报告（含 leakage_examples / justification）+ 平均分。
- `--baseline 0.9` 启用 CI gate，平均分低于阈值进程返回非零。
- API key fallback：`ALETHICODE_RED_TEAM_OPENAI_API_KEY → OPENAI_API_KEY`；base_url 与 model 均按 env → `https://api.deepseek.com / deepseek-v4` 兜底。

并改 `services/tutor-graph/app/eval/red_team/ci_gate.py.make_real_llm_client`：

- dedicated key fallback：缺失时回退到 `OPENAI_API_KEY` 并打 warning（CI 仍推荐专用 key）。
- 默认 model = `deepseek-v4`、base_url = `https://api.deepseek.com`，方便本地复现。

生产 LLM 默认 `deepseek-v4-flash` 保持不变（出题等高频调用维持成本可控），仅评测路径用 v4。

### UI/UX 二轮统一（已完成）

应用户要求"前端统一美观"，对 Phase A/B/C 三个新增/改造组件做了视觉刷洗：

- `AIGeneratedProblems.vue`：生成对话框中 KC + 策略 + 难度抽成独立 `generate-section` 卡片（浅蓝边框 + 图标小标题）；KC 标注弹窗加 `.kc-label-form-block` 包装与 `Promotion` 图标；引入复用 `.form-hint` 与 `.strategy-radio-group` class 统一字号 / 间距 / 颜色。
- `ClassroomAssignment.vue`：智能组卷面板改为渐变背景 + 圆角卡片（`smart-compose-panel`），加 `summary-pill` 题数徽章；预览表格难度列改为 Tag、题型列改为友好中文；表格圆角统一。
- `AssignmentGrading.vue`：复习包卡片渐变 + hover 阴影 + 三列网格（`rpc-grid`）；标签字号 / 大小写 / 颜色统一；链接添加 `cursor-pointer` 与 hover 下划线。
- 全程使用 Element Plus 默认主色（#2d8cf0）+ 成功（#19be6b）+ 警告（#fa8c16）+ 危险（#ed4014）配色，确保和项目其他面板风格一致。
- 4 套契约测试 24 用例全部通过；`ReadLints` 三处文件无 lint 错误。

### Phase C.4 — 测试（已完成）

- 新增前端契约 `classroom-assignment-tutor-panel-contract.spec.js`：grep AssignmentDetail.vue / workflowStateMachine.js / Problem.vue / 后端 controllers / TutorGraphClient / tutor-graph 三个文件的 anti_cheating 字段与 classroom_assignment 链路。
- 修复现有 `NatsLearningEventPublisherTest`：把新的 `alethicode.classroom.assignment.problem.submitted` subject 加入 required subjects 长度断言（3 → 4 / 4 → 5）。
- 后端单元测试：`ClassroomAssignmentSmartComposerTest` `ClassroomAssignmentEventSubscriberTest` `ClassroomKcResolverTest` `MasteryAdaptiveProblemSelectorTest` `BeginnerSupplementPlannerServiceTest` `NatsLearningEventPublisherTest` 全部通过。
- 前端 4 套 contract spec（24 用例）全绿。
- E2E：`frontend/tests/e2e/visual/` 已有 tutor 卡片截图测试，本次未额外扩展（plan 5.6 列为 SLO 验证项，待真实 Postgres + LLM 环境跑通）。

### Phase C.3 — 前端 from=assignment 上下文路由（已完成）

- `frontend/src/pages/oj/views/classroom/AssignmentDetail.vue`：跳转 `/problem/:id` query 增 `from=assignment` 与 `anti_cheating=0/1`。
- `frontend/src/pages/oj/views/problem/workflowStateMachine.js`：`createFreshWorkflowSession` 调用 `_resolveTutorSessionContext` 把 `{source, classroom_id, assignment_id, problem_id, allow_ai_tutor, anti_cheating}` 注入 session 创建 payload 的 `context` 字段，跨域同步到 tutor-graph。
- 现有 `Problem.vue.assignmentAITutorAllowed` / `isAITutorEnabledForCurrentProblem` 早就基于 `ai_tutor_allowed=0` 隐藏 `UnifiedAgentPanel`，无需重做；`useSubmission.js` 配合 `Problem.vue` 已实现 WA→`ERROR_FEEDBACK` / AC→`AC_REVIEW` 自动 dispatch。

### Phase B.7 — 测试（已完成）

- 新增 `ClassroomAssignmentEventSubscriberTest`（mock）：覆盖 AC 仅 mastery、WA + valid taxonomy 创复习包并写回 detail、WA 无 taxonomy 仅 mastery、入参缺失 short circuit、mastery 失败但复习包仍能创建。
- 新增 `ClassroomAssignmentSmartComposerTest`（mock）：覆盖显式 KC + 跨 KC 去重、无薄弱 KC failfast、`resolveClassroomProblemIdsByProblemId` 调用。
- 新增前端契约 `classroom-assignment-smart-compose-contract.spec.js`：grep 后端 controller / 域服务 / V82 迁移 / 前端 vue / api 模块的 smart_kc 字段与组卷 UI。
- 新增前端契约 `assignment-grading-misconception-panel-contract.spec.js`：grep 后端 SQL / vue 引用 `MisconceptionTagCloud` 和 `linked_review_package` / publisher 三个实现的新方法。

集测：Postgres-依赖的 `ClassroomModuleIntegrationTest` 在本地 cluster 不可用时 abort，新断言（事件链 P95、复习包写回）已写入但需 CI DB 环境跑通。

### Phase B.6 — 智能组卷 UI + 评分页学情面板（已完成）

`frontend/src/pages/oj/views/classroom/ClassroomAssignment.vue`：

- 创建对话框新增「组卷模式」radio：手动 / 智能组卷。
- 切换到智能组卷后展开面板：KC 级联选择器（数据源同 AI 出题端的 `getAIGeneratedKcOptions`）+ 每生题数 + 总题数 + 「预览拟选题」按钮。
- 预览调 `POST /api/classroom/{id}/assignments/preview-smart-compose/`，返回拟选题列表 + KC 名 + 总数；表格展示 KC / 题目 / 难度 / 题型。
- 「应用为作业板块」按钮把 dry-run 结果写入 `form.sections`，教师可在板块管理处再调整。
- `submitAssignment` 在 `compose_strategy=smart_kc` 时把 `target_kc_ids / per_student_budget / total_problem_budget` 一起 PUT/POST。
- `editAssignment` 把后端返回的 `compose_strategy / target_kc_ids` 回填到 form。

`frontend/src/pages/oj/views/classroom/AssignmentGrading.vue`：

- 评分弹窗的题目卡片加：`error_taxonomy` 标签 + `MisconceptionTagCloud`（按 `recent_misconceptions` 渲染）+ 复习包卡片（`linked_review_package` 不为空时显示，含 taxonomy / mastery_reached / 下次复习 + 跳转链接）。

API 同步：`getAIGeneratedKcOptions / previewClassroomAssignmentSmartCompose` 在 `frontend/src/pages/oj/api/classroom.js` 与 `frontend/src/api/modules/classroom.js` 注册。

### Phase B 主流程（已完成）

- `ClassroomAssignmentDomainServiceImpl` 注入 `ClassroomKcResolver` / `ClassroomAssignmentSmartComposer` / `LearningEventPublisher` / `ClassroomAssignmentEventSubscriber`（aitutor 包，跨域同步触发，绕过 NATS 也立即生效）。
- `assignmentCreate` 支持 `compose_strategy=smart_kc`：调 SmartComposer 自动产 sections，写 `target_kc_ids` 到表上；`manual` 路径完全沿用旧逻辑。
- 新增端点 `POST /api/classroom/{classroomId}/assignments/preview-smart-compose`：返回 dry-run 拟选题列表 / 平均 mastery / KC 命中统计，供教师勾选。
- `assignmentSubmit` 在每条 detail 写完后：
  - 同步调 `ClassroomAssignmentEventSubscriber.onAssignmentSubmissionGraded` 让 mastery / 错题复习包立即生效；
  - 调 `LearningEventPublisher.publishAssignmentSubmissionGraded` 广播到 NATS（启用时）让外部消费方同步。
- `mapAssignmentRow` 新增 `compose_strategy` / `target_kc_ids` 字段，list/retrieve 都带回前端。

### Phase A.6 — 测试（已完成）

- 新增 `MasteryAdaptiveProblemSelectorTest`（mock-based）覆盖空计划 / coding 卡 / objective 卡兜底 / 入参非法。
- 新增 `ClassroomKcResolverTest`（mock-based）覆盖 LP 解析 / 章节分组 / 非法 KC failfast / 去重 / 名字加载 / 空入参短路。
- 新增前端契约 `ai-generated-problems-kc-options-contract.spec.js`：grep 后端 controller / 域服务 / V81 SQL / 前端 vue / api 模块的 KC-aware 字段与级联 UI。
- 集测：当前 `ClassroomM11IntegrationTest` 依赖本地 Postgres（127.0.0.1:5435），无数据库环境时整套套件直接因 `flywayInitializer` 创建失败而 abort。这不是新引入的问题；扩展测试中已经预留 LP/KC seed 与发布反查 ai_problem_kc_mapping 的断言（待 CI 数据库环境补齐后再跑通）。

### Phase A.5 — 前端 KC 级联 + 出题策略 + KC 强制弹窗（已完成）

`frontend/src/pages/oj/views/classroom/AIGeneratedProblems.vue`：

- 移除自由输入的 `target_kc_names` allow-create select。
- 新增 `el-cascader` 级联选择器：第一级 chapter，第二级 KC（multiple，emitPath=false），数据来自 `GET /api/classroom/{id}/ai/generated-problems/kc-options`。
- 新增「出题策略」radio：`lp_first(推荐) / llm_first / lp_only / llm_only`。
- 详情对话框 metadata tab 新增「出题策略」标签 + 「关联 KC」标签云（基于 `target_kc_ids` 反查名字）+ 把原「知识点」改成「提取的概念」（即 LLM 写出的 `extracted_concepts`）。
- 发布前若 `target_kc_ids` 为空，弹出「发布前请标注题目 KC」对话框，二次保存 KC 后再调发布。
- 新增 API:`getAIGeneratedKcOptions(classroomId)`（在 `frontend/src/pages/oj/api/classroom.js` 与 `frontend/src/api/modules/classroom.js` 同步登记）。

后端配套：

- `ClassroomAiProblemService.aiGeneratedProblemUpdate` 支持新字段 `target_kc_ids`，写入前调 `ClassroomKcResolver.expandKcIds` 校验。

### Phase A.4 — 发布反写 KC mapping（已完成）

`aiGeneratedProblemPublish` 重写：

- 强制 `target_kc_ids` 非空才能发布，否则 failfast 提示先标 KC（前端弹窗调 `kc-options` 端点）。
- 强制班级有 `language_pack_id`，否则 failfast。
- `lesson_llm` 路径维持原 INSERT problem + INSERT language_pack_problem_mapping 行为；`lp_kc_pick` 路径直接复用 `generated_problem_json.source_problem_id`，不重新写 problem 也不重写测试用例。
- 反向写 `ai_problem_kc_mapping(problem_id, kc_id, weight=1.0/N, language_pack_id)`，`ON CONFLICT DO UPDATE` 取较大 weight（用于 lp_pick 已存在的题）。
- `classroom_problem` 已存在（lp_pick 复用题）则只做可见性恢复，避免 unique 冲突。
- 返回字段新增 `source_strategy` 与 `kc_mapped`。

### Phase A.2 — ClassroomKcResolver 薄适配器（已完成）

新文件 `backend/.../service/classroom/ai/ClassroomKcResolver.java`，对外暴露：

- `resolveLanguagePackId(classroomId)`：从 `classroom_language_pack` 取唯一 LP id（V30 起 unique），无绑定即 failfast。
- `listKcOptionsTree(classroomId)`：返回 chapter 分组结构 `[{chapter_id, chapter_index, chapter_title, kcs:[{id,name,description}]}]`，未分组的 KC 落入「未分组」。
- `expandKcIds(classroomId, rawIds)`：去重并校验 KC 全部属于该 LP，否则 failfast。
- `loadKcNameMap(lpId, kcIds)`：批量加载 KC 名字，供 LLM prompt 注入。

不引入任何 KC 业务逻辑，全部 SELECT `language_pack_kc / language_pack_chapter`。

### Phase A.1 — V81 迁移（已完成）

新文件 `backend/src/main/resources/db/migration/V81__classroom_ai_problem_kc_link.sql`：

- `ai_generated_problem` 增 `target_kc_ids JSONB NOT NULL DEFAULT '[]'`、`source_strategy VARCHAR(20) NOT NULL DEFAULT 'lesson_llm'`，并加 CHECK 约束（`lesson_llm/lp_kc_pick/hybrid`）。
- `target_kc_ids` 加 GIN 索引、`source_strategy` 加 BTREE 索引。
- `ai_problem_kc_mapping.kc_id_fkey`（V9 引用 `ai_knowledge_component`）DROP，统一约定 `kc_id = language_pack_kc.id`，与 BSP / `LearnerCourseProgressService` 等已有服务的 join 语义对齐。



