# Persistent Memory（学情画像层）增量设计

> **文档编号**：ALETH-PLAN-2026-0425-P1
> **关联调研**：[`docs/reports/2026-04-25-deeptutor-inspiration-survey.md`](../reports/2026-04-25-deeptutor-inspiration-survey.md)
> **优先级**：P1（地基）
> **作者**：AI Coding Assistant
> **创建日期**：2026-04-25

> **一句话目标**：让 5 教学角色 **真正"认识"每个学生**——所有节点的 LLM 调用都能稳定感知 LearningStyle、近期错误模式、长期学习摘要；并让学生 / 教师能在前端看见自己被 AI 怎么"画像"。

---

## 目录

- [一、设计动机](#一设计动机)
- [二、现状盘点](#二现状盘点)
- [三、差距分析](#三差距分析)
- [四、设计目标与非目标](#四设计目标与非目标)
- [五、整体架构](#五整体架构)
- [六、详细设计](#六详细设计)
- [七、全链路时序](#七全链路时序)
- [八、契约示例](#八契约示例)
- [九、工作量评估](#九工作量评估)
- [十、验收标准](#十验收标准)
- [十一、风险与缓解](#十一风险与缓解)
- [十二、第一性原理自检](#十二第一性原理自检)
- [附录 A：测试矩阵](#附录-a测试矩阵)
- [附录 B：DDL 草案](#附录-bddl-草案)

---

## 一、设计动机

### 1.1 教学场景痛点

非计算机专业 Python 初学者 + 5 教学角色当前的关键痛点：

1. **学生进入题目页时，角色"陌生人感"严重**。同一学生 7 天前同类错误踩 5 次，今天 Nene 仍然套用通用导读模板。
2. **角色对话不引用过去**。Yoshino 不会说"这是你第三次循环边界写错了"。
3. **教学风格切换不灵敏**。`LearningStyle` 已经有推断，但节点 `SYSTEM_PROMPT` 写死，模型不会稳定使用。
4. **学生不知道 AI 怎么看自己**。完全的黑盒，无法自我修正画像。
5. **教师看不到班级整体画像**。无法基于全班 KC / error 分布做集体讲评。

### 1.2 第一性原理

**让 LLM 个性化对话的唯一前提，是把"个性化数据"以 LLM 能读懂的格式、在合适的位置、稳定地注入到对话上下文里。**

Alethicode 已经有完整的"个性化数据"（`ai_learner_memory` + Profile + KT），缺的是 **"格式 / 位置 / 稳定性"** 这一层。这就是本设计的全部任务。

---

## 二、现状盘点

### 2.1 已具备能力（不重做，仅复用）

| 模块 | 现状 |
|---|---|
| `ai_learner_memory` 表 | 6 类 memory_type，含 jsonb payload + pgvector 嵌入 + 衰减 + 召回 boost |
| `LearnerMemoryService` | 633 行：createCandidate / evaluateCandidate / persistCandidate / saveTutoringConclusion / loadPreviousConclusions / refreshFromSources / inferLearningStyle |
| `LearnerProfileProjector` | 327 行：project()→LearnerState；含 mastery 融合校准、frustration 推断、teaching_style_prompt 已经放进 recommendedActionBias |
| `LearnerState` record | calibrated / masteryByKc / weakKcs / misconceptionDistribution / recentBehavior / frustrationLevel / confidenceProxy / recommendedActionBias / memoryRefs |
| `LearningStyle` | STEP_BY_STEP / EXPLORATORY / VISUAL / ANALYTICAL，每种含 `toPromptPrefix()` |
| `CrossCourseProfileService` | 跨课程 weak_kcs / dominant_frustration_level / preferred_action |
| `AITutorWelcomeService` | 已有欢迎 greeting + memory_tags |
| `tutor_graph` evidence | `learner_state` 已是 evidence 之一，但 READING / CHAT / CODING 不要 |

### 2.2 已有数据库表（关键）

| 表名 | 关键字段 |
|---|---|
| `ai_learner_memory` | user_id, memory_key, memory_type, memory_value, memory_payload(jsonb), memory_embedding(vector), confidence, source_type, source_problem_id, expires_at, last_recalled_at, recall_count |
| `ai_learner_profile_snapshot` | user_id, problem_id, session_id, mastery_by_kc(jsonb), weak_kcs(jsonb), misconception_distribution(jsonb), recent_behavior(jsonb), frustration_level, confidence_proxy, recommended_action_bias(jsonb), memory_refs(jsonb), created_at |
| `ai_learner_notebook` | user_id, problem_id, error_taxonomy, root_cause, fix_outcome, student_reflection, tags(jsonb), notebook_summary, notebook_embedding(vector) |
| `ai_learning_event` | user_id, problem_id, event_type, extra_data(jsonb), created_at |
| `ai_calibration_state` | user_id, calibrated, accumulated(jsonb) |
| `ai_problem_kc_mapping` | problem_id, kc_id |
| `learner_kc_mastery` | user_id, kc_id, mastery, updated_at |

---

## 三、差距分析

按"教学影响"由强到弱列出 6 项差距。

### G1. tutor_graph 节点 SYSTEM_PROMPT 没有结构化注入 LearningStyle

**现状**（`services/tutor-graph/app/nodes/diagnosis.py`）：

```python
SYSTEM_PROMPT = """你是面向非计算机专业 Python 初学者的 AI 导学助手。
学生提交了错误的代码。请分析错误原因。
要求：
- 不给完整可提交代码
- 给出根本原因和修复方向
...
"""

user_msg = (
    f"提交结果: {diag.get('result', '未知')}\n"
    f"代码:\n{diag.get('code', '(无)')}\n"
    f"语言: {diag.get('language', state.get('language', ''))}\n"
    f"错误信息: {diag.get('err_info', '(无)')}\n"
    f"学习者状态: {learner}"
)
```

**问题**：`{learner}` 是 dict 直接 str 化拼到 user_msg，LLM 视为噪声；`teaching_style_prompt` 虽然在 dict 里，但模型完全不会稳定遵循。

### G2. READING / CHAT / CODING 节点根本不要 learner_state

**现状**（`services/tutor-graph/app/nodes/evidence.py`）：

```python
EVENT_EVIDENCE_REQUIREMENTS: dict[str, list[str]] = {
    "READING": ["workflow_context", "courseware_hits"],   # ⚠️ 缺 learner_state
    "IDEATING": ["workflow_context", "learner_state"],
    "CODING": ["workflow_context"],                       # ⚠️ 缺 learner_state
    "CHAT": [],                                           # ⚠️ 全空
    ...
}
```

**问题**：Nene 导读、Chat 闲聊、Coding 阶段提示完全不知道学生底子；`AITutorWelcomeService` 只能在欢迎卡片做个性化，进入对话后 LLM 又"失忆"。

### G3. 没有"自然语言长期摘要"

**现状**：`ai_learner_memory` 全是离散事件 / 错题 / 反思的结构化记录，**没有一段"近 30 天学习概览"** 的滚动摘要。

**问题**：每次让 LLM 看 5 条 memory_refs，LLM 仍要花 token 自己在头脑里"拼图"；对长尾学生（数据多）反而更糟。

### G4. 没有学生侧画像 dashboard

**现状**：`LearnerState` 只供 Agent 内部使用。

**问题**：

- 学生不知道 AI 怎么看自己，无法纠偏；
- 学生看不见自己的进步，缺反馈感；
- 教学透明度不足。

### G5. 没有教师侧班级画像

**现状**：`ai_learner_profile_snapshot` 是按学生维度，没有班级聚合 API。

**问题**：教师无法基于全班 KC 分布 / 高频错误调整教学。

### G6. Memory 召回是最近 + 衰减，没按"当前题语义"召回

**现状**（`LearnerMemoryService.listActiveMemoryRefs`）：

```sql
select ... from ai_learner_memory
where user_id = ? and enabled = true and (expires_at is null or expires_at > now())
order by updated_at desc limit 20
```

随后在 Java 端按 confidence 排序取 top 5。

**问题**：当前题 KC=`for_loop_boundary`，但 memory 里"字符串切片"问题会被照样塞给 LLM。已经有 `memory_embedding` 字段，但召回没用上。

---

## 四、设计目标与非目标

### 4.1 设计目标（必达）

| # | 目标 | 验收方式 |
|---|---|---|
| O1 | 所有 tutor_graph 教学节点 SYSTEM_PROMPT 显式注入 LearningStyle.toPromptPrefix() | 节点单测：注入后 prompt 含目标字符串 |
| O2 | READING / CHAT / CODING 节点的 evidence 增加 learner_state | evidence 单测 + 集成测 |
| O3 | 每名活跃学生有一份滚动更新的"自然语言长期摘要"（≤500 字） | 单测：摘要生成幂等；用户可见 |
| O4 | 学生侧画像 Dashboard：可见 LearningStyle / 近期错误 / 摘要 / 个人化开关 | 前端展示 + 后端 API 200 |
| O5 | Memory 召回按"当前题 KC + 当前错误向量"做 top-K 检索 | 召回单测：与最近策略输出不同且更相关 |
| O6 | （可选）教师侧班级画像聚合 API | API 200，含班级 KC 分布 + 高频错误 |

### 4.2 非目标（明确不做）

| 非目标 | 原因 |
|---|---|
| 不引入新的 Memory 表 | 现有 `ai_learner_memory` 已够用，加列即可 |
| 不替换 tutor_graph LangGraph 编排 | ADR-0001 框架内增量扩展 |
| 不引入新的向量库 | 继续用 pgvector |
| 不暴露原始 memory_payload 给学生 | 仅暴露 LLM 摘要后的语义层 |
| 不引入 nanobot / 多 Bot 架构 | 与 5 角色协作冲突 |
| 不修改 nfk 模型 / Mastery 计算 | 与本设计无关 |
| 不接入新的 LLM 供应商 | `AiModelGateway` 已抽象 |
| 不写防御性逻辑 | failfast |

---

## 五、整体架构

### 5.1 数据流向

```
                                      ┌──────────────────────┐
                                      │ 前端 UnifiedAgentPanel│
                                      │ + 新增 ProfileDrawer  │
                                      └──────────▲───────────┘
                                                 │ HTTP
                            ┌────────────────────┴─────────────────────┐
                            │   AITutorController (Java)               │
                            │     /ai-tutor/profile/me                 │
                            │     /ai-tutor/profile/me/preferences     │
                            │     /ai-tutor/profile/me/refresh         │
                            │     /ai-tutor/profile/class/{id}         │
                            └────────────────────┬─────────────────────┘
                                                 │
                ┌────────────────────────────────┼─────────────────────────────────┐
                │                                ▼                                 │
                │  ┌──────────────────────────────────────────────────────────┐    │
                │  │ LearnerProfileProjector (现有，扩 narrative_summary 字段) │    │
                │  └─────────────┬────────────────────────────────────────────┘    │
                │                │                                                 │
                │                ▼                                                 │
                │  ┌─────────────────────────┐  ┌──────────────────────────────┐   │
                │  │ LearnerNarrativeSummary │  │ LearnerMemorySemanticRetrieval│  │
                │  │ Service (新增)          │  │ Service (新增)                │   │
                │  │  - 滚动摘要 generator   │  │  - kc/embedding top-K 召回    │   │
                │  └────┬────────────────────┘  └──────┬───────────────────────┘   │
                │       │                              │                           │
                │       ▼                              ▼                           │
                │  ┌──────────────────────────────────────────────────────────┐    │
                │  │ LearnerMemoryService (现有，加 narrative getter +         │    │
                │  │ retrieveByContext)                                        │    │
                │  └──────────────────────┬────────────────────────────────────┘    │
                │                         │                                         │
                │                         ▼                                         │
                │              ┌────────────────────────────┐                       │
                │              │  ai_learner_memory         │                       │
                │              │  ai_learner_profile_       │                       │
                │              │   snapshot                 │                       │
                │              │  ai_learner_narrative_     │ ← 新增表               │
                │              │   summary                  │                       │
                │              └────────────────────────────┘                       │
                │                                                                  │
                │  ┌──────────────────────────────────────────────────────────┐    │
                │  │ InternalAITutorToolService.getLearnerState() (现有)       │    │
                │  │   返回的 LearnerState 多带 narrative_summary 字段          │    │
                │  └──────────────────────┬───────────────────────────────────┘    │
                └────────────────────────┼─────────────────────────────────────────┘
                                          │ /internal/ai-tutor/* (HTTP)
                                          ▼
                          ┌──────────────────────────────────────┐
                          │  tutor_graph (Python LangGraph)      │
                          │   evidence.py: learner_state 扩字段   │
                          │   nodes/*: SYSTEM_PROMPT 模板化注入    │
                          └──────────────────────────────────────┘
```

### 5.2 新增组件清单

| 组件 | 类型 | 责任 |
|---|---|---|
| `ai_learner_narrative_summary` | 数据库表 | 每用户一条滚动自然语言摘要 |
| `LearnerNarrativeSummaryService` | Java service | 生成 / 增量更新 / 读取摘要 |
| `LearnerMemorySemanticRetrievalService` | Java service | 按 KC + 当前错误向量做 top-K 召回 |
| `ProfileViewService` | Java service | 学生侧画像 dashboard 数据组装（脱敏后） |
| `ClassProfileAggregationService` | Java service（可选） | 教师侧班级画像聚合 |
| `AITutorController.profileMe()` | REST | `GET /ai-tutor/profile/me` |
| `AITutorController.updatePreferences()` | REST | `PATCH /ai-tutor/profile/me/preferences` |
| `AITutorController.refreshSummary()` | REST | `POST /ai-tutor/profile/me/refresh` |
| `AITutorController.classProfile()` | REST（可选） | `GET /ai-tutor/profile/class/{classId}` |
| `ProfileDrawer.vue` | 前端组件 | 学生画像抽屉 |
| `useProfileApi.js` | 前端 composable | 调画像 API |
| `services/tutor-graph/app/nodes/prompts.py` | Python 模块 | 统一 prompt 模板拼装（learner block） |

---

## 六、详细设计

### 6.1 数据模型变更

#### 6.1.1 新增表 `ai_learner_narrative_summary`

```sql
CREATE TABLE ai_learner_narrative_summary (
    user_id            BIGINT       NOT NULL,
    summary_version    INTEGER      NOT NULL DEFAULT 1,
    summary_text       TEXT         NOT NULL,
    summary_payload    JSONB        NOT NULL DEFAULT '{}',
    learning_style_key VARCHAR(32)  NOT NULL,
    last_event_id      BIGINT,
    last_session_id    VARCHAR(64),
    is_user_overridden BOOLEAN      NOT NULL DEFAULT FALSE,
    user_disabled      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP    NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id)
);

CREATE INDEX idx_aln_user_updated
    ON ai_learner_narrative_summary(user_id, updated_at DESC);
```

字段说明：

| 字段 | 用途 |
|---|---|
| `summary_version` | 单调递增；每次重生 +1，前端显示给学生看"第 N 版" |
| `summary_text` | LLM 生成的中文摘要，≤500 字 |
| `summary_payload` | 结构化辅助字段：`{top_kcs: [...], top_errors: [...], stats: {...}}` |
| `learning_style_key` | 生成时刻的 LearningStyle，便于审计何时风格漂移 |
| `last_event_id` | 末次纳入摘要的 `ai_learning_event.id`，下次增量计算从此处开始 |
| `is_user_overridden` | 学生手动改写后置 true，自动 refresh 不再覆盖 |
| `user_disabled` | 学生关闭个性化时置 true，所有节点 prompt 不再注入 |

#### 6.1.2 现有表加列

`ai_learner_profile_snapshot`：

```sql
ALTER TABLE ai_learner_profile_snapshot
    ADD COLUMN narrative_summary_version INTEGER,
    ADD COLUMN narrative_summary_text    TEXT;
```

冗余写入 snapshot，便于审计回溯历史画像。

#### 6.1.3 Flyway 迁移

新增 `V0039__add_learner_narrative_summary.sql`，序号顺延（当前 38 个）。

### 6.2 服务层变更

#### 6.2.1 `LearnerNarrativeSummaryService`（新增）

```java
@Service
public class LearnerNarrativeSummaryService {

    private static final int MAX_SUMMARY_CHARS = 500;
    private static final int MIN_NEW_EVENTS_TO_REFRESH = 3;
    private static final Duration MAX_STALENESS = Duration.ofHours(12);

    private final JdbcTemplate jdbc;
    private final AiModelGateway aiModelGateway;
    private final ObjectMapper objectMapper;

    public NarrativeSummary loadOrGenerate(Long userId) { ... }
    public NarrativeSummary refreshIfStale(Long userId, Long lastEventId) { ... }
    public void disablePersonalization(Long userId) { ... }
    public void enablePersonalization(Long userId) { ... }
    public void overrideSummary(Long userId, String userEditedText) { ... }

    private NarrativeSummary generateSummary(Long userId, ProfileMaterials materials) {
        Map<String, Object> result = aiModelGateway.callForJson(
            SUMMARY_SYSTEM_PROMPT, buildSummaryUserPrompt(materials));
        return NarrativeSummary.fromLlmResult(userId, result);
    }
}
```

**触发时机**（fail-fast，不写定时器）：

| 时机 | 触发位置 | 条件 |
|---|---|---|
| 每次 AC_REVIEW 节点完成 | `AITutorWorkflowDomainServiceImpl.persistAcReview()` | 自上次摘要起新增事件 ≥ 3 |
| 学生进入题目页 | `AITutorWelcomeService.getWelcome()` | 摘要为空或超过 12h |
| 学生手动 "刷新画像" 按钮 | `POST /ai-tutor/profile/me/refresh` | 用户主动 |

**降级原则**：摘要生成失败时返回上一版本；首次失败则 narrative=空字符串 + 日志告警；不生成兜底文本（避免误导）。

#### 6.2.2 `LearnerMemorySemanticRetrievalService`（新增）

```java
@Service
public class LearnerMemorySemanticRetrievalService {

    private static final double MAX_DISTANCE = 0.4;
    private static final double MIN_CONFIDENCE = 0.5;

    public List<Map<String, Object>> retrieveByContext(
            Long userId,
            List<String> currentKcs,
            String currentErrorContext,
            int topK) {

        String embeddingLiteral = VectorCodec.toPgVector(
            aiModelGateway.callForEmbedding(currentErrorContext));

        return jdbc.query("""
            SELECT id, memory_key, memory_type, memory_value, memory_payload::text AS payload,
                   confidence, source_problem_id,
                   memory_embedding <=> cast(? as vector) AS distance
            FROM ai_learner_memory
            WHERE user_id = ?
              AND enabled = true
              AND confidence >= ?
              AND (memory_embedding <=> cast(? as vector)) < ?
              AND (expires_at IS NULL OR expires_at > now())
            ORDER BY (memory_embedding <=> cast(? as vector)) ASC,
                     confidence DESC
            LIMIT ?
            """, ...);
    }
}
```

**调用方**：`LearnerProfileProjector.project()` 把现有 `listActiveMemoryRefs` 替换为 `retrieveByContext`，需要 `currentKcs` + `currentErrorContext` 作为新入参。

**关键约束**：

- 距离阈值 0.4：避免无关历史记忆污染；
- 置信度阈值 0.5：避免低质量 memory 出现在 prompt 里；
- 仅 top-K=5：保证 prompt 长度可控。

#### 6.2.3 `LearnerProfileProjector` 扩展

保留旧 signature（兼容现有调用），新增带 ContextSignals 的重载：

```java
public LearnerState project(Long userId, Long problemId,
                            Map<String, Object> behaviorMetrics, String currentPhase) { ... }

public LearnerState project(Long userId, Long problemId,
                            Map<String, Object> behaviorMetrics, String currentPhase,
                            ContextSignals contextSignals) { ... }
```

`ContextSignals` 结构：

```java
public record ContextSignals(
    List<String> currentKcs,
    String currentErrorContext,
    String currentProblemStatement
) {}
```

`LearnerState` 增加 2 个字段：

```java
public record LearnerState(
    boolean calibrated,
    Map<String, Double> masteryByKc,
    List<String> weakKcs,
    Map<String, Double> misconceptionDistribution,
    Map<String, Object> recentBehavior,
    String frustrationLevel,
    String confidenceProxy,
    Map<String, Object> recommendedActionBias,
    List<Map<String, Object>> memoryRefs,
    String narrativeSummary,
    boolean personalizationEnabled
) { ... }
```

#### 6.2.4 `ProfileViewService`（新增，学生侧）

```java
@Service
public class ProfileViewService {

    public StudentProfileView getMyProfile(Long userId) {
        // 1. 加载 narrative_summary
        // 2. 加载 LearningStyle
        // 3. 加载 weak_kcs（取最新 snapshot）
        // 4. 加载 top 5 error_pattern memory（脱敏：不暴露原 payload）
        // 5. 加载 personalization_enabled / user_overridden 标记
        return new StudentProfileView(...);
    }

    public void updatePreferences(Long userId, UpdatePreferencesRequest req) {
        // 仅允许学生修改：personalizationEnabled / userEditedSummaryText
        // 学生主动改写后 isUserOverridden = true，AI 后续不再覆盖
    }
}
```

**脱敏规则**：

| 字段 | 学生可见 | 教师可见 | 内部使用 |
|---|---|---|---|
| `narrative_summary` | ✅ | 👁(脱敏) | ✅ |
| `learning_style.key` | ✅ | ✅ | ✅ |
| `learning_style.label` | ✅ | ✅ | ✅ |
| `weak_kcs.name` | ✅ | ✅ | ✅ |
| `weak_kcs.mastery` (数字) | ✅(>=0.4 显示) | ✅ | ✅ |
| `error_pattern.taxonomy_label` (中文) | ✅ | ✅ | ✅ |
| `error_pattern.detector_name` (内部名) | ❌ | ❌ | ✅ |
| `misconception_distribution` (raw) | ❌ | ❌ | ✅ |
| 其他学生数据对照 | ❌ | ❌(仅聚合) | ❌ |

#### 6.2.5 `InternalAITutorToolService.getLearnerState()` 契约扩展

```java
// 现有：getLearnerState(userId, problemId, sessionId, language)
// 扩展：增加可选 contextSignals
public LearnerStateView getLearnerState(Long userId, Long problemId, String sessionId,
                                        String language, ContextSignalsDto contextSignals);
```

旧调用方传 `contextSignals=null` 时退回到原有逻辑（用 `listActiveMemoryRefs`），新调用方传完整 ContextSignals 时用 `retrieveByContext`。

### 6.3 tutor_graph 节点契约变更

#### 6.3.1 evidence_pack 增加 learner_state

```python
# services/tutor-graph/app/nodes/evidence.py
EVENT_EVIDENCE_REQUIREMENTS: dict[str, list[str]] = {
    "READING": ["workflow_context", "courseware_hits", "learner_state"],   # 新增
    "IDEATING": ["workflow_context", "learner_state"],
    "CODING": ["workflow_context", "learner_state"],                       # 新增
    "ERROR_FEEDBACK": ["diagnosis_evidence", "learner_state", "similar_errors"],
    "AC_REVIEW": ["diagnosis_evidence", "learner_state", "courseware_hits"],
    "TRANSFER": ["workflow_context", "learner_state"],
    "CHAT": ["learner_state"],                                             # 新增
    "AGENT_FEEDBACK": [],
    "KNOWLEDGE_REVIEW": ["learner_state", "courseware_hits"],
    "PLAN_RECOMMEND": ["learner_state"],
    "PLAN_START": [],
    "PLAN_RESPONSE": [],
    "PLAN_STEERING": [],
}
```

`get_learner_state` 调用时多传 `context_signals`：

```python
evidence["learner_state"] = await java_client.get_learner_state(
    user_id, problem_id=problem_id, session_id=session_id, language=language,
    context_signals={
        "current_kcs": context.get("kc_names", []),
        "current_error_context": diag.get("err_info", "") if diag else "",
        "current_problem_statement": context.get("statement", ""),
    },
)
```

#### 6.3.2 节点 SYSTEM_PROMPT 模板化

新建 `services/tutor-graph/app/nodes/prompts.py`：

```python
"""Centralized prompt template assembler — learner block injection."""

LEARNER_BLOCK_TEMPLATE = """
[学习者画像]
{narrative_summary_block}
[教学风格偏好]
{teaching_style_block}
[召回的相关历史记忆]
{memory_refs_block}
[关键知识点掌握度]
{mastery_block}
"""

MAX_LEARNER_BLOCK_CHARS = 1500


def assemble_learner_block(learner_state: dict) -> str:
    """Assemble the learner profile block to be appended to SYSTEM_PROMPT.

    Returns "" if personalization disabled or learner_state empty (failfast).
    """
    if not learner_state or not learner_state.get("personalization_enabled", True):
        return ""
    narrative = learner_state.get("narrative_summary", "").strip()
    style_prompt = (learner_state.get("recommended_action_bias", {})
                    .get("teaching_style_prompt", ""))
    memory_refs = learner_state.get("memory_refs", [])
    weak_kcs = learner_state.get("weak_kcs", [])
    mastery = learner_state.get("mastery_by_kc", {})

    block = LEARNER_BLOCK_TEMPLATE.format(
        narrative_summary_block=narrative or "（暂无）",
        teaching_style_block=style_prompt or "（默认 step_by_step）",
        memory_refs_block=_format_memory_refs(memory_refs) or "（暂无）",
        mastery_block=_format_mastery(mastery, weak_kcs) or "（暂无）",
    )
    if len(block) > MAX_LEARNER_BLOCK_CHARS:
        block = block[:MAX_LEARNER_BLOCK_CHARS] + "...(truncated)"
    return block
```

每个节点的 SYSTEM_PROMPT 拼装：

```python
# services/tutor-graph/app/nodes/diagnosis.py
from app.nodes.prompts import assemble_learner_block

SYSTEM_PROMPT_BASE = """你是面向非计算机专业 Python 初学者的 AI 导学助手。
学生提交了错误的代码。请分析错误原因。
要求：
- 不给完整可提交代码
- 给出根本原因和修复方向
- 区分"程序现在在做什么"和"你当时为什么可能会这样写"
- 若有历史重复错误，给出针对性提醒（参考 [学习者画像] 中的"召回的相关历史记忆"）
- 必须遵循 [教学风格偏好] 给出的格式与节奏
- 输出 JSON 格式，字段: root_cause, what_program_is_doing, expected_behavior,
  fix_direction, related_kcs, error_pattern, is_recurring, encouragement,
  teaching_goal, checkpoint_prompt, mentor_role, reflection_prompt"""


async def error_feedback_node(state, *, llm_client):
    learner_block = assemble_learner_block(state.get("learner_state", {}))
    system_prompt = SYSTEM_PROMPT_BASE + "\n\n" + learner_block
    ...
```

**关键约束**：

- 学生关闭个性化（`personalization_enabled=False`）时，`learner_block` 为空字符串，prompt 退回到原始通用模板；
- 注入位置统一在 SYSTEM_PROMPT 末尾，便于审计与日志；
- 通过 `langfuse_metadata` 标记 `learner_block_injected: bool` 以便观测。

### 6.4 Prompt 注入规范

#### 6.4.1 注入格式

```
[学习者画像]
近 30 天小明做了 18 道题，AC 12 道。集中在 for 循环与字符串切片，
仍在 range 边界条件上反复出错（已发生 4 次）。最近一次错误是第 7 题 missing range upper bound。
[教学风格偏好]
【学生偏好】喜欢被逐步引导：请把解题步骤按 1/2/3 编号列出，每一步说清楚目的和验证方式...
[召回的相关历史记忆]
1. (置信 0.92, 距离 0.08) 题 5：for i in range(n) 漏掉边界，根因=对 range 半开区间不熟
2. (置信 0.85, 距离 0.12) 题 3：for j in range(1, n) 当 n=0 时跳过循环，根因=未考虑空输入
3. (置信 0.78, 距离 0.14) 错题本反思：range 在我脑里像数学区间 [a,b]，应记为 [a,b)
[关键知识点掌握度]
弱：for_loop_boundary(0.34)、range_semantics(0.41)
强：variable_assignment(0.92)、print_format(0.88)
```

#### 6.4.2 截断策略

| 项 | 上限 | 截断方式 |
|---|---|---|
| narrative_summary | 500 字 | 超出截断尾部 + "..." |
| memory_refs | top 5 | retrieve 只返 5 |
| mastery 弱项 | top 5 | 按掌握度升序 |
| mastery 强项 | top 3 | 按掌握度降序 |
| 整个 learner_block | 1500 字 | 超出按上述比例缩 + 末尾 "(truncated)" |

#### 6.4.3 注入开关

| 开关 | 控制方 | 默认 | 行为 |
|---|---|---|---|
| `personalization_enabled` | 学生 | true | false 时 learner_block 为空 |
| `narrative_disabled_by_admin` | 管理员 | false | true 时摘要不下发（仅 KC + memory） |
| `learner_block_max_chars` | 配置 | 1500 | 超过截断 |

#### 6.4.4 摘要生成 SYSTEM_PROMPT

```
你是教学画像生成助手。请基于以下学生数据，生成一段中文学习摘要。

要求：
- 字数 ≤ 500
- 用学生的视角（"近 30 天小明做了 X 题"）
- 仅陈述事实，不提任何鼓励 / 评价 / 励志语
- 不暴露内部检测器名（如 misconception_detected_ast）
- 必须包含：题量统计、AC 率、最常涉及的 KC、仍在反复的错误模式（≤2 个）、最近一次错误概览
- 输出 JSON：{"summary_text": "...", "top_kcs": [...], "top_errors": [...]}
- 任一字段无法生成则返回该字段为空数组 / 空字符串，不要编造
```

### 6.5 学生侧画像 Dashboard

#### 6.5.1 API 设计

```http
GET /ai-tutor/profile/me
Response 200:
{
  "user_id": 123,
  "personalization_enabled": true,
  "is_user_overridden": false,
  "narrative_summary": {
    "version": 7,
    "text": "近 30 天小明做了 18 道题...",
    "updated_at": "2026-04-25T10:00:00Z",
    "auto_generated": true
  },
  "learning_style": {
    "key": "step_by_step",
    "label": "喜欢被逐步引导"
  },
  "top_weak_kcs": [
    {"name": "for 循环边界", "mastery": 0.34},
    {"name": "range 语义", "mastery": 0.41}
  ],
  "top_strong_kcs": [
    {"name": "变量赋值", "mastery": 0.92}
  ],
  "top_errors": [
    {"taxonomy_label": "循环边界错误", "count_30d": 4, "last_seen": "2026-04-23"}
  ],
  "stats_30d": {
    "problems_attempted": 18,
    "problems_ac": 12,
    "ac_rate": 0.667,
    "avg_attempts_per_ac": 2.3
  }
}

PATCH /ai-tutor/profile/me/preferences
Request: { "personalization_enabled": false }
Response: { "ok": true }

POST /ai-tutor/profile/me/refresh
Response: { "ok": true, "version": 8, "summary_text": "..." }

POST /ai-tutor/profile/me/summary/override
Request: { "summary_text": "我是 ANALYTICAL，请用严谨推理" }
Response: { "ok": true, "version": 9 }
```

#### 6.5.2 前端组件

`ProfileDrawer.vue`：

- 在 `UnifiedAgentPanel.vue` 顶部加一个"我的学习画像"入口按钮；
- 点击打开 Drawer，展示上述字段；
- 提供 4 个交互：「个性化推理」开/关、「自动生成摘要」开/关、「手动改写摘要」、「重新生成」；
- 学生关闭个性化后，Panel 顶部显示一行灰色提示："已关闭个性化，AI 将以通用模式回应"。

### 6.6 教师侧班级画像（可选，最小化）

仅做"班级聚合"，不做学生个体下钻：

```http
GET /ai-tutor/profile/class/{classId}
Response 200:
{
  "class_id": "C001",
  "snapshot_at": "2026-04-25T10:00:00Z",
  "student_count": 45,
  "active_student_count": 38,
  "kc_distribution": [
    {"kc_name": "for 循环边界", "weak_student_count": 22, "weak_ratio": 0.49},
    {"kc_name": "range 语义", "weak_student_count": 18, "weak_ratio": 0.40}
  ],
  "top_class_errors": [
    {"taxonomy_label": "循环边界错误", "occurrence_count": 87}
  ],
  "frustration_distribution": {"low": 25, "medium": 10, "high": 3, "severe": 0}
}
```

实现路径：`ClassProfileAggregationService` 直接 SQL aggregate `ai_learner_profile_snapshot` + `ai_learning_event`，无需新表。

---

## 七、全链路时序

### 7.1 学生提交错误代码 → Yoshino 个性化纠错

```
学生在题目页提交错误代码
        │
        ▼
Java AITutorController /ai-tutor/run-event
        │ event_data={ submission_id }
        ▼
TutorGraphClient.startRun(event="ERROR_FEEDBACK", state)
        │
        ▼
services/tutor-graph/main.py
        │
        ▼
evidence.py
   ├─ workflow_context  ← Java workflow_context API
   ├─ diagnosis_evidence ← Java diagnosis API
   ├─ learner_state    ← Java getLearnerState(context_signals={
   │                          current_kcs: ["for_loop", "range"],
   │                          current_error_context: "IndexError: list index out of range"
   │                       })
   │   └─ Java 端: LearnerProfileProjector.project(..., contextSignals)
   │       ├─ MasteryService.projectMastery
   │       ├─ LearnerMemorySemanticRetrievalService.retrieveByContext
   │       │   ← embedding(current_error_context) + KC filter
   │       │   → top 5 memory_refs (按语义距离排序)
   │       ├─ LearnerNarrativeSummaryService.loadOrGenerate
   │       │   → narrative_summary
   │       └─ inferLearningStyle → STEP_BY_STEP
   └─ similar_errors    ← Java similarErrors API
        │
        ▼
nodes/diagnosis.py
   ├─ system_prompt = SYSTEM_PROMPT_BASE + assemble_learner_block(learner_state)
   ├─ user_prompt = ... (代码、错误信息)
   └─ llm_client.generate_json
        │
        ▼
LLM 输出（带个性化）：
{
  "root_cause": "你又在 range(n) 边界上漏掉了上界...",
  "is_recurring": true,
  "encouragement": "已经第 4 次了，这次我们用画图一起看一下",
  "mentor_role": "Yoshino",
  ...
}
        │
        ▼
projection.py → 写回 ai_tutor_card / 触发事件
        │
        ▼
前端 SSE → UnifiedAgentPanel 渲染卡片
```

### 7.2 学生 AC 后 → 自然语言摘要 incremental update

```
AC_REVIEW 节点完成
        │
        ▼
Java AITutorWorkflowDomainServiceImpl.persistAcReview(...)
        ├─ LearnerMemoryService.saveTutoringConclusion(...)
        ├─ NEW: LearnerNarrativeSummaryService.refreshIfStale(userId, lastEventId)
        │       ├─ 计算自上次摘要起新增事件数
        │       ├─ 若 ≥ 3 → 触发摘要 LLM 调用
        │       └─ 写回 ai_learner_narrative_summary（version++）
        └─ 发布 NATS 事件 LEARNER_NARRATIVE_UPDATED
              └─ 前端通过 WebSocket 收到 → 顶部气泡："你的学习画像已更新"
```

---

## 八、契约示例

### 8.1 `LearnerStateView`（Java → Python，新版本）

```json
{
  "calibrated": true,
  "mastery_by_kc": {"for_loop_boundary": 0.34, "range_semantics": 0.41, "variable_assignment": 0.92},
  "weak_kcs": ["for_loop_boundary", "range_semantics"],
  "misconception_distribution": {"loop_boundary": 0.45, "off_by_one": 0.30},
  "recent_behavior": {"consecutiveErrors": 2, "submissionCount": 5},
  "frustration_level": "medium",
  "confidence_proxy": "low",
  "recommended_action_bias": {
    "current_phase": "ERROR_FEEDBACK",
    "teaching_style": "step_by_step",
    "teaching_style_prompt": "【学生偏好】喜欢被逐步引导..."
  },
  "memory_refs": [
    {
      "memory_key": "notebook:abc-123",
      "memory_summary": "题 5：for i in range(n) 漏掉边界，根因=对 range 半开区间不熟",
      "memory_type": "error_pattern",
      "confidence": 0.92,
      "source_problem_id": 5,
      "distance": 0.08
    }
  ],
  "narrative_summary": "近 30 天小明做了 18 道题，AC 12 道。集中在 for 循环与字符串切片，仍在 range 边界条件上反复出错（已发生 4 次）。最近一次错误是第 7 题 missing range upper bound。",
  "personalization_enabled": true
}
```

### 8.2 `assemble_learner_block` 输出（节点 SYSTEM_PROMPT 末尾）

```
[学习者画像]
近 30 天小明做了 18 道题，AC 12 道。集中在 for 循环与字符串切片，
仍在 range 边界条件上反复出错（已发生 4 次）。最近一次错误是第 7 题 missing range upper bound。
[教学风格偏好]
【学生偏好】喜欢被逐步引导：请把解题步骤按 1/2/3 编号列出，每一步说清楚目的和验证方式，避免一口气给出完整结论。
[召回的相关历史记忆]
1. (置信 0.92, 距离 0.08) 题 5：for i in range(n) 漏掉边界，根因=对 range 半开区间不熟
2. (置信 0.85, 距离 0.12) 题 3：for j in range(1, n) 当 n=0 时跳过循环，根因=未考虑空输入
3. (置信 0.78, 距离 0.14) 错题本反思：range 在我脑里像数学区间 [a,b]，应记为 [a,b)
[关键知识点掌握度]
弱：for_loop_boundary(0.34)、range_semantics(0.41)
强：variable_assignment(0.92)、print_format(0.88)
```

---

## 九、工作量评估

> 工作量按"单人工程师 + 已熟悉 Alethicode 代码"估算，单位为人日。

| 模块 | 任务 | 人日 |
|---|---|:---:|
| **数据层** | Flyway V0039 迁移 + 索引 | 0.5 |
| **服务层 (Java)** | `LearnerNarrativeSummaryService` + 单测 | 2 |
|  | `LearnerMemorySemanticRetrievalService` + 单测 | 1.5 |
|  | `LearnerProfileProjector.project()` 扩 ContextSignals + 兼容 + 单测 | 1 |
|  | `ProfileViewService` + 脱敏单测 | 1 |
|  | `InternalAITutorToolService.getLearnerState()` 契约升级 | 0.5 |
|  | `AITutorWorkflowDomainServiceImpl.persistAcReview` 接 refreshIfStale | 0.5 |
|  | `AITutorController` 4 个 REST 端点 + DTO | 1 |
| **tutor_graph (Python)** | `nodes/prompts.py` + 单测 | 1 |
|  | 9 个节点接 `assemble_learner_block` + 单测 | 2 |
|  | `evidence.py` 调整 + 单测 | 0.5 |
|  | `clients/java_tools_client.py` 加 context_signals 入参 | 0.5 |
| **前端 (Vue)** | `ProfileDrawer.vue` + 4 个交互按钮 | 2 |
|  | `useProfileApi.js` composable | 0.5 |
|  | `UnifiedAgentPanel.vue` 顶部入口 + WebSocket 提示 | 0.5 |
| **集成 / E2E** | 4 个核心场景 E2E（开关 / 注入 / 摘要 / 召回） | 1.5 |
| **可选** | `ClassProfileAggregationService` + Controller + 教师页 | 2 |
| **合计**（不含可选） | | **15.5 人日** |
| **合计**（含教师可选） | | **17.5 人日** |

---

## 十、验收标准

### 10.1 单元测试（必过）

| 测试 | 验证点 |
|---|---|
| `LearnerNarrativeSummaryServiceTest#firstGenerateUsesLlm` | 首次生成调用 `AiModelGateway.callForJson`，写表 version=1 |
| `LearnerNarrativeSummaryServiceTest#refreshIfStaleSkipsWhenFresh` | 12h 内不重生 |
| `LearnerNarrativeSummaryServiceTest#refreshIfStaleSkipsWhenLessThan3NewEvents` | 新事件 < 3 不重生 |
| `LearnerNarrativeSummaryServiceTest#userOverriddenSummaryNotOverwritten` | `is_user_overridden=true` 时 refresh 不覆盖 |
| `LearnerMemorySemanticRetrievalServiceTest#orderByDistance` | 同 user 多条 memory，按 cosine distance 升序返回 |
| `LearnerProfileProjectorTest#includesNarrativeSummary` | LearnerState.narrativeSummary 不为 null |
| `LearnerProfileProjectorTest#disabledPersonalizationReturnsEmptyNarrative` | personalization_enabled=false 时 narrative=空字符串 |
| `ProfileViewServiceTest#redactsRawDetectorNames` | 学生侧不暴露 misconception_detected_ast 等内部名 |
| `services/tutor-graph/tests/test_prompts.py::test_assemble_learner_block_when_disabled` | 学生关闭后 block 为空 |
| `services/tutor-graph/tests/test_prompts.py::test_assemble_learner_block_truncation` | 超长 narrative 截断到 1500 字 |
| `services/tutor-graph/tests/nodes/test_diagnosis.py::test_system_prompt_includes_learner_block` | SYSTEM_PROMPT 末尾含 [学习者画像] 标记 |

### 10.2 集成测试（必过）

| 测试 | 验证点 |
|---|---|
| `AITutorWorkflowEvidenceIntegrationTest#readingNodeReceivesLearnerState` | READING 节点 evidence 含 learner_state |
| `AITutorWorkflowEvidenceIntegrationTest#chatNodeReceivesLearnerState` | CHAT 节点 evidence 含 learner_state |
| `AITutorWorkflowEvidenceIntegrationTest#errorFeedbackInjectsNarrative` | ERROR_FEEDBACK 节点的 system_prompt 含 narrative_summary 内容 |
| `AITutorWorkflowAcReviewIntegrationTest#acReviewTriggersNarrativeRefresh` | AC_REVIEW 完成后 narrative_summary version 自增 |
| `ProfileApiIntegrationTest#getMyProfileReturnsCorrectFields` | /ai-tutor/profile/me 字段完整 |
| `ProfileApiIntegrationTest#disablePersonalizationStopsInjection` | PATCH 后 evidence 中 personalization_enabled=false |

### 10.3 E2E 验收（必过）

| 场景 | 期望 |
|---|---|
| 学生提交错误代码 | 控制台日志 `learner_block_injected=true`；卡片中 mention 历史错误 |
| 学生关闭个性化 | 后续节点 prompt 不含 [学习者画像] |
| 学生进入题目页 | 摘要为空时立即触发首次生成；非空且 < 12h 时复用 |
| 学生 AC 后 | 顶部气泡 "你的学习画像已更新"，version+1 |
| 学生在 ProfileDrawer 改写摘要 | 后续 12h 不被自动覆盖 |

### 10.4 教学指标（上线后 4 周内观察）

| 指标 | 目标 |
|---|---|
| AI 反馈 1h 内 AC 率 | 提升 ≥ 5% |
| 连续 3 天打开 AI 助手的学生比例 | 提升 ≥ 5% |
| 学生主观满意度（每周 NPS） | 提升 ≥ 5 点 |
| 学生关闭个性化比例 | < 10% |

任一指标负向超过 -3% 视为回滚信号。

---

## 十一、风险与缓解

| # | 风险 | 概率 | 影响 | 缓解 |
|---|---|---|---|---|
| R1 | 摘要生成 LLM 失败导致 evidence 装配卡死 | 低 | 中 | failfast：摘要失败时返回上一版本；首次生成失败则 narrative=空 + 日志告警 |
| R2 | learner_block 注入后 token 暴涨 | 中 | 中 | 截断到 1500 字；统计每节点平均 token 增量；超过阈值告警 |
| R3 | 语义召回返回不相关 memory | 中 | 低 | top-K 仅取距离 < 0.4；同时保留 confidence ≥ 0.5 过滤 |
| R4 | 学生看见画像后产生焦虑 | 低 | 中 | 摘要 prompt 强制"不带评价 / 励志语"；提供一键关闭 |
| R5 | LearningStyle 误判（学生数据少） | 中 | 低 | 现有 `inferLearningStyle` 已要求 ≥ 20 条反馈才决策；< 20 条用默认 STEP_BY_STEP |
| R6 | personalization_enabled 旁路不彻底 | 低 | 高 | 在 `assemble_learner_block` 单点判断；单测覆盖 |
| R7 | 教师班级 API 暴露学生隐私 | 低 | 高 | 仅返回 aggregated 数据，不返回学生 ID 列表；权限校验严格 |

---

## 十二、第一性原理自检

| 自检项 | 通过 |
|---|---|
| 不引入兼容性 / 补丁性方案 | ✅ 加列 + 新表，不并存两套 Memory |
| 不过度设计 | ✅ 教师班级 API 标记为可选；不引入新向量库 / 新 LLM Provider |
| 不擅自扩展业务目标 | ✅ 仅围绕"角色感知学生 + 学生可见画像" |
| 不写防御性 / 兜底逻辑 | ✅ 摘要失败 failfast；不生成假摘要 |
| 全链路逻辑验证 | ✅ 七、全链路时序 已覆盖入参 → 处理 → 输出 → 注入 |
| 重命名全链路同步 | N/A（无重命名） |
| 不做与当前需求无关的兜底 | ✅ 不引入定时器；不做"低置信容错" |

---

## 附录 A：测试矩阵

| 维度 | 测试数 |
|---|:---:|
| Java 单测（新增） | 11 |
| Python 单测（新增） | 5 |
| 集成测试（新增） | 6 |
| 前端单测（新增） | 4 |
| E2E（新增） | 5 |
| **合计新增测试** | **31** |

---

## 附录 B：DDL 草案

`backend/src/main/resources/db/migration/V0039__add_learner_narrative_summary.sql`：

```sql
-- 1. 新表
CREATE TABLE ai_learner_narrative_summary (
    user_id            BIGINT       NOT NULL,
    summary_version    INTEGER      NOT NULL DEFAULT 1,
    summary_text       TEXT         NOT NULL,
    summary_payload    JSONB        NOT NULL DEFAULT '{}'::jsonb,
    learning_style_key VARCHAR(32)  NOT NULL DEFAULT 'step_by_step',
    last_event_id      BIGINT,
    last_session_id    VARCHAR(64),
    is_user_overridden BOOLEAN      NOT NULL DEFAULT FALSE,
    user_disabled      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMP    NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id)
);

CREATE INDEX idx_aln_user_updated
    ON ai_learner_narrative_summary(user_id, updated_at DESC);

-- 2. 现有表加列
ALTER TABLE ai_learner_profile_snapshot
    ADD COLUMN narrative_summary_version INTEGER,
    ADD COLUMN narrative_summary_text    TEXT;

-- 3. 数据回填策略
-- 不在迁移中调用 LLM。首次访问 loadOrGenerate 自动生成。
```

---

**设计完。**
