# OJ AI 大一统实施总纲

> 文档状态：可直接交给工程师 / AI 执行
> 更新日期：2026-03-27
> 优先主线：学生题目页与解题闭环
> 核心目标：冷启动可用、和现有项目大一统融合、优先吸收顶会/顶刊可落地算法、全链路可评测可灰度

---

## 0. 本文的定位

这不是普通待办清单，而是实施总纲。

它要同时回答 6 个问题：

1. 现在项目里已经有什么 AI 基础，哪些能复用
2. 最终要把学生题目页做成什么样
3. 哪些前沿算法值得吸收，哪些暂时不该上
4. 每一阶段具体改哪些模块、表、接口、卡片、策略
5. 如何验证 AI 真的变强了，而不是只变复杂了
6. 如何在生产环境里安全灰度，而不是一把梭上线

---

## 1. 执行摘要

### 1.1 一句话目标

把当前 OJ 题目页升级成唯一的“AI 学习驾驶舱”：由服务端弱状态机编排教学动作，以证据层驱动 AI 输出，以学习者画像支撑个性化，以评测和灰度机制保障质量。

### 1.2 一句话路线

先做“弱状态机 + EvidencePack + 冷启动画像 + 卡片协议 + 评测门槛”，再逐步引入 AKT、跨课程冷启动迁移、上下文 bandit、偏好优化等更强算法。

### 1.3 一句话原则

不要把项目做成“更大的聊天框”；要把它做成“有阶段、有证据、有画像、有边界的学习系统”。

---

## 2. 当前项目基线判断

当前仓库已经有 AI 大一统的基础骨架：

- 学生端统一入口：`frontend/src/pages/oj/views/problem/UnifiedAgentPanel.vue`
- 学生端统一工作流：`frontend/src/pages/oj/views/problem/workflowStateMachine.js`
- 后端统一工作流控制器：`backend/src/main/java/com/alethicode/controller/AITutorWorkflowController.java`
- 后端统一工作流内核：`backend/src/main/java/com/alethicode/service/impl/AITutorWorkflowAdminServiceImpl.java`
- 后端长期学习资产：`ai_workflow_session`、`ai_workflow_event`、`ai_workflow_checkpoint`、`ai_learning_event`、`ai_code_snapshot`、`ai_calibration_state`、`ai_learner_notebook`、`ai_problem_kc_mapping`
- 后端统一模型出口：`backend/src/main/java/com/alethicode/service/LlmClient.java`

### 2.1 现阶段真正的问题

现在的问题不是“没有 AI”，而是“AI 分层还不够清晰”。

具体表现：

- phase 已经存在，但还没有成为服务端强约束规则
- 证据来源已经存在，但还没有统一装配协议
- 冷启动数据已经有一部分，但还没形成系统性策略
- 画像能力已经有轮廓，但还是“散点功能”
- 评测和灰度机制不够强，导致后续越做越难控

### 2.2 本次实施必须坚持的判断

- 不新增第二个学生 AI 面板
- 不新增脱离题目页的万能聊天机器人
- 不先上多 agent 编排，再补基础数据和策略
- 不一开始就训练重模型
- 不为了“更 AI”而牺牲可解释、可追责、可灰度

---

## 3. 北极星目标与成功标准

### 3.1 北极星目标

学生第一次进入任意题目页，即使没有历史数据，也能获得可靠、不过度、可推进下一步的 AI 学习帮助。

### 3.2 成功标准

必须同时满足以下 5 条，才算成功：

1. 冷启动用户可用
2. 学生端只有一个统一 AI 主入口
3. 输出被严格限制在教学边界内，不乱给答案
4. 能记录、回放、评测每次 AI 决策
5. 随着交互增多，系统能渐进式个性化，而不是永远同质化

### 3.3 核心业务指标

- 首次进入题目页 60 秒内 AI 触达率
- 首次会话 helpful 反馈率
- `READING -> IDEATING -> CODING` 主链路完成率
- `ERROR_FEEDBACK` 后下一次提交通过率提升
- 冷启动用户次日留存
- `TRANSFER` 使用率与通过率
- answer leak rate
- schema pass rate
- AI 输出回放覆盖率

---

## 4. 值得吸收的外部案例

## 4.1 Khanmigo

为什么值得学：

- 它不是自由聊天，而是学习引导
- 它把风险控制写进产品，而不是只写进文档
- 它明确承认 AI 会出错，并通过 red teaming、使用限制、反馈闭环持续纠偏

可直接吸收：

- 练习态和考试态分离
- 长会话风险限制
- 家长 / 教师可见性和未成年人保护
- 持续反馈和 red teaming

本项目对应动作：

- 增加“考试 / 正式作业禁援助模式”
- 增加 session 长度限制和 answer leak 风险升级策略
- 增加学生端 AI 输出可投诉、可纠错、可人工审核的回路

## 4.2 Duolingo Max / Explain My Answer

为什么值得学：

- 它把“解释为什么错”做成核心学习动作，而不是附属功能
- 它强调基于具体错误的个性化解释，而不是泛泛表扬
- 它允许学习者主动拉起解释，而不是强塞解释

可直接吸收：

- `ERROR_FEEDBACK` 从结果解释升级为“错误机制解释”
- `AC_REVIEW` 从庆祝升级为“能力归因”
- 解释是按错误对象定制，而不是模板化复读

本项目对应动作：

- 错误诊断卡片必须绑定：提交结果、代码快照、KC、误概念
- AC 复盘卡片必须解释“为什么这次做对了”
- 允许学生主动请求某类反馈，而不是只被动接收

## 4.3 AutoTutor meets LLMs

为什么值得学：

- 它证明了“有限状态教学系统 + LLM 填充状态空间”优于自由 GPT 式 tutor
- 它非常适合当前项目，因为我们已经有 phase 和卡片工作流
- 它明确强调 pedagogy 仍需人工设计，不能完全交给模型

可直接吸收：

- 用有限状态机约束教学流程
- 用 guardrails 保护 pedagogy
- 把 LLM 定位成“状态内容生成器”，不是“全能决策者”

本项目对应动作：

- phase 规则必须上移到服务端
- teaching policy 和 card renderer 分离
- 任何卡片都必须由“策略层”先决定，再由“模型层”生成内容

## 4.4 CourseAssist

为什么值得学：

- 它不是泛用聊天，而是课程专属 tutor
- 它采用 `RAG + user intent classification + question decomposition`
- 它强调 pedagogy appropriateness，而不是单纯回答正确率

可直接吸收：

- 课程资料检索
- 学生问题意图分类
- 问题分解后再回答

本项目对应动作：

- 引入课程资料索引
- 将自由输入先做 `intent -> subtask` 分流
- 将 `chat` / `ideate` 的自由问题拆成可控子任务

## 4.5 GitHub Copilot Agent Mode / Agentic Memory

为什么值得学：

- 它强调“上下文、轨迹、记忆、审查”是一体的
- 它的记忆机制不是无上限累积，而是带有有效性和失效管理
- 它的 agent 能跨文件行动，但前提是上下文和约束足够清晰

可直接吸收：

- 记忆必须显式、可验证、可过期
- AI 不应只保留对话历史，还应抽取“长期有效的学习记忆”
- 运行轨迹要能支持回放和诊断

本项目对应动作：

- 引入 `LearnerMemory` 概念，但默认 opt-in 到策略层，而不是自由拼接到 prompt
- 对长期学习记忆加入置信度、更新时间、过期策略
- 引入 trace 级诊断而不是只看最终文本

---

## 5. 前沿算法吸收策略

## 5.1 总原则

优先吸收“顶会/顶刊中已经证明有效、且能映射到当前项目数据结构”的算法思想，而不是机械复现整篇论文。

### 5.1.1 采用标准

- 必须能和当前项目表结构、模块边界兼容
- 必须有明确输入、输出、状态更新逻辑
- 必须能先离线验证，再小流量灰度
- 必须有比当前基线更强的业务解释力

### 5.1.2 拒绝标准

- 需要先积累海量数据才能启动
- 无法解释、无法回放、无法灰度
- 只提高 paper 指标但不改善学生真实体验
- 会把项目拖进重训练 / 重标注 / 重基础设施泥潭

## 5.2 算法优先级梯度

| 层级 | 算法 / 方法 | 来源 | 作用 | 是否首期上线 |
|---|---|---|---|---|
| L0 | 有限状态教学策略 + 规则引擎 + guardrails | AutoTutor meets LLMs | 控制 phase 与答案泄露风险 | 是 |
| L0 | EvidencePack 检索增强 | CourseAssist + RAG 工程实践 | 冷启动可用、课程绑定 | 是 |
| L0 | 结构化输出 + schema 校验 | 现代 LLM 工程最佳实践 | 降低协议漂移 | 是 |
| L1 | BKT-lite / mastery EMA | 经典 ITS 基线 | 低成本可解释画像 | 是 |
| L1 | DKT | NeurIPS 2015 | 序列化掌握度预测基线 | 先离线 |
| L1 | AKT | KDD 2020 | 可解释注意力 + 题目难度建模 | 先离线，后候选上线 |
| L2 | CCLMF / Cross-course transfer | NeurIPS 2023 PTADisc | 跨课程冷启动迁移 | 有足够多课程数据后 |
| L2 | Contextual Bandit for action selection | ICML 2019 + ICML 2021 | 动态选择下一教学动作 | 在 L0/L1 稳定后 |
| L3 | SFT / DPO for pedagogical alignment | 现代模型优化实践 | 把 pedagogy 风格沉淀进模型 | 评测证明 prompt 已见顶后 |
| L3 | Agentic memory with decay | 产品工程前沿 | 跨题长期个性化记忆 | 在记忆失效机制稳定后 |

## 5.3 必须吸收的顶会 / 顶刊思想

### 5.3.1 DKT：Deep Knowledge Tracing

来源：NeurIPS 2015

必须吸收的不是“RNN 结构本身”，而是：

- 学习者画像应该由交互序列驱动，而不是只靠静态标签
- 正确 / 错误的时间序列是有价值的
- 题目与概念之间可以通过交互数据挖掘出隐含结构

本项目落地：

- 先做 DKT 风格离线基线模型
- 不直接替换线上策略
- 输出成为 `mastery_by_kc` 的候选信号之一

### 5.3.2 AKT：Context-Aware Attentive Knowledge Tracing

来源：KDD 2020

必须吸收的不是“照搬整套网络”，而是：

- 学习预测要考虑时间衰减
- 题目难度和概念嵌入应具备解释性
- 注意力可用于“这次建议为什么这样给”的可解释追踪

本项目落地：

- 将 AKT 作为第二代离线画像模型候选
- 优先用它解释 `为什么推荐这题 / 为什么这个 KC 被判弱`
- 只有在离线 AUC 和在线帮助率都显著优于 BKT-lite / DKT 后才进入线上

### 5.3.3 PTADisc + CCLMF

来源：NeurIPS 2023 Datasets and Benchmarks

必须吸收的不是“大数据规模”，而是：

- 冷启动不能只靠当前课程当前题
- 跨课程学习痕迹可以迁移到新课程
- 学生的 latent proficiency 可以跨课程映射

本项目落地：

- 当课堂 / 课程 / assignment 维度数据稳定后
- 建立 `cross_course_profile`
- 用于“学生第一次进入新章节 / 新班级 / 新题型”的暖启动

### 5.3.4 pyKT 的反向提醒

来源：NeurIPS 2022 Datasets and Benchmarks

必须吸收的不是某个模型，而是结论：

- 很多 DLKT 的增益并没有想象中大
- 错误的数据切分和评估协议会造成 label leakage
- 不严谨的 KT 上线很容易看起来先进，实际上不稳

本项目落地：

- 不直接把“更复杂的 KT 模型”当成产品答案
- 每个 KT 模型都必须有严格离线协议和线上小流量验证
- 先设评测，再谈升级

### 5.3.5 Contextual Bandit

来源：ICML 2019 Warm-starting Contextual Bandits、ICML 2021 Off-Policy Confidence Sequences

必须吸收的不是“先上 bandit”，而是：

- 教学动作选择本质上是 sequential decision making
- 可以把“已有监督规则”和“在线反馈”结合，而不是二选一
- 上线前可以做 off-policy gated deployment，而不是盲目 AB

本项目落地：

- 先由规则策略产生 baseline 动作
- 再让 bandit 在有限候选动作上做 rerank
- 先用 OPE 验证，再灰度上线

## 5.4 暂时不上的前沿东西

- 端到端 RL teacher policy
- 大规模图神经网络在线推理
- 多 agent 自主协作式 tutor
- 全量 fine-tune / continual training
- 让模型直接决定 phase 和课程目标

原因：

- 当前项目还处在“统一骨架收口阶段”
- 上述方法会明显增加训练、评测、风控和解释成本

---

## 6. 目标系统设计

## 6.1 统一学生 AI 主线

唯一主线：

`READING -> IDEATING -> SCAFFOLDING -> CODING -> ERROR_FEEDBACK -> AC_REVIEW -> TRANSFER`

补充规则：

- `CHAT` 只能作为当前 phase 的辅助手段，不能绕开 phase 直接给答案
- `pending_human_action` 用于阻塞下一步，例如必须确认 scaffold、必须先看解释、必须确认恢复 checkpoint
- phase 切换必须由服务端校验，不允许只靠前端状态跳转

## 6.2 统一运行时架构

### 6.2.1 输入

- 题目：题面、样例、hint、template、reference_solution_code、language
- 学生当前态：session、phase、user input、code snapshot、submission result
- 学生历史态：learning events、code snapshots、notebook、calibration、mastery、misconception
- 课程态：KC、chapter、courseware、classroom、assignment
- 风险态：detector、guardrail、safety flags、leakage risk

### 6.2.2 处理

统一处理流程：

1. 读取 session
2. 校验 phase -> event 合法性
3. 生成 `EvidencePack`
4. 生成 `LearnerState`
5. 由 `TutorActionPolicy` 决定下一教学动作
6. 如需要模型，则由 `CardRenderer` 生成结构化卡片
7. 经过 `SchemaValidator` 和 `GuardrailService`
8. 写入 trace、event、checkpoint、feedback hook
9. 返回前端卡片

### 6.2.3 输出

输出只允许是结构化卡片，不允许自由文本裸奔。

卡片族：

- `problem_guide`
- `ideate_analysis`
- `skeleton_code`
- `parsons_problem`
- `code_companion`
- `error_diagnosis`
- `post_ac`
- `transfer_problem`
- `ai_reply`

---

## 7. 数据契约

## 7.1 EvidencePack 契约

`EvidencePack` 是本系统的关键中间层，必须独立成显式对象，而不是零散地拼 prompt。

建议字段：

| 字段 | 类型 | 含义 |
|---|---|---|
| `problem` | object | 题面、样例、hint、template、reference solution、语言 |
| `workflow` | object | 当前 session、phase、pending action、last event |
| `submission` | object | 最近提交结果、error_info、judge feedback |
| `code` | object | 当前代码、最近代码快照、diff 特征 |
| `learner_short_term` | object | 本题会话内行为特征 |
| `learner_long_term` | object | 跨题画像、mastery、misconception、review due |
| `courseware` | object | 章节、讲义片段、课程资料命中 |
| `kc` | object | 题目 KC、相邻 KC、推荐 KC |
| `risk` | object | detector、guardrail flags、answer leak risk |
| `retrieval` | object | 检索命中来源、片段、分数 |
| `meta` | object | 构造时间、版本、构造策略 |

### 7.1.1 构造规则

- 必须先构造 `EvidencePack`，再调用模型
- 构造失败直接 fail-fast
- 每次卡片渲染都要记录 EvidencePack 摘要
- 同一个 phase 的 prompt 不再直接访问数据库，而是只读 EvidencePack

## 7.2 LearnerState 契约

`LearnerState` 负责表达“系统当前认为这个学生处于什么学习状态”。

建议字段：

| 字段 | 类型 | 含义 |
|---|---|---|
| `calibrated` | bool | 是否已完成基础校准 |
| `mastery_by_kc` | map | 各 KC 掌握度 |
| `weak_kcs` | list | 当前弱项 KC |
| `misconception_distribution` | map | 误概念分布 |
| `recent_behavior` | object | 最近提交、停留、删改、重试 |
| `frustration_level` | enum | `low / medium / high / severe` |
| `confidence_proxy` | enum | 对当前题的信心估计 |
| `recommended_action_bias` | object | 对下一步动作的偏好 |
| `memory_refs` | list | 长期有效学习记忆引用 |

### 7.2.1 最小上线版本

第一版不追求复杂：

- `mastery_by_kc` 可先用 BKT-lite / EMA
- `frustration_level` 由行为规则估计
- `confidence_proxy` 由 IDEATING/CODING 信号估计
- `misconception_distribution` 由 error taxonomy 归一化

## 7.3 Card Schema Registry

每张卡片都必须有 schema，不再依赖提示词“请严格返回 JSON”。

建议新增：

- `problem_guide.schema.json`
- `ideate_analysis.schema.json`
- `skeleton_code.schema.json`
- `parsons_problem.schema.json`
- `code_companion.schema.json`
- `error_diagnosis.schema.json`
- `post_ac.schema.json`
- `transfer_problem.schema.json`
- `ai_reply.schema.json`

---

## 8. 模块拆分方案

## 8.1 后端新增模块

建议新增以下目录与核心类：

- `backend/src/main/java/com/alethicode/service/aitutor/policy/TransitionPolicy.java`
- `backend/src/main/java/com/alethicode/service/aitutor/policy/TutorActionPolicy.java`
- `backend/src/main/java/com/alethicode/service/aitutor/evidence/EvidencePack.java`
- `backend/src/main/java/com/alethicode/service/aitutor/evidence/EvidencePackAssembler.java`
- `backend/src/main/java/com/alethicode/service/aitutor/profile/LearnerState.java`
- `backend/src/main/java/com/alethicode/service/aitutor/profile/LearnerProfileProjector.java`
- `backend/src/main/java/com/alethicode/service/aitutor/profile/MasteryService.java`
- `backend/src/main/java/com/alethicode/service/aitutor/retrieval/CoursewareRetrievalService.java`
- `backend/src/main/java/com/alethicode/service/aitutor/schema/CardSchemaRegistry.java`
- `backend/src/main/java/com/alethicode/service/aitutor/schema/CardSchemaValidator.java`
- `backend/src/main/java/com/alethicode/service/aitutor/render/CardRenderer.java`
- `backend/src/main/java/com/alethicode/service/aitutor/render/PromptBackedCardRenderer.java`
- `backend/src/main/java/com/alethicode/service/aitutor/guardrail/GuardrailService.java`
- `backend/src/main/java/com/alethicode/service/aitutor/eval/AITutorEvalService.java`
- `backend/src/main/java/com/alethicode/service/aitutor/eval/TraceGradeService.java`
- `backend/src/main/java/com/alethicode/service/aitutor/rollout/RolloutPolicyService.java`

## 8.2 后端需要瘦身的现有类

重点瘦身：

- `AITutorWorkflowAdminServiceImpl.java`
- `AITutorServiceImpl.java`

目标：

- 控制器只做协议层
- workflow 主类只做 orchestration
- profile / evidence / guardrail / render / eval 分层

## 8.3 前端新增与重构模块

建议新增：

- `frontend/src/pages/oj/views/problem/agentContracts.js`
- `frontend/src/pages/oj/views/problem/agentPhasePolicy.js`
- `frontend/src/pages/oj/views/problem/useUnifiedAgentSession.js`
- `frontend/src/pages/oj/views/problem/cards/`
  - 继续沿用现有卡片组件
  - 统一 props 协议、事件协议、反馈协议

需要重构：

- `UnifiedAgentPanel.vue`
- `workflowStateMachine.js`
- `Problem.vue`

目标：

- 前端不再自己“发明 phase”
- 前端只消费服务端动作与卡片协议
- 前端可以回放 checkpoint 与 trace

---

## 9. 数据库与存储改造

## 9.1 必须新增的表

建议新增：

| 表名 | 作用 |
|---|---|
| `ai_courseware_chunk` | 课程资料切片与索引元数据 |
| `ai_retrieval_log` | 每次 AI 检索命中记录 |
| `ai_tutor_trace` | 每次工作流执行 trace |
| `ai_tutor_generation_log` | 每次模型调用记录 |
| `ai_learner_profile_snapshot` | 学习者画像快照 |
| `ai_feedback_label` | 用户 / 教师对卡片质量的标注 |
| `ai_eval_dataset` | 评测样本 |
| `ai_eval_run` | 评测运行记录 |
| `ai_rollout_decision` | 灰度、开关、版本决策记录 |

## 9.2 可以复用的现有表

- `ai_workflow_session`
- `ai_workflow_event`
- `ai_workflow_checkpoint`
- `ai_learning_event`
- `ai_code_snapshot`
- `ai_calibration_state`
- `ai_learner_notebook`
- `ai_problem_kc_mapping`
- `ai_misconception`

## 9.3 存储设计原则

- 长期画像和运行时状态分离
- 检索原文和检索日志分离
- 卡片结果和模型原始输出分离
- 运行 trace 和业务事件分离

---

## 10. 交付顺序

## 10.1 总体顺序

严格按以下顺序推进：

1. 基线盘点与协议收口
2. 服务端弱状态机
3. EvidencePack
4. Card schema 与 validator
5. 冷启动画像
6. 课程检索
7. KT 离线基线
8. 在线动作 rerank
9. 评测、红队、灰度
10. 高级个性化

原因：

- 先把“骨架”做稳，再让 AI 更聪明
- 先把“可控”做出来，再让 AI 更强
- 先把“评测”做出来，再让 AI 更复杂

---

## 11. PR 级实施计划

## PR-0：基线盘点与契约冻结

目标：

把当前学生端 AI 主链路盘清楚，冻结 phase、card、feedback、trace 的最小契约。

输入：

- 现有工作流接口
- 现有卡片组件
- 现有数据库表

处理：

- 盘点所有学生侧 `/api/ai/**`
- 区分主线接口与旁路线接口
- 冻结 phase 枚举、event 枚举、card type 枚举

输出：

- `phase/event/card` 契约文档
- 主线接口 inventory

涉及文件：

- `backend/src/main/java/com/alethicode/controller/AITutorWorkflowController.java`
- `backend/src/main/java/com/alethicode/service/impl/AITutorWorkflowAdminServiceImpl.java`
- `frontend/src/api/modules/ai.js`
- `frontend/src/pages/oj/views/problem/UnifiedAgentPanel.vue`
- `frontend/src/pages/oj/views/problem/workflowStateMachine.js`

验收：

- 能明确列出学生 AI 主链路所有 phase 与 card
- 能明确列出不属于主线的接口
- 有一份 frozen contract 可以被前后端共同引用

## PR-1：服务端弱状态机

目标：

把 phase 从“前端建议”升级为“服务端强校验”。

输入：

- 当前 `ai_workflow_session.phase`
- 当前 `workflowEvent`

处理：

- 建立 `TransitionPolicy`
- 建立 `phase -> allowed events`
- 将非法流转改为 fail-fast
- `pending_human_action` 参与二次校验

输出：

- 可信的服务端工作流控制面

涉及文件：

- `backend/src/main/java/com/alethicode/service/aitutor/policy/TransitionPolicy.java`
- `backend/src/main/java/com/alethicode/service/impl/AITutorWorkflowAdminServiceImpl.java`
- `frontend/src/pages/oj/views/problem/workflowStateMachine.js`

验收：

- 非法 phase 跳转被后端拒绝
- 前端不再绕过服务端 phase
- checkpoint 恢复也经过 phase 校验

## PR-2：EvidencePack 装配层

目标：

建立统一证据装配层，解决冷启动和 prompt 分散问题。

输入：

- 题目信息
- 学习事件
- 代码快照
- 提交结果
- KC 映射

处理：

- 实现 `EvidencePackAssembler`
- 将工作流卡片渲染改为只读 EvidencePack
- 为检索命中、证据摘要建立日志

输出：

- 每个 AI 卡片都有统一输入对象

涉及文件：

- `backend/src/main/java/com/alethicode/service/aitutor/evidence/EvidencePack.java`
- `backend/src/main/java/com/alethicode/service/aitutor/evidence/EvidencePackAssembler.java`
- `backend/src/main/java/com/alethicode/service/impl/AITutorWorkflowAdminServiceImpl.java`
- 新增 migration：`ai_retrieval_log`

验收：

- 每次卡片渲染前必有 EvidencePack
- EvidencePack 构造失败时直接 fail-fast
- 日志中可看到本次卡片使用了哪些证据

## PR-3：Card Schema Registry

目标：

让每张卡片都有明确 schema，杜绝返回结构漂移。

输入：

- 当前各类卡片协议

处理：

- 为卡片定义 schema
- 在模型输出后强制校验
- 明确 `schema_violation` 的 fallback 行为

输出：

- 稳定的卡片 JSON 契约

涉及文件：

- `backend/src/main/java/com/alethicode/service/aitutor/schema/CardSchemaRegistry.java`
- `backend/src/main/java/com/alethicode/service/aitutor/schema/CardSchemaValidator.java`
- `backend/src/main/java/com/alethicode/service/aitutor/render/`

验收：

- 所有学生主链路卡片都有 schema
- schema 失败可追踪到具体字段
- 前端不再需要猜字段

## PR-4：冷启动画像与 BKT-lite

目标：

在没有历史或历史很少时，也能形成可靠的 LearnerState。

输入：

- KC 映射
- 当前题阶段事件
- 提交结果
- calibration

处理：

- 实现 `LearnerProfileProjector`
- 首版使用 `BKT-lite + EMA + 行为规则`
- 形成 `mastery_by_kc`、`weak_kcs`、`frustration_level`

输出：

- 冷启动也可用的 LearnerState

涉及文件：

- `backend/src/main/java/com/alethicode/service/aitutor/profile/LearnerState.java`
- `backend/src/main/java/com/alethicode/service/aitutor/profile/LearnerProfileProjector.java`
- `backend/src/main/java/com/alethicode/service/aitutor/profile/MasteryService.java`
- 新增 migration：`ai_learner_profile_snapshot`

验收：

- 新用户首次进入题目页也能得到非空 LearnerState
- 不依赖大量历史数据
- 画像字段可解释

## PR-5：课程资料检索

目标：

把课程与讲义接到学生题目页工作流里。

输入：

- classroom / lesson / chapter
- 课程讲义、辅导资料、FAQ

处理：

- 建立课程资料 ingestion 流程
- 将 chunk 与章节、KC、课程关联
- 为 `problem_guide`、`ideate_analysis`、`post_ac` 提供检索证据

输出：

- 课程绑定的 RAG tutor

涉及文件：

- `backend/src/main/java/com/alethicode/service/aitutor/retrieval/CoursewareRetrievalService.java`
- 新增 migration：`ai_courseware_chunk`

验收：

- AI 回答可以引用讲义上下文
- 检索命中记录可追踪
- 冷启动题也能拿到课程解释材料

## PR-6：KT 离线基线与模型对比

目标：

建立 BKT-lite / DKT / AKT 的离线比较，不盲目上线复杂模型。

输入：

- `submission`
- `ai_learning_event`
- `ai_problem_kc_mapping`
- 历史练习序列

处理：

- 统一离线数据切分协议
- 实现 BKT-lite baseline
- 实现 DKT baseline
- 实现 AKT candidate
- 严格避免 label leakage

输出：

- 一套离线 leaderboard
- 一套是否值得上线的算法决策依据

涉及文件：

- `scripts/` 下新增离线训练与评估脚本
- `docs/` 下新增离线评估报告

验收：

- 至少能比较 BKT-lite / DKT / AKT
- 有明确切分协议
- 有 AUC / calibration / interpretability 对比

## PR-7：教学动作策略引擎

目标：

不再让模型直接决定下一步，而是让策略引擎在有限候选上决策。

输入：

- phase
- EvidencePack
- LearnerState

处理：

- 实现 `TutorActionPolicy`
- 先用规则版动作选择
- 输出下一动作、说明、置信度

输出：

- 稳定可解释的教学动作决策层

涉及文件：

- `backend/src/main/java/com/alethicode/service/aitutor/policy/TutorActionPolicy.java`
- `backend/src/main/java/com/alethicode/service/impl/AITutorWorkflowAdminServiceImpl.java`

验收：

- 每次动作都有来源
- 连续错误、挫败、冷启动、新题都可分流
- 模型只负责卡片内容，不负责主决策

## PR-8：Contextual Bandit 小流量动作 rerank

目标：

在规则候选动作基础上，让系统学会为不同学生选择更合适的下一步。

输入：

- 候选动作集
- LearnerState
- 历史 helpful / progress / submit outcome

处理：

- 用 warm-start contextual bandit 做 action rerank
- 先离线 OPE
- 再 gated deployment

输出：

- 个性化的下一教学动作推荐

涉及文件：

- `backend/src/main/java/com/alethicode/service/aitutor/policy/BanditActionRanker.java`
- `backend/src/main/java/com/alethicode/service/aitutor/eval/OffPolicyEvalService.java`

验收：

- 先过离线 OPE
- 再做小流量灰度
- 没有劣化则扩量

## PR-9：评测、红队、灰度与回滚

目标：

AI 迭代必须像后端发布一样可控。

输入：

- 真实 traces
- 合成样本
- 历史坏案例

处理：

- 构建 eval dataset
- 构建 trace grading
- 构建红队集
- 构建 rollout policy

输出：

- 发布门槛
- 回滚机制
- 版本对比能力

涉及文件：

- `backend/src/main/java/com/alethicode/service/aitutor/eval/`
- `backend/src/main/java/com/alethicode/service/aitutor/rollout/`
- 新增 migration：`ai_eval_dataset`、`ai_eval_run`、`ai_rollout_decision`

验收：

- 每次 prompt / 模型 / 检索改动都能比较
- answer leak、schema drift、pedagogy drift 都可测
- 支持灰度与回滚

## PR-10：跨课程冷启动与长期记忆

目标：

在课堂、课程、章节维度数据稳定后，做真正的跨题、跨课程个性化。

输入：

- 多课程、多班级学习轨迹

处理：

- 引入 CCLMF 类跨课程迁移思路
- 引入长期 `LearnerMemory`
- 对记忆加入置信度与过期机制

输出：

- 新题、新章节、新课程的更强冷启动

涉及文件：

- `backend/src/main/java/com/alethicode/service/aitutor/profile/CrossCourseProfileService.java`
- `backend/src/main/java/com/alethicode/service/aitutor/profile/LearnerMemoryService.java`

验收：

- 新课程冷启动比纯单课程画像更优
- 记忆可验证、可失效、可关闭

---

## 12. 评测体系

## 12.1 必建的评测维度

| 维度 | 说明 |
|---|---|
| `schema_pass` | 输出结构是否合法 |
| `pedagogy_pass` | 是否遵守教学边界 |
| `helpfulness` | 是否真正帮助推进下一步 |
| `answer_leak` | 是否泄露完整答案 |
| `context_recall` | 是否用了应使用的证据 |
| `context_precision` | 是否夹带无关证据 |
| `latency` | 是否满足交互要求 |
| `stability` | 同类输入是否稳定 |

## 12.2 必建的红队集

至少覆盖：

- 直接索要答案
- 暗示型索要答案
- 情绪绑架
- prompt injection
- 题面字段缺失
- 代码快照为空
- 编译错误伪装成逻辑错误
- 迁移题 judge 资产缺失
- 少样例 / 无历史 / 新题冷启动
- 未成年人保护相关边界

## 12.3 Trace Grading

不是只评最终文本，而是评完整 trace：

- 本次用了哪些证据
- phase 是否正确
- guardrail 是否生效
- 失败发生在哪个节点
- fallback 是否合理

---

## 13. 灰度与发布策略

## 13.1 发布原则

- 任何算法替换都先离线、再影子、再小流量、再扩量
- 没有评测不准发布
- 没有回滚不准发布

## 13.2 灰度层级

| 层级 | 范围 |
|---|---|
| L0 | 开发环境 |
| L1 | 内部测试用户 |
| L2 | 单课程 / 单班级 |
| L3 | 小比例真实学生 |
| L4 | 全量学生 |

## 13.3 回滚触发条件

- answer leak rate 超阈值
- helpfulness 明显下降
- latency 持续上升
- schema_violation 超阈值
- 某卡片类型出现大面积反馈负向波动

---

## 14. 人类接管与治理

## 14.1 必须保留的人类控制

- 教师可关闭某班级或某作业的 AI 辅助
- 教师可查看高风险卡片与异常 trace
- 管理员可快速下线某类 prompt / 某类 card / 某个模型版本
- 学生可以反馈“有帮助 / 没帮助 / 看不懂 / 泄露太多 / 明显错误”

## 14.2 未成年人保护

借鉴 Khanmigo 的思路，必须显式处理：

- 未成年人可见性与权限控制
- 敏感内容审核
- 长会话风险限制
- 教师 / 家长可监督的场景化策略

---

## 15. 明确不做的事

- 不做第二个学生 AI 产品入口
- 不做没有边界的“AI 同桌”
- 不做一开始就上线 AKT / bandit / cross-course 全家桶
- 不做没有证据的自由编故事式教学反馈
- 不做只有 demo 效果、没有评测与灰度的“前沿算法接入”

---

## 16. 推荐实施节奏

推荐分三波推进：

### 第一波：骨架收口

- PR-0
- PR-1
- PR-2
- PR-3

目标：

- 系统从“散点 AI”变成“统一 AI 工作流”

### 第二波：冷启动可用

- PR-4
- PR-5
- PR-6

目标：

- 新用户、新题、新章节也可用

### 第三波：渐进个性化

- PR-7
- PR-8
- PR-9
- PR-10

目标：

- 在不牺牲可控性的前提下，让系统逐步变聪明

---

## 17. 关键决策总结

### 17.1 先上线什么

- 有限状态教学策略
- EvidencePack
- Card schema
- 冷启动 LearnerState
- 课程资料检索
- 评测和灰度

### 17.2 先离线验证什么

- DKT
- AKT
- contextual bandit rerank
- cross-course transfer

### 17.3 暂时只观察不落地什么

- 全量 SFT / DPO
- agentic memory 自动写回
- 多 agent 自主协作 tutor

---

## 18. 外部参考与为什么选它们

### 案例

- Khanmigo 官方介绍：<https://support.khanacademy.org/hc/en-us/articles/14394953976333--Update-Introducing-Khanmigo-Khan-Academy-s-AI-Tool>
  - 价值：教育场景下负责任 AI 的产品实践
- Khanmigo 责任 AI：<https://support.khanacademy.org/hc/en-us/articles/13965308352781-What-is-Khan-Academy-s-approach-to-responsible-AI-development>
  - 价值：监控、red teaming、使用限制、反馈闭环
- Duolingo Max：<https://blog.duolingo.com/duolingo-max/>
  - 价值：AI 练习、角色扮演、个性化复盘
- Duolingo Explain My Answer：<https://blog.duolingo.com/explain-my-answer-now-free/>
  - 价值：按具体错误解释“为什么错”
- GitHub Copilot Agent Mode：<https://github.blog/news-insights/product-news/github-copilot-agent-mode-activated/>
  - 价值：上下文、工具、轨迹、agentic 工作方式
- GitHub Copilot Agentic Memory：<https://github.blog/ai-and-ml/github-copilot/building-an-agentic-memory-system-for-github-copilot/>
  - 价值：长期记忆的有效性、过期与同步问题

### 论文与技术

- Deep Knowledge Tracing, NeurIPS 2015：<https://proceedings.neurips.cc/paper/5654-deep-knowledge-tracing>
  - 价值：交互序列驱动的学习者画像
- Context-Aware Attentive Knowledge Tracing, KDD 2020：<https://arxiv.org/abs/2007.12324>
  - 价值：可解释 attention、时间衰减、题目难度
- pyKT, NeurIPS 2022 Datasets and Benchmarks：<https://proceedings.neurips.cc/paper_files/paper/2022/hash/75ca2b23d9794f02a92449af65a57556-Abstract-Datasets_and_Benchmarks.html>
  - 价值：提醒不要高估复杂 KT 模型
- PTADisc + CCLMF, NeurIPS 2023 Datasets and Benchmarks：<https://proceedings.neurips.cc/paper_files/paper/2023/hash/8cf04c64d1734e5f7e63418a2a4d49de-Abstract-Datasets_and_Benchmarks.html>
  - 价值：跨课程冷启动迁移
- AutoTutor meets Large Language Models：<https://arxiv.org/abs/2402.09216>
  - 价值：有限状态教学系统 + LLM guardrails
- CourseAssist：<https://arxiv.org/abs/2407.10246>
  - 价值：RAG + 意图分类 + 问题分解
- Warm-starting Contextual Bandits, ICML 2019：<https://proceedings.mlr.press/v97/zhang19b.html>
  - 价值：结合监督规则与在线 bandit 反馈
- Off-Policy Confidence Sequences, ICML 2021：<https://proceedings.mlr.press/v139/karampatziakis21a.html>
  - 价值：安全灰度与 gated deployment

### 工程参考

- File Search：<https://developers.openai.com/api/docs/guides/tools-file-search>
  - 价值：课程资料检索与知识库组织参考
- Evaluation Best Practices：<https://developers.openai.com/api/docs/guides/evaluation-best-practices>
  - 价值：先建 eval，再优化模型与 prompt
- Agent Evals：<https://developers.openai.com/api/docs/guides/agent-evals>
  - 价值：把 workflow 当成 agent 系统评测
- Trace Grading：<https://developers.openai.com/api/docs/guides/trace-grading>
  - 价值：不是只评最终答案，而是评 trace

---

## 19. NFK 知识追踪模型集成

> NFK = DKT (LSTM) + FoLiBiKT (遗忘注意力) + simpleKT (交叉注意力)
> 代码位置: `research/nfk/`（计划迁移到 `backend/nfk/`）
> 详细集成计划: `TODO_NFK_INTEGRATION.md`

### 19.1 当前进展

- [x] 模型架构：DKT LSTM + FoLiBiKT 遗忘线性偏置注意力 + simpleKT 交叉注意力（组件 C 已从 TSK 模糊层替换为 simpleKT）
- [x] 嵌入共享：SimpleKTAttention 共享 DKTBase 题目嵌入，参数量 756万→328万 (-57%)
- [x] 学习率调度：warmup + ReduceLROnPlateau
- [x] 基准验证：ASSISTments 2009 AUC=0.758, EdNet KT1 AUC=0.669
- [x] GPU 适配：RTX 5060 (8GB) 已验证，24GB GPU 配置已准备

### 19.2 待完成：抗过拟合增强

- [ ] **Label Smoothing（标签平滑）**
  - 位置: `research/nfk/training/loss.py`
  - 改动: BCE 的 label 从 hard (0/1) 软化为 (0.05/0.95)
  - 预期效果: +0.3-0.5% AUC，防止模型对训练标签过于自信
  - 代码:
    ```python
    smoothed = labels * (1 - smoothing) + smoothing / 2
    loss = F.binary_cross_entropy_with_logits(pred, smoothed)
    ```

- [ ] **Sequence Augmentation（序列增强）**
  - 位置: `research/nfk/models/component_a.py`
  - 改动: 训练时随机丢弃 10% 的交互步
  - 预期效果: 迫使模型不依赖特定序列位置，减少记忆化
  - 代码:
    ```python
    if self.training:
        mask = torch.rand(B, T, device=h.device) > 0.1
        h = h * mask.unsqueeze(-1)
    ```

- [ ] **Weight Decay 调优**
  - 当前 weight_decay=0.01，对 3.28M 参数的模型可能不足
  - 尝试 0.03 和 0.05，观察 val_auc 变化

### 19.3 待完成：项目集成

- [ ] **Phase 0**: 将 `research/nfk/` 迁移到 `backend/nfk/`
- [ ] **Phase A**: 验证 `ai_problem_kc_mapping` KC 映射覆盖率 > 90%
- [ ] **Phase B**: 后端新增 `NfkDataExportService` + 就绪度查询 API
- [ ] **Phase C**: 添加 ONNX Runtime 依赖 + `NfkInferenceService`
- [ ] **Phase D**: 课程包导出时可选训练 + 导入时自动加载 ONNX
- [ ] **Phase E**: Admin 页面显示 NFK 数据就绪度

### 19.4 数据飞轮

```
第 1 轮使用（BKT 冷启动）→ 学生做题积累 →
导出课程包时训练 NFK → ONNX 打包进导出 zip →
另一个老师导入 → 立即享受 NFK 精度 →
第 2+ 轮使用 → 累积数据 → 再次导出时重训 → 模型越用越准
```

---

## 20. 最后一句话

这份总纲的核心不是“把 OJ 做得更像 AI 产品”，而是：

把现有 OJ 做成一个真正懂学习流程、懂学生差异、懂风险边界、也懂如何持续变强的 AI 学习系统。
