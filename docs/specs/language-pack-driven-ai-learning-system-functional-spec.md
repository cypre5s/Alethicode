# 语言包驱动 AI 学习系统 — 功能说明书

## 概述

本次实施将 Alethicode 从"带 AI 的 OJ"演进为"以语言包为课程中心、以 OJ 为实践引擎、以 AI 为学习教练的统一 AI 学习系统"。按 5 个阶段完成全链路构建：语言包课程化 → 学生学习系统化 → AI 治理一体化 → 考试目标显性化 → 课程运营平台化。

---

## 阶段一：语言包课程化

### 完成功能

将 `language_pack` 从"文档+题目的集合"升级为"结构化课程真相源"。

#### 数据库变更 (V40)
- `language_pack` 表新增课程级字段：`course_objective`（课程目标）、`target_audience`（目标受众，默认"非计算机专业编程初学者"）、`total_hours`（总学时）
- `language_pack_chapter` 表新增：`learning_objective`（章节学习目标）、`estimated_hours`（预估学时）
- 新建 `language_pack_kc_prerequisite` 表：KC（知识点）之间的前驱依赖关系（有向无环图），支持拓扑排序
- 新建 `language_pack_review_task` 表：课程复习任务模板，随语言包发布固化

#### 后端服务
- **`CourseStructureService`**：
  - `getCourseStructure(languagePackId)` → 返回完整课程树：pack 元信息、章节（按 chapter_index 排序）、每章 KC（含前驱关系）、例题、题目、复习任务
  - `getKcGraph(languagePackId)` → 返回 KC 有向图（节点 + 边）

#### API 端点
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/language-pack/{id}/course-structure` | 获取完整课程结构树 |
| GET | `/api/language-pack/{id}/kc-graph` | 获取 KC 有向图 |

#### 前端页面
- **`CourseOverview.vue`**（路由：`/course/:languagePackId`）：课程总览页面，展示课程目标、章节大纲、KC 结构、例题/题目分布统计，支持折叠展开章节详情

---

## 阶段二：学生学习系统化

### 完成功能

将导学、问答、复盘、冲刺收敛为统一学习系统的四个能力面（理解、练习、复习、冲刺）。

#### 数据库变更 (V41)
- 新建 `learner_course_progress` 表：每用户每语言包的课程进度追踪（整体掌握度、已完成章节数、已尝试/通过题目数）
- 新建 `learner_kc_mastery` 表：每用户每 KC 的精细掌握度（EMA 指数平滑，含尝试/正确/错误计数）
- 新建 `exam_sprint_plan` 表：冲刺计划（状态、目标日期、薄弱 KCs、计划结构化任务列表）
- 新建 `exam_sprint_task` 表：冲刺任务条目（任务类型、状态、完成时间）

#### 后端服务
- **`LearnerMasteryServiceUnified`**：
  - 统一 EMA 指数平滑掌握度计算（alpha=0.7），替代原有两套分散计算
  - `updateMastery(userId, languagePackId, kcId, isCorrect)` → 写入 `learner_kc_mastery` 并级联刷新 `learner_course_progress`
  - `getCourseMastery(userId, languagePackId)` → 聚合到章节和课程级
  - `getWeakKcs(userId, languagePackId, threshold)` → 低于阈值的薄弱 KCs

- **`LearnerCourseProgressService`**：
  - `getOrCreateProgress(userId, languagePackId)` → 获取或初始化课程进度
  - `refreshProgress(userId, languagePackId)` → 重新聚合所有指标

- **`ExamSprintService`**：
  - `generateSprintPlan(userId, languagePackId, targetDate)` → 生成冲刺计划
  - `getActivePlan(userId, languagePackId)` → 获取当前活跃冲刺
  - `completeTask(taskId)` / `skipTask(taskId)` → 完成/跳过任务
  - `assessReadiness(userId, languagePackId)` → 通过风险评估

#### API 端点
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/course-progress/{languagePackId}` | 学生课程进度 |
| GET | `/api/course-progress/{languagePackId}/kc-mastery` | KC 级掌握度 |
| GET | `/api/review/{languagePackId}/weak-points` | 薄弱知识点列表 |
| GET | `/api/review/{languagePackId}/error-patterns` | 错题模式归纳 |
| POST | `/api/review/{languagePackId}/generate-drill` | 生成专项练习 |
| POST | `/api/sprint/{languagePackId}/generate` | 生成冲刺计划 |
| GET | `/api/sprint/{languagePackId}/active` | 当前冲刺状态 |
| POST | `/api/sprint/task/{taskId}/complete` | 完成冲刺任务 |
| GET | `/api/sprint/{languagePackId}/readiness` | 通过风险评估 |

#### 前端页面
- **`CourseLearningHub.vue`**（路由：`/course/:languagePackId/learn`）：四能力面统一入口，顶部课程进度总览，四个 Tab（理解/练习/复习/冲刺）
- **`ReviewCenter.vue`**（路由：`/course/:languagePackId/review`）：薄弱知识点展示、错题模式分布、一键生成专项练习
- **`SprintDashboard.vue`**（路由：`/course/:languagePackId/sprint`）：冲刺计划生成/管理、通过风险仪表盘（dashboard gauge chart）、任务时间线
- **NavBar** 新增"我的课程"导航入口

---

## 阶段三：AI 治理一体化

### 完成功能

让所有 AI 能力进入统一 Harness 治理框架，从功能点变成可治理的系统。

#### StoppingCondition 接线
- `LlmClient.callWithTools` 方法签名增加 `StoppingCondition` 参数
- ReAct 循环内强制检查：`maxIterations`（迭代上限）、`maxRepeatToolCalls`（同一工具重复调用上限）、`timeoutSeconds`（总超时时间）
- 超出阈值时 fail-fast 抛出 `IllegalStateException`
- 向后兼容：无参调用自动使用 `StoppingCondition.defaults()`

#### 统一 Tool Governance
- `TutorToolRegistry` 新增 `getToolsForDomain(ToolDomain, ToolContext)` 方法
- 按域（TUTOR/QA）严格过滤工具，跨域调用 fail-fast
- `ToolContext.languagePackId` 非空校验

#### Trace 统一
- **`AiTraceService`**：
  - `generateTraceId()` → 生成唯一追踪 ID
  - `recordTrace(traceId, languagePackId, domain, iterations, entries)` → 写入 `ai_workflow_event`
  - `getTraceDetails(traceId)` → 按 trace_id 回放完整调用链
  - `getQualityReport(languagePackId)` → 课程级 AI 质量报告

#### Eval Harness 实用化
- `QaEvalHarness` 增加 `@Scheduled(cron = "0 0 3 * * *")` 每日凌晨 3:00 自动采样评估
- `TutorEvalHarness` 增加 `@Scheduled(cron = "0 30 3 * * *")` 每日凌晨 3:30 自动采样评估
- 评估结果写入 `RolloutPolicyService.evaluateHarnessGate`，形成自动化质量闸门

#### Observability API
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/ai/traces?traceId=xxx` | Trace 详情 |
| GET | `/api/admin/ai/quality-report?languagePackId=xxx` | 课程级 AI 质量报告 |
| GET | `/api/admin/ai/rollout-status` | 当前 rollout 状态 |

---

## 阶段四：考试目标显性化

### 完成功能

将"学懂"延伸到"通过期末考试"。

#### 冲刺计划生成
- 基于 `learner_kc_mastery` 获取所有 KC 掌握度
- 识别 weak KCs（mastery < 0.6）
- 按掌握度排序（先补最弱的基础）
- 按目标日期分配每日任务量
- 生成 `exam_sprint_plan` + `exam_sprint_task`

#### 模拟问答
- **`MockExamService`**：
  - `generateMockQuestions(languagePackId, chapterIds, count)` → 基于课程 KC + 语言包课件证据，LLM 生成模拟考试题目
  - 输出：题目、答案、关联 KC、难度级别

#### 通过风险评估
- `ExamSprintService.assessReadiness(userId, languagePackId)`：
  - 基于全量 KC 掌握度加权计算通过准备度（0-1）
  - 风险等级：`< 0.5` 为高风险，`< 0.7` 为中风险，`>= 0.7` 为低风险
  - 输出：`overall_readiness`、`risk_level`、薄弱 KCs 列表

---

## 阶段五：课程运营平台化

### 完成功能

让教师与管理侧进入同一数据闭环。

#### 教师洞察服务
- **`CourseInsightService`**：
  - `getClassMasteryDistribution(classroomId)` → 班级 KC 掌握度分布（含平均/最小/最大值）
  - `getCommonWeakPoints(classroomId)` → 班级共性薄弱点（平均掌握度 < 0.6 的 KCs）
  - `getStudentRiskList(classroomId)` → 高风险学生列表（含风险等级）
  - `getContentEffectiveness(languagePackId)` → 题目 AC 率排序 + 章节平均掌握度

#### 内容改进闭环
- **`ContentImprovementService`**：
  - `getHighFrequencyErrors(languagePackId)` → 高频错误模式归纳
  - `getLowEfficiencyContent(languagePackId)` → 低效内容识别（AC 率 < 30% 且提交数 >= 5）
  - `getImprovementSuggestions(languagePackId)` → 改进建议聚合

#### 管理端 API
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/insight/classroom/{id}/mastery` | 班级 KC 掌握度分布 |
| GET | `/api/admin/insight/classroom/{id}/weak-points` | 共性薄弱点 |
| GET | `/api/admin/insight/classroom/{id}/risk-students` | 高风险学生预警 |
| GET | `/api/admin/insight/content/{id}/effectiveness` | 内容有效性报告 |
| GET | `/api/admin/insight/content/{id}/improvements` | 改进建议 |

#### 前端管理页面
- **`TeacherInsight.vue`**（管理后台）：教师洞察看板，含班级 KC 掌握度热力表、共性薄弱点排行、高风险学生预警列表、内容有效性报告

---

## 新增文件清单

### 数据库迁移
- `V40__language_pack_course_enrichment.sql`
- `V41__learner_course_progress.sql`

### 后端 Java
- `service/languagepack/CourseStructureService.java`（接口）
- `service/languagepack/impl/CourseStructureServiceImpl.java`（实现）
- `service/aitutor/profile/LearnerMasteryServiceUnified.java`
- `service/aitutor/AiTraceService.java`
- `service/impl/LearnerCourseProgressService.java`
- `service/impl/ExamSprintService.java`
- `service/impl/MockExamService.java`
- `service/impl/CourseInsightService.java`
- `service/impl/ContentImprovementService.java`
- `controller/CourseStructureController.java`
- `controller/CourseProgressController.java`
- `controller/AdminAiObservabilityController.java`
- `controller/AdminCourseInsightController.java`

### 前端 Vue
- `views/languagepack/CourseOverview.vue`
- `views/languagepack/CourseLearningHub.vue`
- `views/languagepack/ReviewCenter.vue`
- `views/languagepack/SprintDashboard.vue`
- `admin/views/general/TeacherInsight.vue`

### 修改文件
- `LlmClient.java` — StoppingCondition 接线
- `TutorToolRegistry.java` — 按域隔离
- `RolloutPolicyService.java` — isEnabled 开关
- `QaEvalHarness.java` — 定时采样
- `TutorEvalHarness.java` — 定时采样
- `NavBar.vue` — "我的课程"导航
- `api.js` — 新增全部 API 方法
- `routes.js` — 新增课程相关路由
- `languagepack/index.js` — 导出新组件

---

## 架构视图

```
Course Layer (阶段一)
  └─ language_pack → chapter → KC (+ prerequisite) → example / problem / review_task

Learning Layer (阶段二)
  ├─ Concept: QA + 章节浏览
  ├─ Practice: 做题 + 掌握度追踪
  ├─ Review: 薄弱点 + 错题模式
  └─ Exam: 冲刺 + 模拟 + 风险评估

Intelligence Layer
  ├─ Agent + ReAct (StoppingCondition 强制)
  ├─ RAG: page retrieval
  ├─ Memory: Learner Memory
  ├─ Eval: 定时自动评估
  └─ Mastery + Readiness

Harness Layer (阶段三)
  ├─ RuntimeContract
  ├─ StoppingCondition (全链路强制)
  ├─ Tool Governance (按域隔离)
  ├─ Trace + Replay
  └─ Rollout + Grader

Operations Layer (阶段五)
  ├─ 班级掌握度分布
  ├─ 共性薄弱点排行
  ├─ 高风险学生预警
  └─ 内容改进闭环
```

## 未验证前提

- 章节 `learning_objective` 的 LLM 自动生成质量需要在实际初始化流程中验证
- KC 前驱关系的拓扑排序假设 KC 图无环（需在插入时校验）
- 冲刺计划的每日任务量分配假设学生每日可投入时间大致均匀
- 通过风险评估的章节权重当前为等权，实际可能需要按考试大纲调整
- `MasteryService` 的 EMA 参数（alpha=0.7）是否适合统一掌握度模型需要回测验证
