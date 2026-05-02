# 语言包初始化实时工作记录（2026-04-02）

## 目标

- 将现有 PPT 课件完整初始化为可问答语言包。
- 在真实初始化过程中验证当前初始化代码是否可行。
- 根据真实结果修复链路问题，并最终验证课件问答可运行。

## 进行中记录

### 2026-04-02 当前接手状态

- 延续上一轮已完成的初始化健壮性修复，重点集中在：
  - `.pptx` 预览 PDF 生成不再依赖 LibreOffice。
  - 页解析阶段补写 `page_embedding`。
  - KC / 示例 / 题目生成链路改为分批多次调用模型。
  - 示例抽取 prompt 收紧为“只传当前批次相关知识点”。
  - 长事务已从长耗时 LLM 阶段移除，避免整阶段回滚掩盖真实进度。
- 当前待继续确认事项：
  - 后端完整测试是否全部通过。
  - 运行中的 8081 后端是否仍是旧实例。
  - 真实语言包初始化任务 3 是否能顺利从 `kc_ready` 继续推进到发布。
  - 发布后学生侧课件问答是否真正可用。

### 2026-04-02 本轮新增要求

- 严格遵守 `AGENTS.md`。
- 实时维护一份工作文档记录已完成动作。
- 初始化来源 PPT 目录为 `/home/cypress/Alethicode/docs/competition/ppt`。
- 模型使用 MiniMax M2.7。

### 2026-04-02 本轮已执行

- 重新读取调试、TDD、验收相关技能要求，按“先查根因、先验证再宣称完成”的流程继续执行。
- 核对工作区改动状态，确认当前语言包 QA、初始化链路和测试文件均已有未提交修改，需要在此基础上继续推进，不能误回退用户已有工作。
- 确认之前用于测试和旧后端的进程 PID 已不存在，后续需要重新启动最新实例再做真实初始化。
- 确认 PPT 源目录 `/home/cypress/Alethicode/docs/competition/ppt` 可用，包含 7 个章节 `.pptx` 文件。
- 核对 PostgreSQL 中的真实初始化状态：
  - 当前主任务为 `language_pack_init_task.id=3`，`language_pack_id=4`，阶段为 `kc_ready`。
  - 语言包 `python-basic v4` 当前为 `draft`，共有 `7` 个文档、`561` 页、`7` 个章节、`294` 个知识点。
  - `language_pack_kc_page_mapping` 已有 `605` 条映射。
  - `language_pack_example` 仍为 `0`，说明真正卡点就是示例抽取阶段。
  - 7 个文档均已完成 normalize，且每个文档已有 `canonical_path` 与 `preview_pdf_path`。
- 重新执行后端验证：
  - 运行 `mvn -q -Dtest=LlmClientTest,LanguagePackInitIntegrationTest,LanguagePackQaIntegrationTest,LanguagePackQaControllerContractTest test`，测试通过。
- 启动最新开发后端：
  - 使用 `backend/.env` 中的 MiniMax M2.7 配置启动 `spring-boot:run`。
  - 当前新实例已监听 `8081`，运行进程为 `pid=1067260`。
- 开始真实执行 `task 3` 的 `extract-examples`：
  - 管理员登录成功，可正常调用初始化接口。
  - 后端日志显示已按文档和 15 页批次推进，例如：
    - 第一章：页 `1-15`
    - 第一章：页 `16-30`
    - 第一章：页 `31-45`
    - 第二章：页 `1-15`
  - 数据库已观察到 `language_pack_example` 从 `0` 增长到 `2`，说明示例抽取已经真实入库，不再是首批即失败。
  - 持续轮询后，`language_pack_example` 已进一步增长到 `79`，说明当前示例抽取处于持续执行状态，而不是中途 silently fail。
  - 当前实现的阶段推进时机仍是“整轮示例抽取全部完成后再从 `kc_ready` 切到 `examples_ready`”，因此阶段字段在过程中保持 `kc_ready` 属于现状行为，不代表任务卡死。
- 真实示例抽取最终完成：
  - 接口返回成功，任务阶段已推进到 `examples_ready`。
  - 最终 `language_pack_example=205`。
  - 这说明当前“按文档、按 15 页批次、只携带当前批次相关知识点”的 MiniMax M2.7 示例抽取链路，已经在整套 7 章 PPT 上真实跑通。
- 开始真实执行 `generate-problems`：
  - 接口成功进入题目生成阶段。
  - 后端日志显示已按知识点逐个调用，例如：
    - `kc=1 [计算思维]`
    - `kc=30 [Python语言创立者]`
    - `kc=118 [循环结构概述]`
  - `language_pack_problem_generation_log` 已开始写入，当前已观察到 `candidate_count=4`。
- 真实题目生成最终完成：
  - 后端日志显示 `Generated 10 candidate problems for task 3`。
  - 候选题日志最终为 `10` 条。
  - 按现有状态机，`generate-problems` 完成后任务仍保持 `examples_ready`；真正阶段推进由后续 `validate-problems` 执行。
- 真实结构校验完成：
  - `validate-problems` 调用成功。
  - `language_pack_problem_generation_log` 中 `validation_status=passed` 的候选题为 `10` 条。
  - 任务阶段已推进到 `problems_validated`。
- 真实发布完成：
  - `publish` 调用成功。
  - 任务阶段已推进到 `published`。
  - `language_pack.id=4` 已变为 `published`，`problem_count=10`。
  - `classroom_language_pack` 已绑定到 `language_pack_id=4`。
- 课件问答联调完成：
  - `GET /api/language-pack-qa/packs` 返回已发布语言包 `python-basic v4`。
  - 可成功创建 QA 会话。
  - 对问题“什么是计算思维？”返回 grounded answer，包含 2 个 citation：
    - `第一章：计算工具与计算思维.pptx` 第 `42` 页
    - `第七章：归纳与抽象.pptx` 第 `3` 页
  - 证据页接口 `GET /api/language-pack-qa/packs/4/documents/15/pages/42` 能返回页标题、摘录、全文和 `preview_url`。
  - OJ 防护联调通过：发送“请直接给我这道OJ题的完整代码和题解”会返回拒答，`refusal_reason=oj_problem_question`。

## 后续待补充

- 若继续增强：
  - 为课堂补充真实学生成员，再额外从学生账号复测一次 `/api/language-pack-qa/*`。
  - 根据需要收敛启动日志级别，减少长任务下与业务无关的 `judge_server` 心跳日志噪音。

### 2026-04-02 当前重构续查

- 已开始把初始化流程从旧的 `examples_ready + KC 抽样产题` 切到新的 `units_ready + problem_packages_ready + 标准 JSON 题包`。
- 新增明确约束：
  - “基线题目数固定为 51” 只适用于当前这组 Python 基础 PPT，作为本次测试和回归验收资产，不写死为通用业务规则。
  - 需要在这次有人工参考 Markdown 的基础上，总结出一套没有参考文件时也可执行的 Agent 工作流，目标是尽量逼近这次人工整理质量。
- 续查确认：
  - `ExampleExtractionServiceImpl` 已经部分切到“教学单元”思路，并会写入 `courseware_units.json` 与 `language_pack_init_agent_run`。
  - `ProblemGenerationServiceImpl` 已切到 `units_ready -> problem_packages_ready`，并按 `oj_convertible=true` 的教学单元逐题生成标准 JSON 题包。
  - `ProblemValidationServiceImpl` 已切到 `problem_packages_ready -> problems_validated`，会优先校验 `problem_package_json`，同时兼容旧候选题字段重建。
  - `LanguagePackPublishServiceImpl` 已接入 `ProblemPackageWriteService`，发布阶段改为走共享题包写库核心。
  - `AdminProblemCommandServiceImpl#importProblems` 已改为走共享题包写库核心，导出/导入 round-trip 不再把 `difficulty` 硬写成 `Mid`。
- 本轮新增修正：
  - 发现 `language_pack_problem_generation_log.problem_package_json` 的数据库默认值是 `'{}'`，会把旧候选题误判成已有标准题包。
  - 已修正发布与校验阶段的加载逻辑：`'{}'` 视为“无标准题包”，回退到旧字段重建，避免旧数据被误读。
  - 发现旧候选题重建时使用了 `Map.of(...)`，在 `example_id=null` 时会触发无 message 的空指针。
  - 已改为可容纳空值的 `LinkedHashMap` 构造方式，相关发布回归已转绿。
- 本轮新增验证：
  - `LanguagePackInitIntegrationTest#publishShouldRebindClassroomToNewestPublishedVersion` 已通过。
  - 新增 `ProblemImportExportIntegrationTest` 断言：导出再导入后 `difficulty` 必须保持原值。
  - 该 round-trip 回归已通过，说明 admin 导入已切到共享写库核心且字段不再丢失。
- 本轮新增文档：
  - 新建 `docs/architecture/language-pack-init-agent-workflow.md`，把这次 51 题对照中总结出的“无参考文件也尽量抽全”的 Agent 工作流沉淀成通用规范。
- 下一步执行顺序已经锁定：
  - 继续跑后端回归套件，确认这轮结构性改动没有带来新的联动失败。
  - 补管理端展示、基线 JSON 查询与覆盖率报告读取。
  - 最后补 `CHANGELOG.md` 和正式代码审查。

### 2026-04-02 回归续修

- 本轮修复：
  - `LanguagePackInitAuditServiceImpl` 新增 `resolveModelName`，保证 `language_pack_init_agent_run.model_name` 始终写入稳定非空值，不再因为测试环境下 `LlmClient` 被 mock 后默认返回 `null` 而提前打断初始化。
  - `LanguagePackInitIntegrationTest` 补齐 `llmClient.readEnvOrDefault(...)` 的默认桩，避免 Agent 审计链路在集成测试里失真。
  - `LanguagePackInitIntegrationTest` 的题目生成夹具已升级到新语义：
    - `language_pack_example` 现在明确写入 `document_id`
    - `source_title`
    - `unit_type`
    - `oj_convertible=true`
    - `source_signature`
  - 这次修正后，`generate-problems` 回归不再依赖旧的“默认 false 也能生成”隐含假设，测试口径已与新流程对齐。
  - 测试初始化的 `root` 账号插入改为幂等写法，避免误并行运行回归时先被 `user.username` 唯一键打断。
- 本轮验证：
  - 串行运行 `mvn -q -Dtest=LanguagePackInitIntegrationTest test`，整组初始化集成测试通过。
  - 继续运行
    `mvn -q -Dtest=LanguagePackCoverageBaselineSupportTest,AdminLanguagePackControllerContractTest,LanguagePackInitIntegrationTest,ProblemImportExportIntegrationTest,AdminProblemCommandServiceImplTest test`，
    组合回归通过。
- 当前结论：
  - “51 题基线只属于这组 Python 基础 PPT”的专项约束已经被保留在基线支持与文档里，没有被错误推广成通用业务规则。
  - 新流程已经具备“有参考时可做覆盖率对账、无参考时仍按 Agent 工作流分阶段逼近人工质量”的双轨能力。

### 2026-04-02 抽题质量拉升专项落地

- 本轮实现目标：
  - 把初始化抽题质量从“旧的单阶段示例抽取 + 10 题级别产出”推进到“围绕题源召回质量的 3+1 Agent 流水线”。
  - 对 `python-basic` 这组测试 PPT，正式把 `51` 题基线接入发布前硬门槛。
  - 对无参考文件的新课件，新增章节级高风险识别与 `review_required` 阻断，防止明显漏题的任务被直接发布。
- 本轮实现内容：
  - `ExampleExtractionServiceImpl` 已重构为：
    - `CoursewareSegmentationAgent`
    - `CoursewareUnitExtractionAgent`
    - `OjCandidateJudgementAgent`
    - `EscalationReviewAgent`
  - 题源扫描策略改为按文档 `4` 页窗口、`1` 页重叠窗口执行，先切连续教学片段，再抽教学单元，再判断是否可 OJ 化。
  - 新增并稳定落库的 artifact：
    - `courseware_segments.json`
    - `courseware_units.json`
    - `oj_candidates.json`
    - `escalation_review.json`
    - `coverage_report.json`
  - `ProblemGenerationServiceImpl` 已改为只接受 `oj_candidates_ready` 阶段，并从 `oj_candidates.json` 读取最终候选题源。
  - `LanguagePackCoverageBaselineSupport` 已升级为固定覆盖率报告结构，支持：
    - `missing`
    - `extra`
    - `blocked_candidates`
    - `chapter_stats`
    - `high_risk_chapters`
    - `unresolved_review_required`
  - `LanguagePackPublishServiceImpl` 已接入覆盖率硬门槛：
    - 对 `python-basic` 这组带基线的 PPT，若 `missing` 非空、仍有高风险章节，或生成题数与最终 OJ 候选数不一致，则直接阻断发布。
    - 对无基线课件，若仍有高风险章节或未完成复核的候选题，也直接阻断发布。
  - 章节风险统计已补进完整章节清单，而不再只统计“已经进入候选池的章节”；这样即使整章在教学单元抽取阶段就被漏掉，也会在 `high_risk_chapters` 中暴露出来。
  - `ProblemValidationServiceImpl` 已新增“标题过泛且无法与 `source_title` 对齐则校验失败”的硬约束。
- 本轮验证：
  - `mvn -q -DskipTests test-compile` 通过。
  - `mvn -q -Dtest=LanguagePackCoverageBaselineSupportTest,LanguagePackInitIntegrationTest test` 通过。
  - `mvn -q -Dtest=AdminLanguagePackControllerContractTest,ProblemImportExportIntegrationTest,AdminProblemCommandServiceImplTest test` 通过。
- 本轮结论：
  - 这次实现已经把“题源召回不足”从流程结构上拆开处理，不再把漏题问题误归因到单纯的题目生成模型。
  - `python-basic` 现在具备“生成前有题源复核、生成后有覆盖率对账、发布前有基线阻断”的完整闭环。
  - 下一步若要继续逼近人工整理的 51 题质量，重点不再是补状态机，而是基于真实 PPT 跑新流水线，查看 `coverage_report.json` 的 `missing` 与 `blocked_candidates` 清单，再精修 `CoursewareUnitExtractionAgent` 的 prompt 和判定词表。

### 2026-04-02 Alethicode 正式库清理并重跑 Python -v2

- 本轮执行目标：
  - 删除 Alethicode 正式库中历史失败的 `python-basic v2/v3/v4` 语言包与其初始化任务、课堂绑定痕迹和对应测试数据目录。
  - 以 `Python -v2` 名义在正式库重新完整跑一次初始化，验证新流程在真实 MiniMax M2.7 调用下是否可用。
- 本轮新增实现：
  - 新增手工回放测试 `backend/src/test/java/com/alethicode/manual/LanguagePackAlethicodeReplayManualTest.java`，受环境变量 `ALETHICODE_MANUAL_INIT=1` 控制。
  - 该测试会在 Alethicode 正式库中：
    - 找出 `python-basic` 的历史高版本语言包与初始化任务。
    - 临时把课堂绑定切回默认的 `version=1` 语言包。
    - 删除历史发布题目对应的 `problem` 记录和 `deploy/data/test_case/<test_case_id>` 目录。
    - 删除历史初始化任务目录与预览目录。
    - 重新上传 `/home/cypress/Alethicode/docs/competition/ppt` 下的 7 个 `.pptx`。
    - 以 `name=Python -v2`、`slug=python-basic`、`primary_language=Python3` 创建新任务，并顺序执行 `parse -> extract-kcs -> extract-examples -> generate-problems -> validate-problems -> publish`。
  - 为了定位真实 KC 抽取超时问题，给 `LanguagePackInitIntegrationTest` 新增回归：
    - `extractKcsShouldSplitLongDocumentsIntoSmallBatches`
  - `KcExtractionServiceImpl` 的页批次大小已从 `30` 页收紧到 `10` 页，确保长文档会被拆成更多次 MiniMax 请求，避免首批就超时。
- 本轮真实验证：
  - Alethicode 正式库连接已确认：
    - `database=alethicode`
    - `user=onlinejudge`
    - `port=5436`
  - KC 拆批回归过程：
    - 修改前运行 `LanguagePackInitIntegrationTest#extractKcsShouldSplitLongDocumentsIntoSmallBatches`，断言失败，实际仅发生 `2` 次调用。
    - 修改后运行
      `mvn -q -Dtest=LanguagePackInitIntegrationTest#extractKcsShouldSplitLongDocumentsIntoSmallBatches,LanguagePackInitIntegrationTest#extractKcsShouldCreateSeparateChaptersPerDocument test`
      已通过。
  - 真实回放运行：
    - 执行 `ALETHICODE_MANUAL_INIT=1 mvn -q -Dtest=LanguagePackAlethicodeReplayManualTest test`
    - 历史 `python-basic v2/v3/v4` 已被删除。
    - 新的 `python-basic version=2` 已在 Alethicode 正式库中重新创建，名称为 `Python -v2`。
    - `parse` 已成功完成，`page_count=561`。
    - `extract-kcs` 不再在最早批次失败，而是推进到 `第七章：归纳与抽象.pptx pages 21-30` 后超时。
    - 最终任务 `language_pack_init_task.id=5` 被置为：
      - `stage=failed`
      - `failure_reason=LLM KC extraction failed at 第七章：归纳与抽象.pptx pages 21-30: LLM request failed: request timed out`
  - 当前 Alethicode 正式库终态：
    - `language_pack`
      - `1 | python-basic | 1 | Python基础 | published`
      - `6 | python-basic | 2 | Python -v2 | draft`
    - `language_pack_init_task`
      - `5 | 6 | failed | LLM KC extraction failed at 第七章：归纳与抽象.pptx pages 21-30: LLM request failed: request timed out`
    - `classroom_language_pack`
      - 课堂仍绑定 `language_pack_id=1`，没有误切到失败版本。
- 本轮结论：
  - “删除旧失败版本并以 `Python -v2` 名义重跑”这件事已经实际执行完毕。
  - 当前新的真实瓶颈已经收敛为：即使 KC 抽取改成 `10` 页批次，MiniMax M2.7 在正式 PPT 上仍可能在后续批次超时。
  - 这轮按要求到这里先暂停，不继续追新的修复。

### 2026-04-02 二进制回退 + 章节记忆层 + 断点续跑（代码收口）

- 本轮核心收口：
  - 补齐 `language_pack_init_batch_run` 批次表与 `LanguagePackInitBatchRunStore`，把 `extract-kcs / extract-examples / generate-problems` 的复跑与复用落成可审计能力。
  - `extract-kcs` 端落实 `32→16→8→4→2→1` 递归拆分策略，失败仅缩当前坏批次；在 `failed` 阶段允许从已有批次上下文恢复到 `kc_ready`。
  - `kc_batch_results.json / chapter_memory.json / kc_catalog.json` 已成为 KC 阶段主产物；后续阶段只读 canonical KC。
- 针对本轮测试暴露的问题修复：
  - 修复“页码漂移导致批次空 KC”：
    - 之前模型若返回了不在当前窗口内的页码，会被整条 KC 丢弃并触发 `No valid KCs extracted from batch`。
    - 现在会保留可解析页码并回退使用批次证据摘录，避免恢复重跑时被无意义打断。
  - 修复“章节记忆提示词未命中”：
    - `extract-examples` 与 `generate-problems` 的章节键改为以 `document_id + chapter_index` 为主，不再依赖 `chapter_title` 文本严格一致。
    - 当 segment/unit 的章节字段不完整时，增加文档级回溯，确保提示词仍带 `chapter_synopsis` 和邻接锚点。
  - 修复“题包写库 FK 失败”：
    - `generate-problems` 写 `language_pack_problem_generation_log.example_id` 前先校验示例是否存在，不再把 artifact 临时 ID 直接落库。
  - 修复一次测试运行中的 class 污染：
    - 发现 `target/classes` 残留坏 class（`Unresolved compilation problems`），通过 `mvn clean` 重建后恢复正常。
- 覆盖率与发布门槛新增：
  - `coverage_report.json` 新增：
    - `kc_alias_merge_count`
    - `cross_batch_merged_kc_count`
    - `resume_reused_batch_count`
    - `chapter_memory_conflict_count`
  - 发布前 gate 新增规则：
    - 无 baseline 课件在 `chapter_memory_conflict_count > 0` 时直接阻断发布。
  - 已补集成测试 `publishShouldFailWhenCoverageReportHasChapterMemoryConflictsWithoutBaseline`，确认 gate 生效。
- 本轮验证结果：
  - `mvn -q clean -DskipTests compile` 通过。
  - `LanguagePackCoverageBaselineSupportTest` 通过（2/2）。
  - `LanguagePackInitIntegrationTest` 本轮新增与关键回归通过（6/6）：
    - `extractKcsShouldFallbackByBinaryAndWriteCanonicalArtifacts`
    - `extractKcsShouldReuseCompletedBatchesWhenRetryingFailedTask`
    - `extractExamplesShouldIncludeChapterMemoryAndNeighborAnchorsInUnitPrompt`
    - `generateProblemsShouldIncludeChapterMemoryNeighborUnitsAndCanonicalKcsInPrompt`
    - `validateProblemsShouldRejectRelatedKcsOutsideCanonicalCatalog`
    - `publishShouldFailWhenCoverageReportHasChapterMemoryConflictsWithoutBaseline`
  - 额外执行整组初始化集成回归：
    - `mvn -q -Dtest=LanguagePackInitIntegrationTest test` 通过（19/19）。
  - 同步修正历史回归契约：
    - `extractKcsShouldSplitLongDocumentsIntoSmallBatches` 已更新为“长文档无超时优先 32 页大窗口一次调用，超时再拆分”，与当前二进制回退策略一致。

### 2026-04-02 Python -v2 再次真实续跑（task=6）进行中

- 本轮目标：
  - 在 Alethicode 正式库中对 `python-basic v2` 执行“先清空后重跑”后的失败任务继续推进，确保最终能进入可发布状态。
- 已执行与结果：
  - 已执行一次完整回放：
    - `ALETHICODE_MANUAL_INIT=1 mvn -q -Dtest=LanguagePackAlethicodeReplayManualTest test`
    - 旧版本清理成功，`parse` 与 `extract-kcs` 成功，任务推进到 `segments_ready`（`kc_count=349`）。
  - `extract-examples` 首次失败：
    - 错误：`LLM request failed: request timed out`。
    - 首个失败批次定位：`language_pack_init_batch_run(stage=extract-examples, document_id=36, pages=4-7)`。
  - 增加断点续跑手工测试 `LanguagePackAlethicodeResumeManualTest` 后，多次续跑失败点逐步变化：
    - 超时失败（`request timed out`）
    - 返回 JSON 非法（字符串内部未转义引号）
    - 上游连接中断（`EOF reached while reading`）
- 本轮针对性修复：
  - `LlmClient` 增加 JSON 字符串内部裸引号转义，解决 `所谓"差分"` 触发的 JSON 解析失败。
  - `LlmClient` 增加 API 重试机制（支持超时/EOF/429/5xx），减少长链路阶段的偶发中断失败。
  - 续跑时显式设置更长超时：
    - `LLM_API_TIMEOUT_SECONDS=180`
    - `LLM_API_MAX_RETRIES=3`
- 当前实时状态（本记录更新时）：
  - 续跑测试正在执行：
    - `ALETHICODE_MANUAL_RESUME=1 LLM_API_TIMEOUT_SECONDS=180 LLM_API_MAX_RETRIES=3 mvn -q -Dtest=LanguagePackAlethicodeResumeManualTest test`
  - `extract-examples` 批次在持续推进，最新可见批次区间已经推进到 `document_id=37` 的多个窗口（含 `reused/completed/running`），仍在进行中。
