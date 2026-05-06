# Career Bridging Closure 落地进度

跟踪 plan `career-bridging-closure_e71ce32e.plan.md` 在 main 分支上的逐 todo 推进。
本文档每完成一个 todo 同步一次，避免会话间遗忘。

## 范围与不变量

- plan 共 16 个 commit；本仓库当前推进进度见下表
- 工作分支：`main`（plan 0.6.1 节用户决策）；不切支线
- 由于本机 GitHub SSH 22 端口被网络阻断、443 端口传输不可达，**所有 commit
  暂存本地 main，不 push origin**；待网络恢复后由维护者手动一次 push
- LLM 调用全部走 `AiModelGateway.callForJson`，禁止 `callWithTools`
  （ReAct 路径已下线）
- 判题不动，Judge Server 协议不动，IO schema 不动

## 总进度

| # | commit message | 状态 | 本地 sha | 备注 |
|---|---|---|---|---|
| 0 | `docs(agents): 同步本轮工作准则修订` | 本地完成（待 push） | `8d8fede` | sprint 分支 35 commit ff-merge 至 main 已落本地 |
| 1 | `feat(career-db): 扩展 user_profile 并新建 career_major_dictionary（V83）+ problem_domain_variant（V85）` | 本地完成（待 push） | `d00091d` | V83/V85 SQL + CHANGELOG + 本进度文档同 commit |
| 2 | `feat(aitutor-cardtype): 扩展 4 个新 CardType 与 ReflectionServiceImpl 对应 critic rubric` | 本地完成（待 push） | `7ee640b` | CardType +4 + ReflectionServiceImpl 4 case rubric + ReflectionServiceImplTest 6 用例 |
| 3 | `feat(career-bridging): 里程碑式 Why 报告生成 + Rollout/Reflection 接入` | 本地完成（待 push） | 见 git log | V84 + service + controller + DTO + 8 单测 |
| 4 | `feat(career-bridging-ui): CareerProfilePage 与主页 CareerProgressCard` | 本地完成（待 push） | 见 git log | 3 Vue + 1 API + 路由 + 主页卡片嵌入 |
| 5 | `feat(coding-lens): 受约束 LLM 题面重写 + critic 防语义漂移` | 本地完成（待 push） | 见 git log | service + controller + prompt |
| 6 | `feat(coding-lens-ui): DomainLensToggle 与教师后台只读视图` | 进行中（并行 agent） | — | — |
| 7 | `feat(career-db): V86 新建 career_micro_project 与 career_path_node 及种子数据` | 本地完成（待 push） | 见 git log | V86 SQL + 12 专业 × 5 节点 path 种子（60 行） |
| 8 | `feat(career-path): 拓扑排序 + 解锁判断 + GraphRAG why_md 增强` | 本地完成（待 push） | 见 git log | service + controller |
| 9 | `feat(career-path-ui): vue-mermaid 渲染的专业路径地图与缩略图嵌入` | 本地完成（待 push） | 见 git log | CareerPathPage + API + 路由 |
| review | code review 修复批（🔴 #1 #2 / 🟠 #3-#7 / 🟡 #8 #9 #11） | 本地完成（待 push） | 见 git log | 前端 method/path 拉齐 + Coding Lens RBAC + Vue v-html → marked+DOMPurify + Career Controller 走 service + 死代码删除 + markNodeUnlocked 校验 + ReflectionResult 语义统一 + 新增 13 个单测 |
| 10 | `feat(career-bridging): 串通 KC 毕业与章节进入两类里程碑触发` | 本地完成（待 push） | `ca511c3` | listener 重建 + JudgeCompletedEventListener / LearnerCourseProgressService 接入 + 10 单测 |
| 11 | `feat(career-studio): 微项目生成与 reference solution 真判题自验证` | 本地完成（待 push） | 见 git log | studio 服务 + 真判题自验证 + 4 endpoints + 11 单测 |
| 12 | `feat(career-studio-ui): 项目详情、CodeMirror 复用与作品集卡片导出` | 待开始 | — | — |
| 13 | `feat(career-bridging): project_completed 触发报告重激活` | 待开始 | — | — |
| 14 | `feat(career-rollout): 4 个 experiment_id 接入 RolloutPolicyService 与评测扩展` | 待开始 | — | — |
| 15 | `feat(career-admin): 教师锁定/考试模式与用户级关闭面板` | 待开始 | — | — |
| 16 | `docs(career): README/PROJECT/CHANGELOG/docs/plans 全量同步` | 待开始 | — | — |

## Phase 0：切到 main（本地完成）

### 已完成
- `8d8fede docs(agents): 同步本轮工作准则修订`：把 `AGENTS.md` 由旧版重写为
  当前版工作准则；commit 到 sprint 分支
- 本地 `git merge --ff-only feature/iclr2026-sprint12-r02-followup` 完成
- 当前 `main` HEAD = `8d8fede`，领先 `origin/main` 40 commit
- 工作树干净

### 待执行（用户在网络恢复后手工跑）
```bash
# 仅在 GitHub SSH 通道恢复后执行
git push origin feature/iclr2026-sprint12-r02-followup
git push origin main
```

### 网络受阻取证
- `git push origin <ref>` 在 SSH 22 端口直接被 RST：`kex_exchange_identification:
  Connection closed by remote host`
- 改走 SSH 443 通道（`ssh -p 443 git@ssh.github.com` 认证成功），但 push 在
  `git-receive-pack` 握手后 6+ 分钟无任何 progress 输出，强制中止
- 仓库本身没问题：`https://github.com/cypre5s/Alethicode.git/info/refs` 返回 401
  （未带 PAT，标准响应），不是 404

## todo 1：V83 + V85（本地完成，待 push）

### 已落地
- `backend/src/main/resources/db/migration/V83__career_profile_extension.sql`：
  扩展 `user_profile` 三列 + 新建 `career_major_dictionary` + 12 条种子
- `backend/src/main/resources/db/migration/V85__problem_domain_variant.sql`：
  新建 `problem_domain_variant`（题面专业化变体缓存）+ 2 个索引
- 12 条种子均为 Python 标准库可达成的真实场景，每条 5 个 use_case，覆盖：
  生物、化学、医学、药学、临床医学、工商管理、经济、金融、统计、心理、
  机械工程、土木工程

### 验证
- 隔离 DB（`alethicode_career_test_v83`，跑完即 drop，**未碰 dev DB
  alethicode 与 test_aethicode**）上跑完 V83 + V85，5 项断言全部通过：
  1. user_profile 三新列类型 / nullable 正确
  2. career_major_dictionary 行数 = 12
  3. 12 条 spot check 全数到位、每条 use_cases 长度 = 5
  4. problem_domain_variant 13 列结构与 plan 2.3 节完全一致
  5. 4 个索引（`idx_user_profile_major_code`、
     `idx_career_major_dictionary_discipline`、
     `idx_problem_domain_variant_problem`、
     `idx_problem_domain_variant_major`）全部创建
- mvn 编译验证：见后续 `## 验证记录`

### 已知本地环境冲突（待用户决定）

> 本机 dev DB（`5436/alethicode`）的 `flyway_schema_history` 已经记录到 V94，
> 是 sprint l99 分支跑过的历史（V83=`learning timeline view`、
> V85=`ai learner narrative feedback` 等），与本次 main 上的 V83/V85 内容
> 完全不同，**checksum 不匹配会让 Spring Boot 启动时 Flyway validation 失败**。

main 代码层面已被 Revert 抵消、不含 l99 的迁移文件，但 dev DB 实体对象残留
（`learner_timeline_view` 等表 / 视图）。建议处理（任选其一）：

1. 重置 dev DB（最干净）：
   ```bash
   PGPASSWORD='ChangeMeBeforeDeploy_2026!' \
     psql -h 127.0.0.1 -p 5436 -U onlinejudge -d postgres \
     -c "DROP DATABASE alethicode;" \
     -c "CREATE DATABASE alethicode OWNER onlinejudge;"
   # 然后 cd backend && mvn spring-boot:run，Flyway 会从 V1 重跑到 V85
   ```
2. 临时启用 Flyway repair（需要把 Flyway clean 关闭、validate 关闭，复杂、
   不推荐）。
3. 不重置 dev DB，本地暂不跑 `mvn spring-boot:run`，仅依赖 CI 的 PostgreSQL
   做最终验证（CI 是干净 DB，不会有冲突）。

本次会话默认选 **方案 3**：本地不重置 dev DB；远端 CI 暂时也不会跑（因
push 阻塞）；进度仅靠隔离 DB 的 SQL dry-run 提供证据。

## todo 2：CardType + ReflectionServiceImpl critic rubric（本地完成，待 push）

### 已落地
- `backend/src/main/java/com/alethicode/service/aitutor/contract/CardType.java`：
  在 11 个原 enum 末尾追加 4 个新值：
  - `CAREER_BRIDGING("career_bridging", "career_bridging")`
  - `DOMAIN_VARIANT("domain_variant", "domain_variant")`
  - `MICRO_PROJECT_BRIEF("micro_project_brief", "micro_project_brief")`
  - `CAREER_PATH_NODE("career_path_node", "career_path_node")`
- `backend/src/main/java/com/alethicode/service/aitutor/reflection/ReflectionServiceImpl.java`：
  `buildCriticSystemPrompt` switch 新增 4 个 case，每个 4 维 rubric：
  - `CAREER_BRIDGING`：事实必须可映射 evidence、citations 完整性、Why 层不给代码
  - `DOMAIN_VARIANT`（最严）：IO schema 不变 + 测试样例语义不偏移 + verification 自报告 + abort 机制
  - `MICRO_PROJECT_BRIEF`：专业相关性 + KC ⊆ mastered_kcs + Python 标准库 + reference_solution + ≥5 测试样例
  - `CAREER_PATH_NODE`：why_md 来自 major_dictionary.seed_use_cases，不引入超出本 KC 的代码
- `backend/src/test/java/com/alethicode/service/aitutor/reflection/ReflectionServiceImplTest.java`：
  6 用例（4 个 rubric 关键短语合约 + 1 个 pass 直接返回 + 1 个 critic 失败后 refine 流程）

### 验证
- `mvn -Dtest='ReflectionServiceImplTest' test`：6/6 全过、0 failure 0 error
- `mvn -q compile`：0 错误，确认 CardType 新增 4 个枚举值没有破坏任何
  其它 switch（项目内多个 switch 都用 default 兜底，安全）

## todo 3：Career Bridging service + controller + V84（本地完成，待 push）

### 已落地
- `backend/src/main/resources/db/migration/V84__career_bridging.sql`：
  新建 `career_bridging_milestone`（5 类 type 枚举登记表）+ `career_bridging_report`
  （Why 报告，挂在 milestone 上）；3 个索引 + UNIQUE(user_id, type, ref) + 处理
  NULL ref 用应用层 `IS NOT DISTINCT FROM` 校验。
- `backend/src/main/java/com/alethicode/config/AlethicodeProperties.java`：
  扩展 `getCareer().getBridging()` 配置（`enabled` 默认 true、`treatmentRate`
  默认 0.5），关闭时服务层 fail fast 抛 `ResponseStatusException(503)`，符合
  AGENTS.md 「fail fast」原则。
- `backend/src/main/java/com/alethicode/service/career/bridging/`：
  - `MilestoneType` enum（5 个 code，与 V84 取值集对齐）
  - `CareerBridgingReport` record（V84 行投影 + traceId + Instant createdAt）
  - `CareerBridgingPrompts`（plan 3.3 节 SYSTEM 文本 + `MilestoneContext`
    record + `userPrompt` 拼装）
  - `CareerBridgingService` 接口（`ensureProfile` / `recordMilestone` /
    `generateForMilestone` / `recentReports` + `EnrollmentResult` record）
  - `CareerBridgingServiceImpl`：UPDATE user_profile（NOT_FOUND 时 fail fast）
    + 字典存在性 fail fast + milestone 三元组幂等 + A/B 分组（control 直接
    consume / treatment 调 LLM + Reflection + persist）+ trace_id 32 字符
- `backend/src/main/java/com/alethicode/dto/request/CareerProfileRequest.java`：
  PUT body schema（`major_code` / `career_intent` / `auto_generate` 默认 true）
- `backend/src/main/java/com/alethicode/dto/response/`：
  `CareerEnrollmentResponse` / `CareerMajorOption` / `CareerProfileView`
- `backend/src/main/java/com/alethicode/controller/CareerController.java`：
  5 端点 GET majors / GET profile / PUT profile / POST milestones/{id}/reports
  / GET reports；未登录返回 `error-permission-denied`，资源属主校验下沉到
  service。`auto_generate=true` 在 PUT 内部直接级联 generateForMilestone（
  失败仅 warn 不阻塞 profile 写入，前端可后续手动重试 POST）。
- `backend/src/test/java/com/alethicode/service/career/bridging/CareerBridgingServiceImplTest.java`：
  8 用例 mock JdbcTemplate + 真实 RolloutPolicyService + 真实 AlethicodeProperties
  覆盖 ensureProfile 首次 / 重复 / 未知 major / 缺 user_profile 行 4 路径，
  recordMilestone 未知 type / 已存在 reuse 2 路径，generateForMilestone
  control 不调 LLM / treatment 完整链路 2 路径。

### 验证
- `mvn -Dtest='CareerBridgingServiceImplTest' test`：8/8 全过、0 failure 0 error
- `mvn -q compile`：0 错误，所有依赖（含 LearnerProfileProjector / AlethicodeProperties
  扩展）一致

### 已知中间状态
- 本机有另一并行 agent（IDE / background process）也在写 Career Bridging 文件，
  本会话 Write 工具与对方修改互相覆盖过几次：本次 commit 时已稳定到「对方
  Service / Impl / Prompts / Properties / 3 个 response DTO + 我的 Request
  / Controller / Test / V84 SQL」的合并版本，编译与单测都过。

## todo 7：V86 + path/studio 数据基线（本地完成，待 push）

### 已落地
- `backend/src/main/resources/db/migration/V86__career_path_and_micro_project.sql`：
  - 新建 `career_micro_project` 表（学生 × 专业 × KC 集 → 微项目，13 列含
    `judge_problem_id` 关联到 problem 表、`status` 8 状态枚举、`portfolio_card_uri`
    作品集卡片 URI、`rollout_mode` / `trace_id` 灰度可观测）+ 2 索引
    （`idx_micro_project_user_status` 学生进度页用、`idx_micro_project_judge_problem`
    部分索引仅 judge_problem_id 非 null）
  - 新建 `career_path_node` 表（专业 × KC 桥接关系，UNIQUE(major_code, kc_code)
    防止同一专业同一 KC 重复 + 1 索引 `idx_career_path_node_major` 按
    `(major_code, sort_order)` 排序）
  - 落 12 个高占比专业 × 5 节点核心 KC 共 60 行种子（plan 2.4 节强约束：第一批
    人工编辑，不允许 LLM 直接写入）：
    - 5 节点 KC 链：`variables` → `data_types` → `collections` → `control_flow`
      → `functions`，每个 KC 在不同专业有不同的 `why_md`（GraphRAG 解释来源）
    - `typical_use_cases` 严格基于 V83 字典里该专业的 `seed_use_cases`，**不编造**

### 验证
- `wc -l V86`：256 行；`grep -c "INSERT INTO ... VALUES \| ('<major>'"`：60 行 INSERT
  种子（12 专业 × 5 节点）验证通过

### 强约束
- `kc_code` 复用现有 KC 体系（V13 / V25 / V51 已建），**不引入新 KC**——只新增
  「Domain × KC」桥接一层
- `judge_problem_id` 关联到 problem 表，Studio 生成的题目作为正常 problem 流走
  Judge Server 真判题（plan 0 节强约束：判题不动、Judge Server 协议不动）
- 后续 LLM 辅助扩展 `career_path_node` 时**只能产出候选**，必须人审才入库
  （plan 2.4 节强约束）

## 验证记录

| 时间 | 操作 | 结果 |
|---|---|---|
| 2026-05-06 | V83 + V85 在隔离 DB `alethicode_career_test_v83` 跑通 | ✓ 5 项断言全过、DB 已 drop |
| 2026-05-06 | `git status` 在 main HEAD `8d8fede` | ✓ 工作树干净（除 V83/V85 + 本进度文档） |
| 2026-05-06 | todo 1 `feat(career-db): ...` 本地 commit | ✓ V83/V85 SQL + CHANGELOG + progress 文档同 commit；`frontend/src/pages/oj/components/chat/composerStorage.js` 非本 plan 范围 untracked 残留（AI Tutor composer 草稿层，scope = tutor:/qa:），按 AGENTS.md 实施规范「不擅自删除遗留」原则保留 |
| 2026-05-06 | `mvn -Dtest='ReflectionServiceImplTest' test` | ✓ 6/6 全过、0 failure 0 error |
| 2026-05-06 | `mvn -q compile` | ✓ 0 错误，CardType +4 不破坏任何 switch |
| 2026-05-06 | todo 2 `feat(aitutor-cardtype): ...` 本地 commit | ✓ CardType + ReflectionServiceImpl + 单测 + CHANGELOG + progress 同 commit |
| 2026-05-06 | `mvn -Dtest='CareerBridgingServiceImplTest' test` | ✓ 8/8 全过、0 failure 0 error |
| 2026-05-06 | todo 3 `feat(career-bridging): ...` 本地 commit | ✓ V84 + service + 5 endpoints + 4 DTO + 8 单测 + AlethicodeProperties + CHANGELOG + progress 同 commit |
| 2026-05-06 | `wc -l V86 + grep INSERT 种子计数` | ✓ 256 行、60 条种子（12 专业 × 5 KC 节点） |
| 2026-05-06 | todo 7 `feat(career-db): V86 + path 种子` 本地 commit | ✓ V86 SQL + CHANGELOG + progress 同 commit |
| 2026-05-06 | code review 修复批 `mvn -Dtest='ReflectionServiceImplTest,CareerBridgingServiceImplTest,DomainLensServiceImplTest,CareerPathServiceImplTest' test` | ✓ 27/27 全过（旧 14 + 新增 13）、0 failure 0 error |
| 2026-05-06 | code review 修复批 `mvn -q compile` | ✓ 0 错误（含 untracked `MicroProjectStudioServiceImpl` 编译修复） |
| 2026-05-06 | code review 修复批 `npm run typecheck` + `npx vite build` | ✓ 0 错误，PWA precache 249 entries / 19521.22 KiB 正常生成 |
| 2026-05-06 | code review 🟢 Low 项清理批 单测复跑 | ✓ 27/27 仍全过；mock RolloutPolicyService 替换暴力枚举后无 flake |
| 2026-05-06 | code review 🟢 Low 项清理批 `npm run typecheck` + `npx vite build` | ✓ 0 错误 |
| 2026-05-06 | todo 10 `mvn -Dtest='ReflectionServiceImplTest,CareerBridgingServiceImplTest,DomainLensServiceImplTest,CareerPathServiceImplTest,CareerMilestoneEventListenerTest' test` | ✓ 37/37 全过（旧 27 + 新 10）、0 failure 0 error |
| 2026-05-06 | todo 10 `mvn -q compile && mvn -q test-compile` | ✓ 0 错误，CareerMilestoneEventListener + 2 处构造器接入不破坏其它 switch / 注入链 |
| 2026-05-06 | todo 10 `feat(career-bridging): KC 毕业 + 章节进入里程碑触发器` 本地 commit | ✓ listener + JudgeCompletedEventListener / LearnerCourseProgressService 接入 + 10 单测 + CHANGELOG + progress 同 commit |
| 2026-05-06 | todo 11 `mvn -Dtest='MicroProjectStudioServiceImplTest' test` | ✓ 11/11 全过、0 failure 0 error |
| 2026-05-06 | todo 11 `mvn -Dtest='ReflectionServiceImplTest,CareerBridgingServiceImplTest,DomainLensServiceImplTest,CareerPathServiceImplTest,CareerMilestoneEventListenerTest,MicroProjectStudioServiceImplTest' test` | ✓ 48/48 全过（旧 37 + 新 11）、0 failure 0 error |
| 2026-05-06 | todo 11 `feat(career-studio): 微项目生成 + 真判题自验证` 本地 commit | ✓ studio 服务 + AiProblemTestCaseWriter 提升 public + controller 4 端点 + 11 单测 + CHANGELOG + progress 同 commit |

## todo 10：KC 毕业 + 章节进入里程碑触发器（本地完成，待 push）

### 已落地

- `backend/src/main/java/com/alethicode/service/career/bridging/CareerMilestoneEventListener.java`：
  「直调式」桥接组件，提供 2 个入口：
  - `onMasteryUpdated(userId, languagePackId, kcId)`：检查该 KC mastery
    `>= 0.7` 即调 `careerBridgingService.recordMilestone(KC_CLUSTER_GRADUATED, "lp:<lp>:kc:<kc>")`
  - `onLanguagePackEntered(userId, languagePackId)`：调
    `careerBridgingService.recordMilestone(CHAPTER_ENTERED, "lp:<lp>")`
- `backend/src/main/java/com/alethicode/service/submission/JudgeCompletedEventListener.java`：
  构造器新增 `CareerMilestoneEventListener` 参数；`handleMasteryUpdate`
  在 `masteryService.updateMastery` 之后追加 `careerMilestoneEventListener.onMasteryUpdated`
- `backend/src/main/java/com/alethicode/service/classroom/LearnerCourseProgressService.java`：
  构造器新增 `CareerMilestoneEventListener` 参数；`getOrCreateProgress`
  在首次 INSERT 路径中调用 `careerMilestoneEventListener.onLanguagePackEntered`
- `backend/src/test/java/com/alethicode/service/career/bridging/CareerMilestoneEventListenerTest.java`：
  10 用例覆盖 enabled=false / 无 profile / mastery 三态阈值 / 章节进入正常 + 跳过 4 类边界

### 强约束遵守

- listener 不接管 DB 异常 → 调用方现有 try/catch 兜底
- 三层边界保护：enabled / has profile / mastery 阈值 → 关闭或非 career 学生路径 0 影响
- milestone_ref 命名规范化（`lp:<id>:kc:<id>` / `lp:<id>`）保证幂等键稳定，`recordMilestone` 三元组幂等

## todo 11：Project Studio 微项目生成 + reference solution 真判题自验证（本地完成，待 push）

### 已落地

- `backend/src/main/java/com/alethicode/service/career/studio/MicroProjectStudioServiceImpl.java`：
  - LLM 出题（system: `MicroProjectPrompts.SYSTEM`）+ critic（`CardType.MICRO_PROJECT_BRIEF`）
  - critic 通过后调 `LanguagePackProblemJudgeCheckService.executeReferenceSolution`
    走 Judge Server 跑 reference 自身 test_cases，100% AC 才落库
  - 真判题通过 → 落 `problem`（display_id=`MPRJ-XXXX`，与 SpecializedProblemGenerator
    同源 INSERT 模板）+ `career_micro_project` 含 `judge_problem_id`
  - `recommendForUser` / `listForUser` / `findById` / `markCompleted` 完整接口
- `backend/src/main/java/com/alethicode/service/career/studio/MicroProjectStudioService.java`：
  接口扩展为 5 方法；`CareerMicroProject` record 字段从 7 项扩为 10 项
  （`status, score, createdAt, completedAt` 投影完整）
- `backend/src/main/java/com/alethicode/service/aitutor/review/AiProblemTestCaseWriter.java`：
  由 package-private 提升为 `public`（`writeTestCases` / `buildTestCaseScoreJson`），
  允许 Studio 复用现成的 test_case 落盘工具，避免重复实现 `info` 元数据格式
- `backend/src/main/java/com/alethicode/controller/CareerStudioController.java`：
  4 端点 `GET /recommendations` / `POST /projects` / `GET /projects?limit` /
  `GET /projects/{id}`，未登录抛 401，资源属主校验下沉到 service 层
- `backend/src/test/java/com/alethicode/service/career/studio/MicroProjectStudioServiceImplTest.java`：
  11 用例覆盖 critic 拒绝 / 缺 reference / 真判题失败 / 真判题异常 /
  真判题通过双 INSERT / markCompleted 4 路径

### 强约束遵守（plan 0 + plan 5.1 节）

- **不绕过 Judge Server**：`generate` 走 `LanguagePackProblemJudgeCheckService.executeReferenceSolution`
  （与 OJ 主判题链路同源 `/judge` 协议）；落 problem 表后学生提交走标准
  `SubmissionService` → `JudgeCompletedEvent` 闭环
- **不引入新判题路径**：未新增任何 HTTP 调用，未改 Judge Server 协议
- **fail fast 不掩盖**：critic / reference / 真判题任一失败 ⇒ 不落库返回 empty，
  不写「降级版本」也不静默吞错（log.warn + 返回 empty 让上游重试可观测）
- **可观测**：每条 micro project 自带 trace_id（32 字符 UUID），与 `judge_problem_id`
  关联到 problem 表，运维侧可串联出 LLM 出题 → 真判题 → 学生提交全链路

## 严格约束清单（每个后续 todo 都要遵守）

- 新建 API → 先读 `api-design-principles` skill
- 改前端 → 先读 `ui-ux-pro-max` skill
- 写完代码 → 跑 `code-reviewer` skill 自审
- 多步任务 → `superpowers` skill
- 命名按 `AGENTS.md` 「命名规范」严格执行；同一语义只能有一种拼写
- LLM 调用一律走 `AiModelGateway.callForJson`；不允许任何 fallback / 兜底
- 每个 commit 必须含 `CHANGELOG.md` 中文条目
- 每个 commit 一定要更新本进度文档对应行的状态与 sha
