# `@课件` AI 导学对话引用设计

> **目标**：在 AI 导学助手对话框（`UnifiedAgentPanel`）里支持 `@courseware:<lp_id>` token——选某份用户可见的已发布课件后，把该课件基于当前提问的 RAG top-k 检索结果作为 context 塞给 LLM。
>
> **场景**：学生看题时遇到一个不会的概念（如"递归"），希望 AI 结合**课件原文**讲解，而不是 AI 凭通用知识答。当前有「课件问答」入口可以用，但要切页面、要选课件、要单独 session；做题中插入式问答更快。
>
> **范围**：仅 AI 导学对话；不动「课件问答」页（它仍是 session 内绑定课件的形态）。
>
> **不做**：
> - 跨课件对比（一句话里 `@courseware:1 @courseware:2` 仅各自检索拼接，不做对比合成）
> - 检索结果含页码 anchor 渲染（沿用 RAG service 已有的 page meta，不新增展示）
> - 实时 RAG 索引（依赖现有 `language-pack-rag` 微服务的索引）
> - 鉴权细化（按"用户在 `LanguagePackQaService.listQaPacks(user)` 里能看到 = 可 @"，简单 ACL）

## 一、对外契约

### Token 格式
`@courseware:<lp_id>`，纯 ASCII，与 `@card:<id>` 风格一致。
- `<lp_id>`：发布课件的整数主键（`language_pack.id`），与 `LanguagePackQaService.listQaPacks` 返回的同名字段对齐
- 前端 `@` 菜单展示中文「课件」分类，但插入 token 时落 ASCII 形式

### 用户旅程
1. 学生在做题页打开 AI 导学对话框
2. 输入框敲 `@`，下拉菜单出现 4 个分类：
   - **当前会话卡片**（沿用现有 `@card / @last_*` 7 种 shorthand）
   - **课件**（新增）→ 列出 `LanguagePackQaService.listQaPacks(currentUser)` 返回的所有可见课件包
3. 选某份课件 → 输入框插入 `@courseware:42`
4. 学生继续输入问题文本（如"这个题里用到的递归是什么？"），点发送
5. 后端解析 message：识别 `@courseware:42` → 调 `PageRetrievalService.retrieve(42, "这个题里用到的递归是什么？", recentContext)` → 拿 top-k page chunks → 拼到 LLM prompt
6. LLM 答案带"参考课件第 X 页"形式的引用（如果 chunks 有 page meta）

## 二、改动清单

### Backend (Java)

| 文件 | 改动 |
|---|---|
| `service/aitutor/context/ReferenceResolver.java` | 新增 `COURSEWARE_REF = Pattern.compile("@courseware:(\\d+)")` 正则 + `extractCoursewareId(String raw): Long` 静态方法。不引入新枚举，因为课件不是 card 类型。 |
| `service/aitutor/context/CoursewareSummary.java`（新建） | record `CoursewareSummary(Long languagePackId, String packName, List<RetrievedChunk> chunks, Instant retrievedAt)`，与 `CardSummary` 平级。`RetrievedChunk` 含 `text` / `pageNumber` / `score`。 |
| `service/aitutor/context/CoursewareContextProvider.java`（新建接口） | `resolveCoursewareReferences(String sessionId, long userId, List<String> rawTokens, String currentQuery, String recentContext): List<CoursewareSummary>` |
| `service/aitutor/context/impl/CoursewareContextProviderImpl.java`（新建） | 1) 解析 token 拿 lp_id 列表；2) 用 `LanguagePackQaService.listQaPacks(username)` 取允许列表，过滤越权 lp_id（**failfast：越权直接 throw 403**）；3) 对每个允许的 lp_id 调 `PageRetrievalService.retrieve(lp_id, currentQuery, recentContext)` 拿 hits；4) 组装 `CoursewareSummary` 列表。 |
| `service/aitutor/impl/InternalAITutorToolServiceImpl.java`（或同等 chat 入口） | 在原有 `resolveReferences` 之外，调用 `CoursewareContextProvider.resolveCoursewareReferences`，把 chunks 拼进 LLM 的 system 或 user message（建议放 system 里以"以下是用户引用的课件原文"标注）。 |
| `service/aitutor/contract/ConversationCardContext`（如有） | 扩展承载 `coursewareSummaries` 字段（或新建 `ConversationContextBundle`） |

### Backend 测试
| 测试 | 用例 |
|---|---|
| `ReferenceResolverTest` 增 | `@courseware:42` extractCoursewareId → 42；`@courseware:` / `@courseware:abc` → null |
| `CoursewareContextProviderImplTest`（新建） | 1) token 列表里 lp_id 都允许 → 调 retrieve 后返回正确 CoursewareSummary；2) 部分越权 → 抛 `LegacyBusinessException("permission-denied")`；3) `PageRetrievalService.retrieve` 抛 `RagServiceException` → CoursewareSummary 该项为空但其他项不受影响（partial degradation 由调用方决定怎么处理）；4) 空 token list → 返回 List.of()。 |
| chat 入口集成测试 | 一句话同时含 `@last_error` + `@courseware:42` → 两种 reference 都拼进 prompt（不互相覆盖） |

### Frontend (Vue)

| 文件 | 改动 |
|---|---|
| `pages/oj/views/problem/UnifiedAgentPanel.vue` | `referenceCards` computed 之外新增 `referenceCoursewares`：调 `api.listLanguagePackQaPacks()`（已存在）拿允许的课件列表，映射成 `{ key: 'courseware:<id>', label: pack.name, reference_token: '@courseware:<id>' }`。@ 菜单 UI 加分组分隔符 + 课件分类。 |
| `pages/oj/views/problem/useReferenceParse.js` | 注释里 token 列表加 `@courseware:<id>`；`parseReferences` 已支持 `@xxx:yyy` 通用形式无需改。 |
| `manualContent.js` | `CONTEXT_TOKENS` 加一行 `@courseware:<id>` / 中文名「课件引用」/ 适用场景；`SectionCoursewareQa` 把"规划中"灰底改成"已上线"+ 给一个示例 PromptCard。 |

### 测试 / 文档
- `manual-content-data.spec.js` CONTEXT_TOKENS 期望 9 项（原 8 + 课件）
- `manual-sections-source.spec.js` SectionCoursewareQa 反向断言"规划中"→ 删
- `CHANGELOG.md` 一条「修改」条目

## 三、与现有架构的对齐

- **资源边界**：每条 `@courseware` 的 RAG 检索独立，不共享缓存（首版）。如压测发现 LLM 并发瓶颈在 RAG，再加 query-level cache。
- **Failfast 原则**：越权 lp_id → 抛 403 不静默；RAG 异常 → 该 token 的 CoursewareSummary 字段返回空 chunks 但不阻断对话主链路（user 仍能拿到 LLM 回答，只是 system prompt 少这块 context）。
- **不做防御性兜底**：lp_id 不存在 → throw；listQaPacks 返回空 → user 选不出课件，前端禁止插入 token。

## 四、估算

| 阶段 | 工时 |
|---|---|
| backend ReferenceResolver + CoursewareSummary + Provider | 半天（含单测） |
| InternalAITutorToolService 集成 + 集成测试 | 半天 |
| frontend `@` 菜单扩展 | 半天 |
| manualContent + section 文档 + 单测 | 1h |
| 部署 + e2e | 半天 |
| **总计** | **2 天** |

## 五、上线后压测注意

- LLM 并发 80 是高峰目标。`@courseware` 引入额外 RAG service 调用，每条 reference 增加 ~200-500ms 检索延迟。
- 如果 80 并发下 RAG service 成为瓶颈，第一版可加：(a) 同 lp_id + 同 query 的检索 in-memory cache 60s；(b) 限制单条 message 最多 3 个 `@courseware` token。
- 压测脚本要包含 `@courseware` 用例，不只是纯文本对话。

## 六、回滚

- 移除 `@courseware:` 正则匹配 → token 变成纯文本不影响（backend `ReferenceResolver` 找不到匹配就 silent skip）
- frontend `@` 菜单的"课件"分类用 feature flag 控制，可在前端运行时关闭

