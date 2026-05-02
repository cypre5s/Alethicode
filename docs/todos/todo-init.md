# 全局语言包一键初始化 Todo

> 文档状态：待执行
> 更新日期：2026-03-31
> 当前项目目录：`/home/cypress/Alethicode`
> 目标场景：新机器、空数据库、无既有语言包
> 初始化对象：全局语言包，不是班级，不是单门课
> 题型策略：编程题为主；客观题为可选开关，默认关闭
> 唯一目标：管理员通过一次上传课件操作，自动完成一个全局语言包的冷启动初始化，生成可用的课件知识底座、KC 体系、例题、编程题、后端配置、前端可见入口和数据库数据，使项目不再被固定为 Python，而可扩展到 Java、C++ 等语言初学者场景。

---

## 0. 总原则

### 0.1 硬边界

- [ ] 初始化对象固定为“全局语言包”，例如 `Java Beginner`、`C++ Beginner`，不绑定班级。
- [ ] 不允许继续沿用 `classroom_lesson -> ai_courseware_chunk(metadata)` 这一套课堂局部链路做补丁式扩展，必须新建语言包知识底座。
- [ ] 不允许“上传成功但知识底座不完整”；初始化任务必须以完整阶段状态推进，缺失关键阶段直接失败。
- [ ] 不允许把课件解析、KC 抽取、例题抽取、题目生成拆成互相孤立的几套数据模型，必须围绕同一个语言包域建模。
- [ ] 不允许在没有机器验证的情况下直接把 AI 题目写入正式题库。
- [ ] 不允许把“语言包初始化”和“班级开课配置”混为一谈；语言包先全局可用，班级后续再绑定。
- [ ] 不允许把“客观题可选开关”做成另一条独立初始化链；必须复用同一套文档解析和知识底座。
- [ ] 不允许扫描件静默降级；首期对不可提取文本的文件直接 fail-fast，明确返回原因。
- [ ] 不允许先支持“自由上传任何格式再想办法补”；首期支持集必须在后端契约中显式定义并强校验。

### 0.2 当前项目已知事实

- [ ] 当前课堂链路已经支持上传课件和基于页码生成 AI 题，但它服务的是班级内部功能，不是空库初始化。
- [ ] 当前课件提取脚本实际只支持 `.pdf` 与 `.pptx`，对 `.ppt/.doc/.docx` 并未真正打通。
- [ ] 当前 AI 题目生成链路把受众和参考解语言写死在 Python 初学者场景。
- [ ] 当前判题底层已经支持 `C`、`C++`、`Java`，说明“平台只支持 Python”并不是执行层问题，而是知识底座与初始化层问题。
- [ ] 当前管理端已有通用入口能力，可承载新页面和任务看板。

### 0.3 首期输入约束

- [ ] 首期必需支持：`.pdf`、`.pptx`、`.docx`。
- [ ] 首期兼容支持：`.ppt`、`.doc`，但必须先经统一转换链路转为可解析格式后再继续，不允许旁路解析。
- [ ] 不支持纯图片扫描件；若文本提取率低于阈值，初始化任务直接失败并提示重新上传可提取文本版本。
- [ ] 单个语言包允许一次上传多份课件，必须统一纳入同一个版本化初始化任务。
- [ ] 每个语言包必须有明确的 `primary_language`，例如 `Java`、`C++`、`Python3`。

---

## 1. 终态定义

### 1.1 管理端终态

- [ ] 管理端新增“语言包初始化”独立页面，入口固定在 `/admin` 内，而不是课堂页。
- [ ] 管理员可在该页面一次上传多份课件，填写语言包元信息，点击“一键初始化”后启动完整任务。
- [ ] 页面必须展示阶段进度、当前步骤、失败原因、生成统计、发布结果。
- [ ] 页面必须能查看生成出的章节、KC、例题、正式题数量及来源证据。
- [ ] 页面必须能看到本次初始化是否真正完成了“前端可见 + 后端可查 + 数据库可用”的完整闭环。

### 1.2 后端终态

- [ ] 后端存在独立的语言包初始化任务域，支持 `created -> normalizing -> parsing -> kc_ready -> examples_ready -> problems_validated -> published / failed` 明确状态。
- [ ] 后端存在独立的语言包文档域，记录原始文件、规范化文件、页级内容、文本哈希、页码、提取来源。
- [ ] 后端存在独立的语言包知识域，记录章节、KC、KC 与页、例题与页、正式题与页之间的关系。
- [ ] 后端存在独立的发布域，确保只有验证通过的题目进入正式题库。
- [ ] 后端存在独立的查询域，让前端可以直接按语言包查询文档、页、KC、例题、题目和初始化任务结果。

### 1.3 数据终态

- [ ] 空数据库启动后，不需要手工插入初始化 SQL，即可通过页面完成首个语言包冷启动。
- [ ] 每个语言包都必须带版本号和发布状态，禁止覆盖式无版本更新。
- [ ] 每道初始化产出的正式题都能追溯到 `language_pack -> document -> page -> example -> kc`。
- [ ] 每个 KC 都必须带来源页集合，不能出现“只有名字，没有证据”的 KC。
- [ ] 每个例题都必须带来源页集合，不能只存抽象描述。

---

## 2. 语言包域模型

### 2.1 核心对象

- [ ] `language_pack`
  - 语义：一个全局语言包版本。
  - 关键字段：`id`、`slug`、`display_name`、`primary_language`、`audience`、`status`、`version`、`created_at`、`published_at`。
- [ ] `language_pack_init_task`
  - 语义：一次一键初始化任务。
  - 关键字段：`id`、`language_pack_id`、`status`、`current_stage`、`config_json`、`summary_json`、`error_message`。
- [ ] `language_pack_document`
  - 语义：某份属于语言包的课件文档。
  - 关键字段：`id`、`language_pack_id`、`source_name`、`source_type`、`original_path`、`canonical_path`、`canonical_type`、`file_hash`、`page_count`。
- [ ] `language_pack_page`
  - 语义：文档的页级统一事实源。
  - 关键字段：`id`、`document_id`、`page_no`、`page_title`、`page_text`、`text_hash`、`preview_asset_path`、`metadata_json`。
- [ ] `language_pack_kc`
  - 语义：语言包内的知识组件。
  - 关键字段：`id`、`language_pack_id`、`chapter_no`、`name`、`name_en`、`description`、`difficulty_level`、`status`。
- [ ] `language_pack_kc_page_mapping`
  - 语义：KC 与来源页的映射。
- [ ] `language_pack_example`
  - 语义：从课件中抽取出的例题、例程或讲义中的练习单元。
  - 关键字段：`id`、`language_pack_id`、`example_type`、`title`、`body`、`input_spec`、`output_spec`、`source_page_range_json`、`evidence_json`。
- [ ] `language_pack_example_kc_mapping`
  - 语义：例题与 KC 的映射。
- [ ] `language_pack_problem_generation_log`
  - 语义：从例题到正式题的生成与验证日志。
- [ ] `language_pack_problem_mapping`
  - 语义：正式题与语言包知识底座的映射。

### 2.2 与现有表的关系

- [ ] `problem` 仍然是正式题库的主表，不重建第二套题表。
- [ ] `ai_knowledge_component` 可保留，但不应继续作为唯一知识源；需要新增语言包级 KC 表，再决定是否向现有 KC 表同步。
- [ ] 现有 `ai_courseware_chunk` 不作为语言包长期主表；可在迁移期保留，但首期新能力必须读写语言包专属页表。
- [ ] 现有课堂 AI 出题链路后续可改造为消费语言包底座，而不是反过来驱动语言包初始化。

---

## 3. 实施阶段

## Phase 1：语言包域与初始化任务骨架

### 阶段目标

- [ ] 在后端建立全局语言包和初始化任务的最小骨架，使“空库 -> 创建语言包任务”先具备结构化载体。

### 需要完成

- [ ] 新增 Flyway 迁移，创建语言包主表、初始化任务表、初始化阶段日志表。
- [ ] 新增后端枚举或常量，统一定义初始化状态机阶段。
- [ ] 新增 admin 侧初始化任务控制器与服务骨架。
- [ ] 新增后端契约，支持创建语言包初始化任务，但尚未真正处理文件。
- [ ] 新增最小查询接口，支持查看任务状态与空结果详情。

### 主要落点

- [ ] `backend/src/main/resources/db/migration/Vxx__bootstrap_language_pack_init.sql`
- [ ] `backend/src/main/java/com/alethicode/controller/AdminLanguagePackController.java`
- [ ] `backend/src/main/java/com/alethicode/service/languagepack/LanguagePackInitService.java`
- [ ] `backend/src/main/java/com/alethicode/service/languagepack/impl/LanguagePackInitServiceImpl.java`
- [ ] `backend/src/main/java/com/alethicode/dto/request/CreateLanguagePackInitTaskRequest.java`
- [ ] `backend/src/main/java/com/alethicode/dto/response/LanguagePackInitTaskResponse.java`

### 阶段验收标准

- [ ] 空数据库启动后，管理员可通过 API 成功创建一条初始化任务。
- [ ] 任务状态机字段完整，状态推进不依赖手工 SQL 修改。
- [ ] 非管理员调用接口会被正确拒绝。
- [ ] 状态值非法时接口直接 fail-fast。

## Phase 2：课件上传、格式规范化与 canonical 资产落库

### 阶段目标

- [ ] 把多格式课件统一规整到可解析、可预览、可追溯的 canonical 资产层。

### 需要完成

- [ ] 新增语言包文档上传接口，允许一个初始化任务一次接收多份课件。
- [ ] 存储原始文件，计算文件哈希，禁止同一任务内重复文件静默覆盖。
- [ ] 新增规范化链路：
  - [ ] `.pdf` 直接进入 canonical 流程。
  - [ ] `.pptx` 直接进入 canonical 流程。
  - [ ] `.docx` 直接进入 canonical 流程。
  - [ ] `.ppt/.doc` 必须统一通过 LibreOffice 或等价稳定转换链路先转为 `.pdf` 或 OOXML，再进入后续流程。
- [ ] 统一生成 `canonical_path` 与 `canonical_type`，供后续解析和预览复用。
- [ ] 增加文本可提取率检查，无法提取的文档直接终止任务。

### 主要落点

- [ ] `backend/src/main/java/com/alethicode/controller/AdminLanguagePackController.java`
- [ ] `backend/src/main/java/com/alethicode/service/languagepack/DocumentNormalizationService.java`
- [ ] `backend/src/main/java/com/alethicode/service/languagepack/impl/DocumentNormalizationServiceImpl.java`
- [ ] `backend/src/main/java/com/alethicode/service/languagepack/storage/LanguagePackStorageService.java`
- [ ] `backend/scripts/normalize_language_pack_document.py`
- [ ] `deploy/docker-compose.yml`
- [ ] `deploy/README.md`

### 阶段验收标准

- [ ] 上传多份课件后，数据库中能看到原始文件与 canonical 文件记录。
- [ ] `.pdf/.pptx/.docx` 能稳定进入后续流程。
- [ ] `.ppt/.doc` 转换失败时任务直接失败，并能返回明确错误。
- [ ] 同一份文件重复上传不会静默污染任务数据。
- [ ] 没有文本内容的扫描件不会被错误当成有效课件。

## Phase 3：页级解析、统一索引与预览资产

### 阶段目标

- [ ] 把 canonical 文档解析为统一的“页”事实源，并让后续所有能力都围绕页级数据工作。

### 需要完成

- [ ] 新增统一文档解析器，按文档类型提取页级文本与页级标题。
- [ ] 对每一页写入 `language_pack_page`。
- [ ] 若单页过长，允许切成多个 page chunk，但 chunk 必须可回溯到原始页。
- [ ] 统一生成页预览资产路径，保证后续问答引用点击后能定位到具体页。
- [ ] 统一为页记录保留 `document_id + page_no + excerpt + hash`。
- [ ] 为后续检索预留关键词索引和向量索引字段。

### 主要落点

- [ ] `backend/src/main/java/com/alethicode/service/languagepack/DocumentParsingService.java`
- [ ] `backend/src/main/java/com/alethicode/service/languagepack/impl/DocumentParsingServiceImpl.java`
- [ ] `backend/scripts/extract_language_pack_pages.py`
- [ ] `backend/src/main/resources/db/migration/Vxx__language_pack_pages.sql`

### 阶段验收标准

- [ ] 任意已上传文档都能在数据库中查到完整页序列。
- [ ] 页码连续且与原文档一致。
- [ ] 文档重新上传但哈希未变化时，不会重复写页。
- [ ] 文档重新上传且哈希变化时，会生成新的可追溯页数据。
- [ ] 后续模块不再依赖 `metadata->>'page_no'` 这类字符串 JSON 取值来取页。

## Phase 4：章节与 KC 抽取

### 阶段目标

- [ ] 从页级内容中抽取语言包的章节结构和 KC 结构，形成全局知识骨架。

### 需要完成

- [ ] 抽取章节候选，统一排序并编号。
- [ ] 抽取 KC 候选，去重、规范命名、生成 `name_en`。
- [ ] 为每个 KC 绑定来源页范围和章节。
- [ ] 为每个 KC 生成简洁定义，避免空洞概念名。
- [ ] 建立 `language_pack_kc_page_mapping`。
- [ ] 将语言包级 KC 与现有 `ai_knowledge_component` 的同步策略显式化：
  - [ ] 首期若需要复用现有学生画像链路，可在发布阶段同步写入 `ai_knowledge_component`。
  - [ ] 同步必须可追溯，禁止魔法式自动覆盖。

### 主要落点

- [ ] `backend/src/main/java/com/alethicode/service/languagepack/KcExtractionService.java`
- [ ] `backend/src/main/java/com/alethicode/service/languagepack/impl/KcExtractionServiceImpl.java`
- [ ] `backend/src/main/java/com/alethicode/service/languagepack/LanguagePackPublishService.java`

### 阶段验收标准

- [ ] 每个 KC 都有明确名称、描述、章节和来源页。
- [ ] 同一语义不会在同一语言包内出现多份拼写不同的 KC。
- [ ] KC 抽取结果可重复执行且幂等。
- [ ] 若无可用 KC，初始化任务直接失败，而不是进入后续题目生成阶段。

## Phase 5：例题抽取与证据绑定

### 阶段目标

- [ ] 从课件中自动抽出“例题/例程/讲义练习”，作为正式题生成的中间层，而不是直接从整页胡乱出题。

### 需要完成

- [ ] 识别课件中的例题块、代码块、输入输出说明块、练习提示块。
- [ ] 将抽出的例题结构化为 `language_pack_example`。
- [ ] 为每个例题绑定来源页、证据摘录、关联 KC。
- [ ] 对明显不构成题目的示例块直接过滤，不进入生成阶段。
- [ ] 保留例题文本原貌和归一化版本，避免后续排障时失去原始证据。

### 主要落点

- [ ] `backend/src/main/java/com/alethicode/service/languagepack/ExampleExtractionService.java`
- [ ] `backend/src/main/java/com/alethicode/service/languagepack/impl/ExampleExtractionServiceImpl.java`
- [ ] `backend/src/main/resources/db/migration/Vxx__language_pack_examples.sql`

### 阶段验收标准

- [ ] 每个例题都能追溯到至少一页来源页。
- [ ] 没有来源页的例题不得进入后续正式题生成。
- [ ] 例题与 KC 的映射不是空列表。
- [ ] 管理端可查询并预览抽取出的例题列表。

## Phase 6：编程题生成、机器验证与正式发布

### 阶段目标

- [ ] 以例题为中间层生成正式编程题，且只有验证通过后才能直接落正式题库。

### 需要完成

- [ ] 新增语言包级编程题生成服务，输入为 `language_pack + examples + kcs + target_problem_count`。
- [ ] 生成出的正式题必须包含：
  - [ ] 题目标题
  - [ ] 题面
  - [ ] 输入描述
  - [ ] 输出描述
  - [ ] 样例
  - [ ] 测试用例
  - [ ] 参考解
  - [ ] 参考解语言，固定为 `language_pack.primary_language`
  - [ ] 来源页引用
  - [ ] 关联 KC
- [ ] 新增机器验证关卡：
  - [ ] 参考解可编译
  - [ ] 参考解能通过全部生成测试
  - [ ] 样例输入输出与参考解一致
  - [ ] 题面与测试数据基本一致
- [ ] 验证通过后才允许写入 `problem` 正式题表。
- [ ] 写入 `problem` 时，`languages` 必须限制为当前语言包主语言，不允许默认开放全部语言。
- [ ] 同步建立 `language_pack_problem_mapping`，保留题目与来源页/KC 的关系。
- [ ] 客观题开关在本阶段只建立契约和扩展点：
  - [ ] `enable_objective_questions = false` 时，仅走编程题链路。
  - [ ] `enable_objective_questions = true` 时，允许在编程题之后追加选择题/填空题生成阶段。
  - [ ] 客观题链路失败时，只在开关开启的任务中构成失败。

### 主要落点

- [ ] `backend/src/main/java/com/alethicode/service/languagepack/ProblemGenerationService.java`
- [ ] `backend/src/main/java/com/alethicode/service/languagepack/impl/ProblemGenerationServiceImpl.java`
- [ ] `backend/src/main/java/com/alethicode/service/languagepack/ProblemValidationService.java`
- [ ] `backend/src/main/java/com/alethicode/service/languagepack/impl/ProblemValidationServiceImpl.java`
- [ ] `backend/src/main/java/com/alethicode/service/impl/SubmissionServiceImpl.java`

### 阶段验收标准

- [ ] 至少能稳定生成并发布一批 `primary_language` 对应的正式编程题。
- [ ] 正式题的 `reference_solution_language` 与 `languages` 与语言包主语言一致。
- [ ] 任一验证步骤失败时，该题不会进入正式题库。
- [ ] 管理端能看到本次初始化发布了多少正式题、失败了多少候选题，以及失败原因。

## Phase 7：admin 一键初始化窗口与任务编排

### 阶段目标

- [ ] 在 admin 端提供真正可操作的一键初始化窗口，而不是一堆零散接口。

### 需要完成

- [ ] 新增管理端页面，例如 `/admin/language-pack-init`。
- [ ] 页面需支持：
  - [ ] 填写语言包名称
  - [ ] 选择主语言
  - [ ] 上传多份课件
  - [ ] 配置题目数量
  - [ ] 配置客观题可选开关
  - [ ] 启动初始化任务
  - [ ] 查看阶段进度与失败原因
  - [ ] 查看生成统计与最终发布结果
- [ ] 页面必须展示本次任务的阶段时间线，而不是单一 loading。
- [ ] 页面必须支持查看初始化输出的文档/KC/例题/正式题摘要。

### 主要落点

- [ ] `frontend/src/pages/admin/router.js`
- [ ] `frontend/src/pages/admin/views/index.js`
- [ ] `frontend/src/pages/admin/views/general/LanguagePackInit.vue`
- [ ] `frontend/src/pages/admin/api.js`

### 阶段验收标准

- [ ] 管理员不需要调用 Postman，即可从页面完成首个语言包初始化任务。
- [ ] 页面上可明确看到卡在哪个阶段，不能只有“初始化失败”四个字。
- [ ] 上传成功但任务失败时，页面能看到失败原因与对应文档。
- [ ] 初始化成功后，页面能看到已发布语言包和已发布题目数量。

## Phase 8：前后端接线，让语言包真正可用

### 阶段目标

- [ ] 让初始化出来的语言包不是“数据库里有几张表”，而是对现有 OJ 前后端真正可见、可用。

### 需要完成

- [ ] 新增语言包查询 API，供 OJ 端获取全局可用语言包。
- [ ] 新增语言包详情 API，供前端查看该语言包的章节、KC、文档、题目数量。
- [ ] 新增题库过滤能力，使正式题可按 `language_pack_id` 或 `primary_language` 查询。
- [ ] 为后续独立 QA 窗口暴露语言包文档与页预览接口。
- [ ] 若复用现有 KC/画像链路，需明确语言包 KC 与现有 AI KC 的同步和读取方式。

### 主要落点

- [ ] `backend/src/main/java/com/alethicode/controller/LanguagePackQueryController.java`
- [ ] `backend/src/main/java/com/alethicode/service/languagepack/LanguagePackQueryService.java`
- [ ] `frontend/src/api/modules/problem.js`
- [ ] `frontend/src/pages/oj/router/routes.js`
- [ ] `frontend/src/pages/oj/components/NavBar.vue`

### 阶段验收标准

- [ ] 语言包初始化完成后，OJ 端能查到该语言包。
- [ ] 题库可按该语言包筛选出初始化生成的正式题。
- [ ] 语言包详情页或入口至少能展示该语言包的基础结构，不是只在 admin 端可见。

## Phase 9：空库部署、自检、回归与终验

### 阶段目标

- [ ] 在真正的“新机器空库”场景下验证一键初始化是否闭环。

### 需要完成

- [ ] 编写空库冷启动验收脚本。
- [ ] 在全新数据库环境中执行：
  - [ ] 后端启动
  - [ ] Flyway 迁移
  - [ ] 管理员登录
  - [ ] 上传课件
  - [ ] 一键初始化
  - [ ] 查询语言包
  - [ ] 查询正式题
- [ ] 增加后端单元测试、集成测试、契约测试，覆盖初始化主链与失败链。
- [ ] 为文档规范化、页解析、KC 抽取、题目发布编写最小回归样本。

### 主要落点

- [ ] `backend/src/test/java/com/alethicode/integration/LanguagePackInitIntegrationTest.java`
- [ ] `backend/src/test/java/com/alethicode/controller/AdminLanguagePackControllerContractTest.java`
- [ ] `scripts/verify_language_pack_init.sh`
- [ ] `deploy/README.md`

### 阶段验收标准

- [ ] 在空数据库环境中，不执行手工 SQL，也能通过页面完成首个语言包初始化。
- [ ] 初始化成功后，数据库中能查到语言包、文档、页、KC、例题、正式题、任务日志的完整链路。
- [ ] 初始化失败时，数据库状态保持可诊断，不出现半发布正式题。
- [ ] 回归测试覆盖成功链、失败链、权限链和幂等链。

---

## 4. 总体验收口径

- [ ] 新机器、空数据库、无预置语言包时，管理员一次上传课件即可初始化出一个全局语言包。
- [ ] 初始化结果包含：语言包元数据、文档、页、章节、KC、例题、正式编程题。
- [ ] 正式编程题的语言与语言包主语言一致，不再默认写死 Python。
- [ ] 题目进入正式题库前已经过机器验证。
- [ ] OJ 前端和 admin 前端都能看到并消费该语言包。
- [ ] 后续独立课件问答窗口可以直接复用这套语言包底座。

## 5. 与 `todo_ai_qa.md` 的依赖关系

- [ ] `todo_ai_qa.md` 的 Phase 2 以后必须依赖本 Todo 的 Phase 3 完成，因为问答必须建立在稳定的页级事实源上。
- [ ] `todo_ai_qa.md` 的引用预览能力必须依赖本 Todo 的 canonical 资产与页预览资产。
- [ ] `todo_ai_qa.md` 读取的语言包列表、文档列表、页级内容，必须来自本 Todo 建成的语言包查询域。

## 6. 风险结论

- [ ] 真正的难点不在“再加一个上传按钮”，而在“把课件变成稳定、页级、可验证、可追溯的语言包底座”。
- [ ] 只要语言包底座建对，扩到 Java、C++ 是自然扩展；若底座仍沿课堂链路补丁式推进，后续每种语言都会重新返工。
