# 独立课件问答窗口 Todo

> 文档状态：待执行
> 更新日期：2026-03-31
> 当前项目目录：`/home/cypress/Alethicode`
> 依赖前提：先完成 `todo_init.md` 中与语言包底座、页级索引、文档预览相关的阶段
> 产品边界变更：本 Todo 明确允许新增一个学生侧独立窗口，不再受旧设计文档“题目页单入口”限制
> 唯一目标：新增一个独立于题目页 AI 工作流的课件问答窗口，基于语言包知识底座和页级 RAG 只回答课件中可检索到的内容，并把回答严格定位到具体文档和具体页码。

---

## 0. 总原则

### 0.1 硬边界

- [ ] 这是“课件问答助手”，不是开放式万能聊天机器人。
- [ ] 不允许直接复用当前题目页 `UnifiedAgentPanel` 作为实现主体；必须提供独立窗口。
- [ ] 不允许回答脱离当前语言包课件范围的事实；没有证据就明确拒答。
- [ ] 不允许出现“看起来对但没有来源页”的回答；每次成功回答都必须附带引用页。
- [ ] 不允许只给章节级引用，必须至少精确到 `document + page_no`。
- [ ] 不允许在没有命中页证据的情况下仅凭模型常识补齐答案。
- [ ] 不允许跨语言包串答；Java 语言包的问答不能命中到 Python 语言包内容。
- [ ] 不允许把现有 problem workflow 的 phase/event/card 协议直接扩展为课件问答协议；这是一个独立会话域。
- [ ] 不允许点击引用后仍然看不到对应原页内容；引用必须可预览或可跳转。

### 0.2 当前项目已知事实

- [ ] 当前学生侧 AI 主入口位于题目页面板，服务的是做题学习闭环，不适合直接承载课件问答。
- [ ] 当前设计文档曾明确禁止第二个学生侧面板；本 Todo 是一次明确的产品边界变更，必须同步更新内部设计文档。
- [ ] 当前课件出题链路已经具备“按页范围处理课件”的局部能力，但并未形成严格的页级检索和引用协议。
- [ ] 当前 PPT/Word 浏览器内直接预览能力不足，因此问答引用预览必须建立在语言包初始化阶段生成的 canonical 预览资产之上。

### 0.3 首期范围

- [ ] 首期问答对象固定为“已发布的全局语言包”。
- [ ] 首期只支持文本型、可解析课件。
- [ ] 首期回答语言默认与用户界面语言一致，但内容证据来自目标语言包课件。
- [ ] 首期支持多轮追问，但会话上下文只作用于当前语言包，不跨语言包共享。
- [ ] 首期不做语音，不做图片提问，不做自由上传临时文件问答。

---

## 1. 终态定义

### 1.1 学生侧终态

- [ ] OJ 顶部导航或全局浮动入口出现“课件问答助手”独立入口。
- [ ] 点击后打开独立窗口，而不是题目页内侧栏。
- [ ] 学生可选择一个已发布语言包开始问答。
- [ ] 学生提出问题后，系统返回：
  - [ ] 结论性回答
  - [ ] 引用文档名
  - [ ] 引用页码
  - [ ] 引用摘录
  - [ ] 如证据不足，明确拒答理由
- [ ] 点击引用后，可预览原页或跳转到统一文档预览页。

### 1.2 后端终态

- [ ] 后端存在独立的课件问答会话域。
- [ ] 后端存在独立的页级检索服务，读取语言包页级事实源。
- [ ] 后端存在独立的问答生成协议，输出固定 JSON 结构。
- [ ] 后端存在独立的引用日志、检索日志、反馈日志。
- [ ] 后端能统计问答命中率、拒答率、页码精度、用户反馈。

### 1.3 交互终态

- [ ] 问答不是“返回一大段话”，而是“回答 + 引用 + 预览”三位一体。
- [ ] 学生可以明确追问“这是哪一页提到的”“还有哪些页也讲了这个概念”。
- [ ] 若问题超出课件范围，系统明确回答“当前课件中未找到足够依据”。

---

## 2. 会话协议与回答协议

### 2.1 会话对象

- [ ] `language_pack_chat_session`
  - 关键字段：`id`、`user_id`、`language_pack_id`、`status`、`created_at`、`updated_at`
- [ ] `language_pack_chat_message`
  - 关键字段：`id`、`session_id`、`role`、`content`、`answer_json`、`created_at`
- [ ] `language_pack_chat_retrieval_log`
  - 关键字段：`id`、`session_id`、`query_text`、`page_hit_json`、`created_at`
- [ ] `language_pack_chat_feedback`
  - 关键字段：`id`、`session_id`、`message_id`、`feedback_label`、`comment`、`created_at`

### 2.2 标准回答结构

- [ ] 每次成功回答必须返回：
  - [ ] `answer_markdown`
  - [ ] `citations`
  - [ ] `grounded = true`
  - [ ] `insufficient_evidence = false`
- [ ] 每次拒答必须返回：
  - [ ] `answer_markdown`
  - [ ] `citations = []`
  - [ ] `grounded = false`
  - [ ] `insufficient_evidence = true`
  - [ ] `refusal_reason`
- [ ] `citations` 内每个元素必须包含：
  - [ ] `document_id`
  - [ ] `document_title`
  - [ ] `page_no`
  - [ ] `excerpt`
  - [ ] `confidence`

### 2.3 严格约束

- [ ] 成功回答但 `citations` 为空，视为非法响应。
- [ ] 命中了别的语言包页，视为严重错误。
- [ ] 页码不存在或不可预览，视为引用协议错误。
- [ ] 模型返回无法解析的 JSON，直接视为失败并落日志。

---

## 3. 实施阶段

## Phase 1：产品边界变更与独立入口骨架

### 阶段目标

- [ ] 在产品和前端结构上，把“课件问答”从题目页 AI 工作流中明确切出去，成为独立窗口能力。

### 需要完成

- [ ] 更新内部设计说明，明确允许第二个学生侧 AI 入口。
- [ ] 确认独立入口挂在 OJ 顶部导航或全局浮动入口，而不是题目页。
- [ ] 新增 OJ 路由或全局挂载点，承载独立窗口。
- [ ] 确定窗口打开后的默认行为：若无已发布语言包则显示不可用说明；若有多个语言包则先选择语言包。

### 主要落点

- [ ] `docs/specs/project-design-spec-zh.md`
- [ ] `frontend/src/pages/oj/components/NavBar.vue`
- [ ] `frontend/src/pages/oj/App.vue`
- [ ] `frontend/src/pages/oj/router/routes.js`

### 阶段验收标准

- [ ] 学生登录后可见独立入口。
- [ ] 独立入口不依赖题目页。
- [ ] 没有已发布语言包时，入口不会误导用户进入空白聊天。
- [ ] 旧题目页 AI 面板行为不受破坏。

## Phase 2：课件问答会话域与 API 契约

### 阶段目标

- [ ] 建立独立于 problem workflow 的问答会话域和最小 API 契约。

### 需要完成

- [ ] 新增创建会话接口。
- [ ] 新增发送消息接口。
- [ ] 新增查询历史接口。
- [ ] 新增提交反馈接口。
- [ ] 新增获取可用语言包列表接口。
- [ ] 新增获取引用页详情接口。
- [ ] 明确所有接口都必须显式携带 `language_pack_id` 或绑定到 session。

### 主要落点

- [ ] `backend/src/main/java/com/alethicode/controller/LanguagePackQaController.java`
- [ ] `backend/src/main/java/com/alethicode/service/languagepackqa/LanguagePackQaService.java`
- [ ] `backend/src/main/java/com/alethicode/service/languagepackqa/impl/LanguagePackQaServiceImpl.java`
- [ ] `backend/src/main/resources/db/migration/Vxx__bootstrap_language_pack_chat.sql`
- [ ] `frontend/src/api/modules/ai.js`

### 阶段验收标准

- [ ] 不依赖现有 `workflow/session` 接口即可完成一次独立问答会话。
- [ ] 非登录用户无法创建会话。
- [ ] `language_pack_id` 缺失或非法时直接报错。
- [ ] 历史消息查询可按会话稳定返回。

## Phase 3：页级检索与 grounded answer 引擎

### 阶段目标

- [ ] 实现真正以页为单位的课件 RAG，而不是当前的题目/KC/章节近似召回。

### 需要完成

- [ ] 基于 `todo_init.md` 生成的 `language_pack_page` 建立检索服务。
- [ ] 实现语言包内检索的硬过滤，先按 `language_pack_id` 再做召回。
- [ ] 实现混合检索：
  - [ ] 关键词检索
  - [ ] 向量检索
  - [ ] rerank
- [ ] 召回结果必须至少保留 `document_id + page_no + excerpt + score`。
- [ ] 设计严格提示词，要求模型只能根据召回页回答。
- [ ] 设计拒答分支：命中分数不足或证据冲突时直接拒答。

### 主要落点

- [ ] `backend/src/main/java/com/alethicode/service/languagepackqa/PageRetrievalService.java`
- [ ] `backend/src/main/java/com/alethicode/service/languagepackqa/impl/PageRetrievalServiceImpl.java`
- [ ] `backend/src/main/java/com/alethicode/service/languagepackqa/AnswerSynthesisService.java`
- [ ] `backend/src/main/java/com/alethicode/service/languagepackqa/impl/AnswerSynthesisServiceImpl.java`
- [ ] `backend/src/main/java/com/alethicode/service/LlmClient.java`

### 阶段验收标准

- [ ] 问题命中结果只来自当前语言包。
- [ ] 每次成功回答都能回溯到明确的页命中记录。
- [ ] 没有证据时系统稳定拒答，不会胡乱回答。
- [ ] 当前旧的 `CoursewareRetrievalService` 不会被误当成最终页级问答检索实现。

## Phase 4：引用协议、页预览与证据查看

### 阶段目标

- [ ] 把“引用页码”做成可点击、可预览、可验证的真实证据体验。

### 需要完成

- [ ] 新增引用页详情接口，返回文档标题、页码、摘录、预览资源路径。
- [ ] 新增统一文档页预览能力，优先复用初始化阶段生成的 canonical 预览资产。
- [ ] 点击引用后支持：
  - [ ] 在窗口内侧边栏预览
  - [ ] 或打开单独页面预览
- [ ] 支持“查看同页上下文”而不是只看摘录。

### 主要落点

- [ ] `backend/src/main/java/com/alethicode/controller/LanguagePackDocumentController.java`
- [ ] `backend/src/main/java/com/alethicode/service/languagepack/LanguagePackDocumentQueryService.java`
- [ ] `frontend/src/pages/oj/views/languagepack/LanguagePackCitationPreview.vue`
- [ ] `frontend/src/pages/oj/views/languagepack/LanguagePackDocumentViewer.vue`

### 阶段验收标准

- [ ] 任意引用都可点击打开真实页预览。
- [ ] 打开的页码与回答中的页码一致。
- [ ] 预览失败会明确提示，不会静默无响应。
- [ ] 没有页预览资产的语言包不允许进入“已发布可问答”状态。

## Phase 5：独立窗口前端实现

### 阶段目标

- [ ] 在 OJ 端实现真正独立的课件问答窗口，而不是把旧面板换皮。

### 需要完成

- [ ] 新增独立窗口组件。
- [ ] 支持语言包选择器。
- [ ] 支持会话历史列表。
- [ ] 支持提问输入框、发送态、错误态、拒答态、引用卡片态。
- [ ] 支持回答区与引用预览区联动。
- [ ] 支持对单条回答做“有帮助 / 没帮助 / 引用不准”反馈。

### 主要落点

- [ ] `frontend/src/pages/oj/views/languagepack/LanguagePackQaWindow.vue`
- [ ] `frontend/src/pages/oj/views/languagepack/LanguagePackQaLauncher.vue`
- [ ] `frontend/src/pages/oj/views/languagepack/LanguagePackQaMessageList.vue`
- [ ] `frontend/src/pages/oj/views/languagepack/LanguagePackQaCitationList.vue`
- [ ] `frontend/src/pages/oj/components/NavBar.vue`

### 阶段验收标准

- [ ] 独立窗口可从任意 OJ 页面打开。
- [ ] 语言包切换后不会串掉历史会话。
- [ ] 成功回答、拒答、报错三种状态都可清晰分辨。
- [ ] 点击引用能联动打开预览，而不是只显示一串页码文本。

## Phase 6：多轮追问、上下文管理与边界控制

### 阶段目标

- [ ] 支持围绕同一语言包做多轮追问，但仍然保持严格证据边界。

### 需要完成

- [ ] 为会话记录最近轮次的问答上下文。
- [ ] 允许追问“上一条回答中的这个概念在别的页还有吗”“这页的代码是什么意思”。
- [ ] 明确上下文只能帮助改写检索 query，不能代替真实证据。
- [ ] 当学生追问跳出课件边界时，系统仍必须拒答。
- [ ] 新增清空会话与重新开始按钮。

### 主要落点

- [ ] `backend/src/main/java/com/alethicode/service/languagepackqa/ConversationContextService.java`
- [ ] `frontend/src/pages/oj/views/languagepack/LanguagePackQaWindow.vue`

### 阶段验收标准

- [ ] 多轮问答不会把上一轮的错误答案越传越远。
- [ ] 追问后生成的回答仍然带新的有效引用。
- [ ] 清空会话后，旧上下文不会继续污染新提问。

## Phase 7：观测、评测、反馈与治理

### 阶段目标

- [ ] 让独立窗口的质量可观测、可量化、可回归，而不是只能靠肉眼感受。

### 需要完成

- [ ] 记录问答检索日志、回答日志、反馈日志。
- [ ] 增加页级问答评测集，覆盖：
  - [ ] 明确可回答问题
  - [ ] 必须拒答问题
  - [ ] 页码相近但答案不同的问题
  - [ ] 跨语言包串答风险问题
- [ ] 增加核心指标：
  - [ ] `citation_coverage_ratio`
  - [ ] `page_hit_precision`
  - [ ] `insufficient_evidence_rate`
  - [ ] `user_negative_feedback_ratio`
- [ ] 对“回答无引用”“页码错误”“跨包命中”做强告警。

### 主要落点

- [ ] `backend/src/main/java/com/alethicode/service/languagepackqa/LanguagePackQaEvalService.java`
- [ ] `backend/src/main/java/com/alethicode/service/languagepackqa/LanguagePackQaTraceService.java`
- [ ] `backend/src/test/java/com/alethicode/integration/LanguagePackQaIntegrationTest.java`

### 阶段验收标准

- [ ] 每次回答都能查到检索日志。
- [ ] 评测集能稳定复现“可答 / 不可答”边界。
- [ ] 若出现无引用回答，系统可以直接定位到具体会话和具体消息。

## Phase 8：联调、发布与最终验收

### 阶段目标

- [ ] 完成和语言包初始化底座的联调，确认独立窗口在真实语言包上可用。

### 需要完成

- [ ] 选取至少一个已发布语言包做端到端联调。
- [ ] 验证从选择语言包、提问、检索、回答、点击引用、提交反馈的完整链路。
- [ ] 验证课件中不存在答案的问题会稳定拒答。
- [ ] 验证不同语言包之间不会串答。
- [ ] 验证未发布语言包不会出现在学生可用列表中。

### 主要落点

- [ ] `backend/src/test/java/com/alethicode/integration/LanguagePackQaIntegrationTest.java`
- [ ] `frontend/src/pages/oj/views/languagepack/`
- [ ] `scripts/verify_language_pack_qa.sh`

### 阶段验收标准

- [ ] 学生可从 OJ 顶部独立入口打开课件问答窗口。
- [ ] 成功回答时一定能看到文档和页码。
- [ ] 点击引用后可看到对应页内容。
- [ ] 问答未命中时系统稳定拒答而不是瞎答。
- [ ] 独立窗口与题目页 AI 工作流互不干扰。

---

## 4. 总体验收口径

- [ ] 已发布语言包在学生端可被选择并开始问答。
- [ ] 每条成功回答都带真实页级引用。
- [ ] 引用可点击、可预览、可验证。
- [ ] 课件中没有依据时系统会明确拒答。
- [ ] 问答窗口是独立产品入口，不再依附题目页 AI 面板。

## 5. 与 `todo_init.md` 的依赖关系

- [ ] 没有 `todo_init.md` 中的语言包、文档、页、canonical 预览资产，本 Todo 不应启动。
- [ ] 本 Todo 读取的语言包列表、文档页、页预览必须全部来自语言包底座，不允许再走课堂局部课件表。
- [ ] 若语言包底座未发布完成，学生端不得开放问答入口。

## 6. 风险结论

- [ ] 真正的难点不是“加个聊天框”，而是“保证每句话都能回到文档页”。
- [ ] 如果引用协议、页预览协议、拒答协议没有一次设计对，后面越迭代越难收敛。
- [ ] 只要严格坚持“语言包过滤 + 页级召回 + 有证据才回答”，独立窗口就能和现有题目页 AI 能力长期共存。
