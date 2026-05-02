# Alethicode OJ 项目设计说明书（详细版）

> 文档版本：v1.0  
> 更新时间：2026-03-28  
> 适用仓库：`/home/cypress/Alethicode`  
> 目标读者：产品、后端、前端、测试、运维、教学教研团队

---

## 1. 项目背景与定位

### 1.1 业务背景
Alethicode 是一个面向编程教学的在线判题（OJ）与智能教学融合系统。项目目标不是单纯“做题 + 判题”，而是构建从“审题 -> 思考 -> 编码 -> 错误修复 -> 迁移练习”的完整学习闭环。

### 1.2 目标人群
- 主要用户：非计算机专业的 Python 初学者
- 教学角色：教师、助教、教研团队
- 运营角色：平台管理员、系统管理员

### 1.3 项目定位
- 学习平台定位：可引导、可诊断、可复盘，而非只返回 AC/WA
- AI 定位：教学策略执行器，不是开放式“万能聊天机器人”
- 工程定位：强契约、可追踪、可评测、可灰度、可回滚

---

## 2. 建设目标与非目标

### 2.1 总体目标
1. 保持 OJ 主业务稳定（账户、题库、提交、判题、管理）
2. 在题目页内构建单入口 AI 学习主线（`/api/ai/workflow/*`）
3. 用服务端策略与证据层约束 AI 输出，避免答案泄露和无边界对话
4. 建立可量化的评测、灰度、回滚机制

### 2.2 非目标（当前阶段）
1. 不新增第二个学生侧 AI 面板
2. 不新增脱离题目页的自由聊天入口
3. 不做“先上复杂模型、后补治理”的路径
4. 不做静默降级与兜底分支，契约不合法直接 fail-fast

---

## 3. 第一性原理与设计原则

1. **学习目标优先**：系统输出服务于学习推进，不服务于“回答看起来很聪明”
2. **契约先于实现**：`Phase/Event/Card/Feedback/PendingAction` 为运行时单一事实源
3. **策略先于生成**：先决定“做什么动作”，再生成“动作内容”
4. **证据先于答案**：所有卡片由统一 `EvidencePack` 驱动
5. **治理先于扩展**：无评测/灰度/回滚不放量
6. **最短正确路径**：最小改动满足闭环，不做补丁式并行路径

---

## 4. 系统总体架构

### 4.1 逻辑分层

1. 接入层：前端 Vue2 页面、REST API、WebSocket
2. 编排层：工作流状态机、教学动作策略、bandit rerank
3. 领域层：题目、提交、账户、教室、AI 教学域服务
4. 数据层：PostgreSQL（Flyway）、Redis（Session/缓存）
5. 执行层：Judge 服务（容器化沙箱判题）
6. 治理层：Trace、Eval、Rollout、Load Test

### 4.2 组件拓扑（简图）

```text
[Vue2 Frontend]
   |  HTTP(/api) + WS(/ws/workflow/*)
   v
[Spring Boot Backend]
   |- Controller
   |- Domain Service
   |- Workflow Orchestrator + Policy
   |- Eval/Rollout/Governance
   |          |                 \
   |          |                  -> [k6 Load Tests]
   v
[PostgreSQL + Flyway] <-> [Redis]
   |
   -> [Judge Server]
```

### 4.3 关键技术栈

- 后端：Java 21、Spring Boot 3.4.x、Spring Security、JPA、JdbcTemplate、Flyway、Redis、WebSocket
- 前端：Vue2 + Vue Router 3 + Vuex 3 + axios + iView + ECharts
- 数据库：PostgreSQL 16+
- 压测：Docker Compose + k6
- 算法离线基线：Python + PyTorch（KT baseline）

---

## 5. 核心业务域设计

### 5.1 账户与权限域
- 认证入口：`/api/login`、`/api/logout`、`/api/profile`
- 安全机制：Session + CSRF（`csrftoken` + `X-CSRFToken`）
- 角色模型：`ROLE_USER` / `ROLE_ADMIN` / `ROLE_SUPER_ADMIN`
- 关键实现：`SessionAuthenticationFilter`、`EnsureApiCsrfCookieFilter`

### 5.2 题库域
- 学生侧：题目列表、详情、统计
- 管理侧：CRUD、导入导出、测试用例管理
- AI关联：`ai_problem_kc_mapping` 建立题目与 KC 映射

### 5.3 提交与判题域
- 提交链路：`/api/submission` -> 后端调度 -> Judge
- 能力：提交查询、重判、调试、节流控制、附属行为采集
- 语言配置：Java 后端在 `SubmissionServiceImpl.resolveLanguageConfig` 中统一维护语言模板与编译/运行沙箱参数

### 5.4 教室与课堂域
- 覆盖课堂会话、成员、课程、作业、协同监控与 AI 生成题
- 与 OJ 主站共享用户与题目基础能力

### 5.5 AI 教学域（重点）
- 学生主入口：`/api/ai/workflow/session|event|checkpoint|interrupt`
- 管理入口：`/api/admin/ai/*`（评审、知识组件、诊断等）
- 运行内核：`AITutorWorkflowAdminServiceImpl`

---

## 6. AI 大一统主链路设计

### 6.1 运行时契约

后端枚举定义为唯一事实源：
- `Phase`：`READING/IDEATING/SCAFFOLDING/CODING/ERROR_FEEDBACK/AC_REVIEW/TRANSFER`
- `WorkflowEvent`：包含主线事件与辅助事件（`CHAT`、`AGENT_FEEDBACK`）
- `CardType`：`problem_guide/ideate_analysis/parsons_problem/code_companion/error_diagnosis/post_ac/transfer_problem/ai_reply`
- `PendingHumanAction`：`confirm_scaffold`、`confirm_transfer`
- `FeedbackLabel`：`helpful/unhelpful/confusing`

### 6.2 状态机与流转策略

- `TransitionPolicy` 负责：
  1. phase-event 合法性
  2. pending action 阻塞（如 `confirm_transfer` 阶段禁止直接 coding）
  3. checkpoint restore 合法性
- 非法流转：直接抛错（fail-fast），不做静默纠偏

### 6.3 证据层（EvidencePack）

`EvidencePackAssembler` 在卡片生成前统一组装证据：
1. 题目上下文
2. 会话与当前 phase
3. 最近提交与代码快照
4. KC 与章节
5. courseware 检索命中
6. 风险信号（如 answer leak probe）
7. learner short-term / long-term 状态

### 6.4 学习者画像（LearnerState）

`LearnerState` 结构：
- `mastery_by_kc`
- `weak_kcs`
- `misconception_distribution`
- `frustration_level`
- `confidence_proxy`
- `recommended_action_bias`
- `memory_refs`

`LearnerProfileProjector` 将行为与历史事件投影为画像，并持久化快照到 `ai_learner_profile_snapshot`。

### 6.5 教学动作决策

1. `TutorActionPolicy`：规则层从有限动作集中做首选动作
2. `ContextualBanditReranker`：在规则候选上做 rerank（可关/可 dark launch）
3. 模型职责边界：只生成已选动作对应的卡片内容，不直接决定教学策略

### 6.6 卡片协议与强校验

- `CardSchemaRegistry` 定义每类卡片必填字段
- `CardSchemaValidator` 在模型返回后强校验
- 违规处理：记录 `schema_violation` 到 trace，并失败返回

### 6.7 治理链路（Trace/Eval/Rollout）

1. `TraceGradeService`：对单次决策打分（schema/pedagogy/helpfulness/answer_leak/...）
2. `AITutorEvalService`：汇总评测指标与 reward
3. `OffPolicyEvalService`：OPE 估计（IPS/有效样本阈值）
4. `RolloutPolicyService`：按阈值输出 `baseline/dark_launch/gray/rollback`

### 6.8 长期记忆与跨课程画像

- `LearnerMemoryService` 只读取 `enabled=true` 且未过期记忆
- `CrossCourseProfileService` 提供跨课 bias（如跨课弱 KC）
- 使用原则：长期记忆作为策略特征，不直接原文拼接到 prompt

---

## 7. 数据库设计

### 7.1 核心 OJ 表（已有）
- 用户：`user`、`user_profile`
- 题目：`problem`、`problem_tag` 等
- 提交：`submission`
- 判题服务：`judge_server`

### 7.2 AI 主线表（关键）
- 会话与轨迹：`ai_workflow_session`、`ai_workflow_event`、`ai_workflow_checkpoint`
- 学习行为：`ai_learning_event`、`ai_code_snapshot`、`ai_calibration_state`
- 知识映射：`ai_problem_kc_mapping`、`ai_knowledge_component`

### 7.3 新增治理与画像表（V17）
- 反馈：`ai_feedback_label`
- Trace：`ai_tutor_trace`
- 生成日志：`ai_tutor_generation_log`
- 检索日志：`ai_retrieval_log`
- 画像快照：`ai_learner_profile_snapshot`
- 课件切片：`ai_courseware_chunk`
- 评测：`ai_eval_dataset`、`ai_eval_run`
- 灰度决策：`ai_rollout_decision`
- 长期记忆：`ai_learner_memory`

### 7.4 存储原则
1. 在线决策所需数据与离线评测数据分表
2. 关键链路全部可追溯（session_id 贯穿）
3. 仅保存可解释摘要，不保存不必要敏感内容

---

## 8. API 设计

### 8.1 学生公开 API（保持稳定）
- `GET/POST/DELETE /api/ai/workflow/session`
- `POST /api/ai/workflow/event`
- `GET /api/ai/workflow/checkpoint`
- `POST /api/ai/workflow/checkpoint/restore`
- `POST /api/ai/workflow/interrupt`

### 8.2 设计约束
1. 不新增第二入口
2. 不做兼容别名扩散到 workflow 主链
3. 可执行动作由服务端 `available_actions` 下发，前端不自造状态

### 8.3 管理与内部接口
- 管理域沿 `AdminAITutorController` 提供评审、诊断、知识组件维护
- 新增 internal/admin 接口时遵守统一 API 设计原则（资源命名、幂等、错误码一致）

---

## 9. 前端架构设计

### 9.1 当前基线
- 技术栈：Vue2 + vue-router3 + vuex3
- OJ 入口：`frontend/src/pages/oj/index.js`
- 状态机核心：`frontend/src/pages/oj/views/problem/workflowStateMachine.js`
- 统一面板：`UnifiedAgentPanel.vue`

### 9.2 题目页 AI 交互模式
1. 页面触发事件（READING/IDEATING/.../CHAT）
2. 工作流状态机统一派发到 `/api/ai/workflow/event`
3. 后端返回结构化卡片数据
4. 前端按 `CardType` 渲染组件

### 9.3 前端契约保护
- 镜像常量文件：`agentContracts.js`
- 契约测试：`frontend/tests/unit/workflow-private-ai-contract.spec.js`
- 目标：防止“前端本地发明协议字段”

### 9.4 Vue3 迁移关系（todo_frontend）
- 迁移目标是“视觉与交互不变”，不是“样式重做”
- 推荐路径：`frontend` 保留只读，迁移到 `frontend`
- 与本说明书关系：前端框架升级不改变后端 workflow 主契约

---

## 10. 部署与运行设计

### 10.1 本地/开发
- 后端：`mvn spring-boot:run -Dspring-boot.run.profiles=dev`
- 前端：npm scripts
- 数据：PostgreSQL + Redis

### 10.2 Docker 一体化部署
- `deploy/docker-compose.yml` 提供 `postgres + redis + backend + judge + frontend`
- 访问：前端 `:18080`，后端调试 `:8081`

### 10.3 压测专用配置
- `deploy/docker-compose.loadtest.yml` 开启 `dev,loadtest`
- `LoadTestProfileConfig` 注入 deterministic mock LLM 与压测种子数据
- k6 脚本：`load_tests/ai_workflow/workflow_smoke.js`、`workflow_stress.js`

---

## 11. 性能与容量设计

### 11.1 性能目标（建议）
1. workflow 主链接口 p95 < 1500ms（smoke）
2. stress 场景错误率 < 5%
3. stuck_session_count 维持阈值内

### 11.2 性能风险点
1. LLM 外部依赖波动
2. 并发异步事件与中断竞争
3. 大 JSONB 写入导致 trace 表膨胀

### 11.3 对策
1. 压测时使用 mock LLM 去除外部噪声
2. 单 session 运行任务排他（运行中拒绝重复）
3. 关键索引与日志摘要化

---

## 12. 安全、合规与治理设计

### 12.1 安全控制
- CSRF 强制注入与校验
- Session 用户态恢复
- 权限分层（学生/管理员/超管）

### 12.2 教学安全控制
1. answer leak guardrail
2. 测试/考试场景禁援助策略
3. 风险事件可追踪、可人工介入

### 12.3 未成年人保护
- 默认遵循“可限制、可关闭、可审计”策略
- 重点记录可回溯证据，不鼓励无限自由对话

---

## 13. 测试与质量保证设计

### 13.1 后端测试分层
1. Controller Contract Test
2. Service Unit Test
3. Integration Test（按职责拆分）

### 13.2 AI 工作流关键测试链路
- 非法 phase/event 拒绝
- `pending_human_action` 阻塞
- checkpoint 非法恢复
- schema violation 可追踪
- retrieval hit/miss
- memory disabled/expired
- bandit disabled/dark/gray
- answer leak -> rollout rollback

### 13.3 前端测试
- 单测位于 `frontend/tests/unit/`
- 以“一个 spec 一个契约点”为主

### 13.4 压测
- smoke：开发回归
- stress：容量与稳定性
- 输出阈值：`error_rate`、`p95`、`stuck_session_count`、吞吐

---

## 14. 三波实施与里程碑

### 第一波：骨架收口（PR-0 ~ PR-3）
1. 契约冻结与 feedback 落库
2. TransitionPolicy 强状态机
3. EvidencePack 装配与日志链路
4. Card schema 强校验

### 第二波：冷启动可用（PR-4 ~ PR-6）
1. LearnerState 投影与快照
2. courseware retrieval 接线
3. KT 离线 baseline（BKT-lite/DKT/AKT）

### 第三波：渐进个性化（PR-7 ~ PR-10）
1. TutorActionPolicy 主导动作
2. contextual bandit rerank + OPE 门槛
3. eval/red-team/rollout/rollback 全链路
4. cross-course + long-term memory 接入策略层

---

## 15. 外部优秀案例映射（联网参考）

### 15.1 Khanmigo（教学边界与评测态）
可借鉴点：
1. 学习态与测试态分离（Testing Mode）
2. 家长/教师控制与可见性
3. 年龄与账号权限约束

对本项目映射：
- 保持题目页单入口 + 正式评测场景禁援助
- 高风险输出触发 guardrail 与人工接管

### 15.2 Duolingo Max（解释式反馈）
可借鉴点：
1. Explain My Answer（解释错误机制而非只给结论）
2. Roleplay（场景化练习）

对本项目映射：
- `ERROR_FEEDBACK` 绑定错误上下文与 KC
- `AC_REVIEW` 输出可迁移学习建议

### 15.3 AutoTutor + LLM（状态机 + Guardrails）
可借鉴点：
- “教学状态 + 生成模型”组合优于自由聊天
- 强调 pedagogy 约束与 guardrails

对本项目映射：
- 服务端 `TransitionPolicy + TutorActionPolicy` 先决策后生成

### 15.4 KT 学术基线（DKT/AKT/pyKT）
可借鉴点：
1. DKT：序列建模提升预测能力
2. AKT：注意力机制 + 可解释组件
3. pyKT：统一基准、可复现实验框架

对本项目映射：
- 在线先 BKT-lite 可用，离线对比 DKT/AKT 再择优上线

### 15.5 Contextual Bandit + OPE
可借鉴点：
1. Bandit 适合在有限候选动作上在线优化
2. OPE 可用于上线前离线评估策略收益

对本项目映射：
- 默认 dark launch，OPE 达标后再灰度
- 不允许“无 OPE 直接放量”

### 15.6 Agentic Memory
可借鉴点：
1. 记忆需可验证、可过期、可关闭
2. 跨任务共享但有严格作用域

对本项目映射：
- `ai_learner_memory` 强制 `enabled/expires_at/confidence`
- 策略特征化使用，避免直接拼接原始记忆

---

## 16. 关键风险与缓解

1. **策略复杂度增长快于可观测性**  
   缓解：任何新策略必须先接 trace/eval/rollout 再启用

2. **模型能力提升掩盖教学质量下降**  
   缓解：将 pedagogy 指标与 answer leak 指标设为发布门槛

3. **前端迁移引入隐性行为漂移**  
   缓解：迁移前后契约测试 + 页面行为回归 + 双栈对照

4. **压测结果被外部依赖噪声污染**  
   缓解：loadtest profile 使用 mock LLM 保证可复现

---

## 17. 验收标准（建议）

### 17.1 功能验收
1. 学生侧仅一条 AI 主线入口
2. phase/event/card 强契约生效
3. 关键卡片 schema 强校验

### 17.2 质量验收
1. 后端 `mvn test` 全量通过
2. 前端 `npm run test -- --runInBand` 全量通过
3. k6 smoke/stress 达阈值

### 17.3 治理验收
1. 每次 AI 决策可追溯（trace）
2. rollout 有明确门槛与回滚证据
3. 关键风险场景可被红队集触发并拦截

---

## 18. 参考资料（外部）

1. Khan Academy - Khanmigo 页面：<https://www.khanacademy.org/khan-labs>  
2. Khan Academy Blog - Testing Mode：<https://blog.khanacademy.org/announcing-khanmigo-testing-mode/>  
3. Duolingo Blog - Duolingo Max（Explain My Answer / Role Play）：<https://blog.duolingo.com/duolingo-max/>  
4. AutoTutor meets LLMs（arXiv:2402.09216）：<https://arxiv.org/abs/2402.09216>  
5. Deep Knowledge Tracing（NeurIPS 2015）：<https://papers.nips.cc/paper/5654-deep-knowledge-tracing>  
6. AKT（Context-Aware Attentive Knowledge Tracing）：<https://arxiv.org/abs/2007.12324>  
7. pyKT（arXiv:2206.11460）：<https://arxiv.org/abs/2206.11460>  
8. Contextual Bandit（WWW 2010）：<https://www.microsoft.com/en-us/research/publication/a-contextual-bandit-approach-to-personalized-news-article-recommendation-3/>  
9. Unbiased Offline Evaluation（arXiv:1003.5956）：<https://arxiv.org/abs/1003.5956>  
10. Doubly Robust Policy Evaluation（arXiv:1103.4601）：<https://arxiv.org/abs/1103.4601>  
11. GitHub Blog - Agentic Memory：<https://github.blog/ai-and-ml/github-copilot/building-an-agentic-memory-system-for-github-copilot/>  
12. OpenAI Deployment Safety Hub：<https://openai.com/safety/evaluations-hub/>

---

## 19. 参考资料（仓库内）

1. AI 总纲：`todo.md`
2. 前端迁移总纲：`todo_frontend.md`
3. AI 工作流控制器：`backend/src/main/java/com/alethicode/controller/AITutorWorkflowController.java`
4. AI 工作流内核：`backend/src/main/java/com/alethicode/service/impl/AITutorWorkflowAdminServiceImpl.java`
5. AI 契约枚举：`backend/src/main/java/com/alethicode/service/aitutor/contract/`
6. 证据层：`backend/src/main/java/com/alethicode/service/aitutor/evidence/`
7. 策略与评测：`backend/src/main/java/com/alethicode/service/aitutor/policy/`、`.../eval/`、`.../rollout/`
8. 数据迁移：`backend/src/main/resources/db/migration/V17__bootstrap_oj_ai_unified_core.sql`
9. 前端题目页工作流：`frontend/src/pages/oj/views/problem/workflowStateMachine.js`
10. 压测：`load_tests/ai_workflow/`
