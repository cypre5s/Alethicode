# Unified Chat（多模式共享上下文）设计

> **文档编号**：ALETH-PLAN-2026-0425-P3
> **关联调研**：[`docs/reports/2026-04-25-deeptutor-inspiration-survey.md`](../reports/2026-04-25-deeptutor-inspiration-survey.md)
> **关联设计**：
> - [`docs/plans/2026-04-25-persistent-memory-layer-design.md`](2026-04-25-persistent-memory-layer-design.md)（弱依赖：可借力 Memory）
> - [`docs/plans/2026-04-25-visualize-capability-design.md`](2026-04-25-visualize-capability-design.md)（可视化是一种 Mode）
> **优先级**：P3（建议在 P1 完成后启动；与 P2 并行）
> **作者**：AI Coding Assistant
> **创建日期**：2026-04-25

> **一句话目标**：让 8 张 AI 卡片之间 **真正共享上下文**——学生在 Chat 里 @某张过往卡片时，AI 能精确引用；学生主动切换"我现在想做什么"（导读 / 思路分析 / 纠错 / 可视化 / 复盘 / 迁移）时，整个会话上下文不丢、不重、不串。

---

## 目录

- [一、设计动机](#一设计动机)
- [二、现状盘点](#二现状盘点)
- [三、差距分析](#三差距分析)
- [四、设计目标与非目标](#四设计目标与非目标)
- [五、概念厘清：Phase vs Mode vs Card](#五概念厘清phase-vs-mode-vs-card)
- [六、整体架构](#六整体架构)
- [七、详细设计](#七详细设计)
- [八、契约示例](#八契约示例)
- [九、全链路时序](#九全链路时序)
- [十、工作量评估](#十工作量评估)
- [十一、验收标准](#十一验收标准)
- [十二、风险与缓解](#十二风险与缓解)
- [十三、第一性原理自检](#十三第一性原理自检)
- [附录 A：Mode 与 Phase / Card 映射](#附录-amode-与-phase--card-映射)
- [附录 B：@引用语法](#附录-b引用语法)

---

## 一、设计动机

### 1.1 教学场景痛点

学生在做一道题的过程中，会经历以下典型片段：

1. 看导读（PROBLEM_GUIDE 卡）
2. 自己想思路 → 让 AI 帮忙分析（IDEATE_ANALYSIS 卡）
3. 写代码、提交错误 → 让 AI 纠错（ERROR_DIAGNOSIS 卡）
4. 看不懂自己的循环 → 让 AI 画图（VISUALIZE 卡）
5. AC 后 → 让 AI 复盘（POST_AC 卡）
6. 想看变体 → 让 AI 推迁移题（TRANSFER_PROBLEM 卡）

当前的痛点：

1. **学生在 Chat 里说"那张图你画错了"，AI 不知道指哪张图**——因为 Chat 节点的 evidence 只有 `learner_state`，没有"刚才的卡片"；
2. **学生想"再来一次思路分析"**——必须等系统自动 transition 到 IDEATING phase，无法主动触发；
3. **学生 AC 后想"我能不能再画一下复杂度对比"**——POST_AC phase 不会主动触发 VISUALIZE 卡，学生没有触发入口；
4. **8 卡片之间无引用机制**——比如 ERROR_DIAGNOSIS 卡输出"这个错和前面 IDEATE 阶段你假设的边界不符"，但前端只是字面文本，无法点击跳转回那张 IDEATE 卡。

### 1.2 第一性原理

**学生的"教学体验"质量 = 上下文连续性 + 主动权。**

- 上下文连续性：AI 必须知道刚才发生了什么；
- 主动权：学生必须能在任何时刻说"我现在想要什么"。

Alethicode 当前是**强 FSM 驱动**：阶段切换由系统编排（READING→IDEATING→CODING→...），上下文藏在 `node_outputs` 字典里，前端能看见但 AI Chat 节点看不见，学生也不能主动跳转。

DeepTutor 是**强用户驱动**：6 模式（Chat / Deep Solve / Quiz / Deep Research / Math Animator / Visualize）随时可切，上下文跨模式共享。

本设计要做的是**"FSM 编排 + 用户主动模式"双驱动**——保留 Phase FSM 作为系统级编排，新增 Mode 作为用户视角的主动权，并打通跨卡片上下文引用。

---

## 二、现状盘点

### 2.1 已具备能力（不重做，仅复用）

| 模块 | 现状 |
|---|---|
| `UnifiedAgentPanel.vue` | 1847 行，已经是统一容器：8 卡片、Plan、Checkpoint、Approval 都在里面 |
| 9 种 `CardType` | PROBLEM_GUIDE / IDEATE_ANALYSIS / FADED_EXAMPLE / ERROR_DIAGNOSIS / EXECUTION_TRACE_EXPLAINER / POST_AC / TRANSFER_PROBLEM / KNOWLEDGE_REVIEW / AI_REPLY |
| `tutor_graph` 7 阶段 FSM | READING / IDEATING / CODING / ERROR_FEEDBACK / AC_REVIEW / TRANSFER / KNOWLEDGE_REVIEW + CHAT 旁路 |
| `state.node_outputs` | dict[str, Any]，跨节点共享，但 Chat 节点不消费 |
| `state.evidence_pack` | dict，按事件类型装载不同字段 |
| `client_event` | 已支持 13 种 event 类型（含 PLAN_*） |
| 前端 SSE / WebSocket | `runtime_state` 已通过 SSE 实时推前端 |
| 前端会话 ID `session_id` | 已贯穿前后端 |

### 2.2 当前 8 卡片触发方式

| Card | 触发 | 触发方 |
|---|---|---|
| PROBLEM_GUIDE | READING phase 进入时 | 系统 |
| IDEATE_ANALYSIS | IDEATING phase 进入时 | 系统 |
| FADED_EXAMPLE | 失败次数多时降级 | 系统 |
| ERROR_DIAGNOSIS | 学生提交错误 | 系统（提交触发） |
| EXECUTION_TRACE_EXPLAINER | CODING / ERROR_FEEDBACK 时 | 系统 |
| POST_AC | AC_REVIEW phase | 系统（AC 触发） |
| TRANSFER_PROBLEM | TRANSFER phase | 系统 |
| KNOWLEDGE_REVIEW | 学生主动点欢迎卡按钮 | **学生** |
| AI_REPLY (Chat) | 学生在输入框打字 | **学生** |

可见：仅 KNOWLEDGE_REVIEW 和 Chat 是学生可主动触发的。

---

## 三、差距分析

### G1. Chat 节点不消费 node_outputs

**现状**（`services/tutor-graph/app/nodes/chat.py`）：

```python
user_msg = (
    f"当前阶段: {phase}\n"
    f"用户消息: {message}\n"
    f"历史: {history[-6:]}"
)
```

**问题**：完全没有引用过去的卡片。学生说"刚才那张图画错了"，LLM 不知道哪张图。

### G2. evidence_pack["CHAT"] = []（空）

**现状**（`services/tutor-graph/app/nodes/evidence.py`）：

```python
"CHAT": [],
```

**问题**：Chat 不要任何 evidence，所以也没机会装载 last_cards。

### G3. 学生无主动触发模式的入口

**现状**：UnifiedAgentPanel 没有"我想要哪种 Mode"的快捷入口（除了顶部欢迎卡的 starter actions，且只在题目刚进入时显示）。

**问题**：学生在 IDEATING 阶段想"再画一下流程图"无路可走；想"先看复杂度对比"也没入口。

### G4. 跨卡片 @引用机制缺失

**现状**：卡片输出文本里偶有"参考前面 IDEATE 阶段的思路"这类描述，但都是字面文本，无 ID 锚点。

**问题**：学生看到这句话只能自己上下翻找；点击不能跳转。

### G5. 模式与阶段语义混淆

**现状**：阶段（Phase）和卡片类型几乎 1:1 绑定（READING→PROBLEM_GUIDE / IDEATING→IDEATE_ANALYSIS / ...）。

**问题**：当用户主动调"再来一次 PROBLEM_GUIDE"时，系统不知道是要"切回 READING phase"还是"在当前 phase 调一次 problem_guide_node"，缺一个明确的语义层。

### G6. 跨模式上下文未做权限边界

**现状**：所有 node_outputs 都暴露给所有节点。

**问题**：未来如果引入"教师模式 / 旁观模式"等，会有权限隔离需求；当前没有抽象层。

---

## 四、设计目标与非目标

### 4.1 设计目标（必达）

| # | 目标 | 验收方式 |
|---|---|---|
| O1 | 引入 `Mode` 概念（用户视角），与 `Phase`（系统视角）解耦 | 单测：Mode 切换不强制触发 Phase 转换 |
| O2 | Chat 节点的 evidence 增加 `last_cards`（最近 N 张卡片摘要） | 集成测：Chat 输出引用历史卡片 |
| O3 | 学生可在输入框使用 `@card:<id>` 或 `@last_error` 等语法引用过往卡片 | 前端解析 + 后端识别 + LLM 注入 |
| O4 | 学生可主动切换 Mode，触发对应 capability（PROBLEM_GUIDE / IDEATE / ERROR_DIAG / VISUALIZE / KNOWLEDGE_REVIEW / TRANSFER / CHAT） | 前端 Mode Bar + API |
| O5 | 跨模式时 `ConversationContext`（会话上下文池）持久化共享 | 数据表 + 单测 |
| O6 | 卡片输出含 `card_id` 锚点，前端可点击跳转 | 前端跳转交互 |

### 4.2 非目标（明确不做）

| 非目标 | 原因 |
|---|---|
| 不替换 Phase FSM | Phase 仍是系统编排核心 |
| 不允许学生强制跳过 Phase | 教学逻辑需要按节奏推进 |
| 不引入新的会话表 | 复用 `ai_tutor_card` + `ai_tutor_session` |
| 不做"多人共享同一会话" | 当前是单学生单题 |
| 不做"会话回滚 / 删除卡片" | 教学溯源不允许 |
| 不引入 nanobot 多 Bot 框架 | 与 5 角色冲突 |

---

## 五、概念厘清：Phase vs Mode vs Card

为避免后续讨论歧义，本节明确三者关系。

### 5.1 三层语义

```
                         ┌─────────────────────────────┐
                         │    Mode（用户视角）          │
                         │    "我现在想要什么"          │
                         │    - 导读 / 思路 / 纠错 /    │
                         │      可视化 / 复盘 / 迁移 /   │
                         │      复习 / 闲聊             │
                         └──────────┬──────────────────┘
                                    │ 用户主动 / Phase 系统触发
                                    ▼
                         ┌─────────────────────────────┐
                         │  Capability（节点视角）      │
                         │  实际跑哪个 tutor_graph 节点 │
                         │   problem_guide / ideate /   │
                         │   diagnosis / visualize / ...│
                         └──────────┬──────────────────┘
                                    │ 节点输出
                                    ▼
                         ┌─────────────────────────────┐
                         │    Card（产物）              │
                         │  写入 ai_tutor_card 一条     │
                         └──────────┬──────────────────┘
                                    │ 副作用
                                    ▼
                         ┌─────────────────────────────┐
                         │  Phase（系统编排状态）       │
                         │  READING/IDEATING/CODING/   │
                         │  ERROR_FEEDBACK/AC_REVIEW... │
                         │  按 FSM 转换                 │
                         └─────────────────────────────┘
```

### 5.2 三者职责

| 层 | 谁定义 | 谁修改 | 持久化在哪 |
|---|---|---|---|
| Phase | tutor_graph FSM | 系统（按事件触发 transition） | `tutor_graph_state.current_phase` |
| Mode | 用户 + 系统 | 用户主动选 / 系统按 Phase 默认 | `ai_tutor_session.active_mode` |
| Card | 节点输出 | 节点写入 | `ai_tutor_card` |

### 5.3 核心约束

- **Mode 切换不一定 Phase 切换**：学生在 CODING 阶段切到"想看复杂度对比 (Visualize)"，Phase 保持 CODING；
- **Phase 切换一定 Mode 切换**：READING→IDEATING 时 active_mode 自动跟随 IDEATE；
- **Card 始终带 phase + mode 双标签**：便于追溯；
- **Mode 受 Phase 限制**：某些 Mode 只在某些 Phase 可触发（见附录 A）。

---

## 六、整体架构

### 6.1 数据流向

```
  ┌─────────────────────────────────────────────────────┐
  │  前端 UnifiedAgentPanel                             │
  │   ├─ ModeBar（新增）：导读/思路/纠错/可视化/...      │
  │   ├─ ChatComposer（增强）：@card:<id> 语法解析      │
  │   ├─ 8 + 1 卡片渲染（每张含 card_id 锚点）          │
  │   └─ "跳转到引用卡片" 交互                           │
  └─────────────────────┬───────────────────────────────┘
                         │ HTTP / SSE
  ┌──────────────────────┴──────────────────────────────┐
  │   AITutorController                                  │
  │     POST /ai-tutor/run-event                         │
  │       (event_data 增加 mode + references)            │
  │     POST /ai-tutor/mode/switch                       │
  │       (学生主动切 Mode)                              │
  │     GET /ai-tutor/conversation/{sessionId}           │
  │       (拉取 ConversationContext，含 last cards)      │
  └──────────────────────┬──────────────────────────────┘
                         │
  ┌──────────────────────▼──────────────────────────────┐
  │  ConversationContextService（新增）                  │
  │   - listLastCards(sessionId, limit)                  │
  │   - resolveReferences(refs[])                        │
  │   - getActiveMode(sessionId)                         │
  │   - switchMode(sessionId, newMode)                   │
  └──────────────────────┬──────────────────────────────┘
                         │
                         ▼
                ┌────────────────────────────┐
                │  ai_tutor_card (现有)      │
                │  ai_tutor_session (现有)   │
                │   + active_mode 字段       │
                │   + last_cards 视图        │
                └────────────────────────────┘
                         │
                         │ /internal/ai-tutor/conversation/*
                         ▼
       ┌─────────────────────────────────────┐
       │ tutor_graph (Python LangGraph)      │
       │   evidence.py：CHAT 增加 last_cards   │
       │   nodes/chat.py：消费 last_cards +   │
       │     references                       │
       │   state.user_mode: 新增字段           │
       │   FSM 不变                           │
       └─────────────────────────────────────┘
```

### 6.2 新增组件清单

| 组件 | 类型 | 责任 |
|---|---|---|
| `Mode` | Java enum | 用户视角的模式枚举 |
| `ConversationContextService` | Java service | 跨卡片上下文池 |
| `ReferenceResolver` | Java service | 解析 `@card:<id>` 等引用语法 |
| `ai_tutor_session.active_mode` | 字段 | 当前活跃 Mode |
| `AITutorController.switchMode()` | REST | 学生主动切 Mode |
| `AITutorController.getConversation()` | REST | 拉 ConversationContext |
| `tutor_graph` `state.user_mode` | 字段 | LangGraph state 加 mode |
| `tutor_graph` `nodes/chat.py` | 改造 | 消费 last_cards + references |
| `tutor_graph` `evidence.py` | 改造 | CHAT 增加 last_cards |
| `ModeBar.vue` | 前端组件 | Mode 切换条 |
| `ChatComposer.vue` | 前端组件 | 输入框增强（@语法） |
| `useReferenceParse.js` | 前端 composable | 解析 @ 语法 |
| `CardJumpAnchor.vue` | 前端组件 | 卡片间跳转锚点 |

---

## 七、详细设计

### 7.1 Mode 枚举

```java
public enum Mode {
    READING("reading", "导读", "PROBLEM_GUIDE"),
    IDEATING("ideating", "思路分析", "IDEATE_ANALYSIS"),
    CODING("coding", "陪练编码", null),                          // 不直接产卡
    ERROR_DIAGNOSIS("error_diagnosis", "纠错", "ERROR_DIAGNOSIS"),
    VISUALIZE("visualize", "可视化", "VISUALIZE"),
    AC_REVIEW("ac_review", "复盘", "POST_AC"),
    TRANSFER("transfer", "迁移练习", "TRANSFER_PROBLEM"),
    KNOWLEDGE_REVIEW("knowledge_review", "知识点复习", "KNOWLEDGE_REVIEW"),
    CHAT("chat", "自由提问", "AI_REPLY");

    private final String key;
    private final String label;
    private final String defaultProducedCardType;

    public Set<Phase> allowedPhases() { ... }   // 见附录 A
    public boolean isAvailableIn(Phase phase) { return allowedPhases().contains(phase); }
}
```

### 7.2 数据库改动

#### 7.2.1 现有表加列

```sql
ALTER TABLE ai_tutor_session
    ADD COLUMN active_mode VARCHAR(32) NOT NULL DEFAULT 'reading',
    ADD COLUMN last_mode_switched_at TIMESTAMP;

ALTER TABLE ai_tutor_card
    ADD COLUMN mode_when_produced VARCHAR(32),
    ADD COLUMN referenced_card_ids JSONB DEFAULT '[]'::jsonb;

CREATE INDEX idx_ai_tutor_card_session_created
    ON ai_tutor_card(session_id, created_at DESC);
```

#### 7.2.2 Flyway 迁移

新增 `V0040__add_mode_and_references.sql`（在 P1 之后顺序）。

### 7.3 ConversationContextService

```java
@Service
public class ConversationContextService {

    private static final int DEFAULT_LAST_CARDS_LIMIT = 5;
    private static final int MAX_LAST_CARDS_LIMIT = 10;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public ConversationContext load(String sessionId) {
        Mode activeMode = getActiveMode(sessionId);
        List<CardSummary> lastCards = listLastCards(sessionId, DEFAULT_LAST_CARDS_LIMIT);
        return new ConversationContext(sessionId, activeMode, lastCards);
    }

    public List<CardSummary> listLastCards(String sessionId, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), MAX_LAST_CARDS_LIMIT);
        return jdbc.query("""
            SELECT id, card_type, mode_when_produced, payload::text AS payload_json,
                   linked_card_id, created_at
            FROM ai_tutor_card
            WHERE session_id = ?
            ORDER BY created_at DESC
            LIMIT ?
            """, ... , sessionId, safeLimit);
    }

    public List<CardSummary> resolveReferences(String sessionId, List<String> references) {
        // references 形如 ["@card:C-V-001", "@last_error", "@last_visualize"]
        // 返回去重 + 限定 sessionId 后的卡片摘要
        ...
    }

    public Mode getActiveMode(String sessionId) { ... }

    public void switchMode(String sessionId, Mode newMode, Phase currentPhase) {
        if (!newMode.isAvailableIn(currentPhase)) {
            throw new ModeNotAllowedException(newMode, currentPhase);
        }
        jdbc.update("""
            UPDATE ai_tutor_session
            SET active_mode = ?, last_mode_switched_at = NOW()
            WHERE session_id = ?
            """, newMode.key(), sessionId);
    }
}
```

`CardSummary` 结构（注入 prompt 时使用）：

```java
public record CardSummary(
    String cardId,
    String cardType,           // "error_diagnosis"
    String modeWhenProduced,   // "error_diagnosis"
    String shortText,          // 截取 payload 的关键字段，≤200 字
    String linkedCardId,       // 若该卡引用了别的卡
    Instant createdAt
) {
    public String toPromptLine() {
        return String.format("[%s|%s|%s] %s",
            cardId, cardType, formatRelativeTime(createdAt), shortText);
    }
}
```

### 7.4 ReferenceResolver

```java
@Service
public class ReferenceResolver {

    private static final Pattern CARD_ID_REF = Pattern.compile("@card:([A-Za-z0-9\\-]+)");
    private static final Pattern SHORTHAND_REF = Pattern.compile(
        "@(last_error|last_visualize|last_ideate|last_guide|last_ac|last_transfer|all)"
    );

    private final ConversationContextService contextService;

    public ResolvedReferences parse(String sessionId, String userMessage) {
        Set<String> explicitIds = new HashSet<>();
        Set<String> shorthands = new HashSet<>();

        Matcher m1 = CARD_ID_REF.matcher(userMessage);
        while (m1.find()) explicitIds.add(m1.group(1));

        Matcher m2 = SHORTHAND_REF.matcher(userMessage);
        while (m2.find()) shorthands.add(m2.group(1));

        List<CardSummary> cards = new ArrayList<>();
        if (!explicitIds.isEmpty()) {
            cards.addAll(contextService.resolveByIds(sessionId, explicitIds));
        }
        for (String shorthand : shorthands) {
            cards.addAll(contextService.resolveByShorthand(sessionId, shorthand));
        }
        return new ResolvedReferences(explicitIds, shorthands, cards);
    }
}
```

简写引用映射：

| 简写 | 含义 |
|---|---|
| `@last_error` | 最近一张 ERROR_DIAGNOSIS 卡 |
| `@last_visualize` | 最近一张 VISUALIZE 卡 |
| `@last_ideate` | 最近一张 IDEATE_ANALYSIS 卡 |
| `@last_guide` | 最近一张 PROBLEM_GUIDE 卡 |
| `@last_ac` | 最近一张 POST_AC 卡 |
| `@last_transfer` | 最近一张 TRANSFER_PROBLEM 卡 |
| `@all` | 最近 5 张任意类型 |

### 7.5 tutor_graph 改动

#### 7.5.1 state 增加 user_mode

```python
# services/tutor-graph/app/graph/state.py
class TutorGraphState(TypedDict, total=False):
    ...
    user_mode: str          # 新增
    references: list[dict]  # 新增：[{card_id, card_type, short_text}]
    last_cards: list[dict]  # 新增
```

#### 7.5.2 evidence.py 增加 last_cards / references

```python
EVENT_EVIDENCE_REQUIREMENTS = {
    ...
    "CHAT": ["learner_state", "last_cards", "references"],   # 新增
}

if "last_cards" in requirements:
    evidence["last_cards"] = await java_client.get_last_cards(
        session_id, limit=5
    )
if "references" in requirements:
    raw_refs = event_data.get("references", [])
    if raw_refs:
        evidence["references"] = await java_client.resolve_references(
            session_id, raw_refs
        )
```

#### 7.5.3 chat.py 改造

```python
SYSTEM_PROMPT = """你是面向非计算机专业 Python 初学者的 AI 导学助手。
在当前题目和当前学习阶段进行导学对话。

要求：
- 不给完整代码
- 不退化为普通闲聊
- 引用当前题、当前阶段、最近输出
- **如果用户消息中含有 @card:<id> 或 @last_xxx，必须明确引用对应卡片的内容**
- **不要凭空编造没看到的卡片内容**
- 输出 JSON 格式，字段: content, history(数组，每项含 role 和 content), referenced_card_ids(数组)
"""

async def chat_node(state, *, llm_client):
    event_data = state.get("event_data", {})
    message = event_data.get("message", "")
    phase = state.get("current_phase", "READING")
    user_mode = state.get("user_mode", "chat")

    last_cards = state.get("last_cards", [])
    references = state.get("references", [])

    # 构造给 LLM 的上下文
    context_block = ""
    if references:
        context_block += "[用户明确引用的卡片]\n"
        for c in references:
            context_block += f"- [{c['card_id']}|{c['card_type']}] {c['short_text']}\n"
    if last_cards:
        context_block += "\n[最近的会话卡片（用户未明确引用，可作背景）]\n"
        for c in last_cards[:5]:
            context_block += f"- [{c['card_id']}|{c['card_type']}] {c['short_text']}\n"

    user_msg = (
        f"当前 Mode: {user_mode}\n"
        f"当前 Phase: {phase}\n"
        f"用户消息: {message}\n"
        f"{context_block}\n"
        f"对话历史: {history[-6:]}"
    )

    result = await llm_client.generate_json(SYSTEM_PROMPT, user_msg, ...)
    # 后处理：result["referenced_card_ids"] 用于前端跳转锚点
    ...
```

#### 7.5.4 其他节点产卡时携带 mode_when_produced

```python
# services/tutor-graph/app/nodes/projection.py

def project_to_card(state, java_client, ...):
    user_mode = state.get("user_mode", _phase_to_default_mode(state["current_phase"]))
    ...
    # 写入 ai_tutor_card 时带 mode_when_produced=user_mode
```

### 7.6 主动切换 Mode

#### 7.6.1 API

```http
POST /ai-tutor/mode/switch
Request:
{
  "session_id": "S001",
  "mode": "visualize"
}
Response 200:
{
  "ok": true,
  "active_mode": "visualize",
  "phase": "CODING"
}
Response 422:
{
  "ok": false,
  "error_code": "MODE_NOT_ALLOWED_IN_PHASE",
  "reason": "Mode 'visualize' not allowed in phase 'READING'"
}
```

#### 7.6.2 切换语义

学生切换 Mode 时：

1. 后端校验 Mode 是否在当前 Phase 允许（附录 A）；
2. 写 `ai_tutor_session.active_mode`；
3. 不自动触发 capability，等学生在 ChatComposer 输入消息或点击 ModeBar 上的"开始"按钮再触发；
4. 触发时按 Mode → Capability 映射调用对应节点。

### 7.7 前端改动

#### 7.7.1 `ModeBar.vue`

挂在 UnifiedAgentPanel 顶部，紧贴 ProfileDrawer 入口：

```html
<ModeBar
  :current-phase="currentPhase"
  :active-mode="activeMode"
  @switch="onModeSwitch"
/>
```

视觉上是一行 9 个 chips（READING / IDEATE / CODING / ERROR_DIAG / VISUALIZE / AC_REVIEW / TRANSFER / KNOWLEDGE / CHAT），不可用 Mode 灰显。

#### 7.7.2 `ChatComposer.vue`

输入框增强：

- 用户输入 `@` 时弹出快捷面板（最近 5 张卡片 + 7 个简写）；
- 选中后插入 `@card:C-V-001` 或 `@last_error` 文本；
- 提交时把 raw text 与 references 数组一并发送：

```javascript
const refs = parseReferences(message)  // ["@card:C-V-001", "@last_error"]
await api.runEvent({
  event: 'CHAT',
  event_data: { message, references: refs, mode: 'chat' }
})
```

#### 7.7.3 `CardJumpAnchor.vue`

每张卡片渲染时附 `id="card-${cardId}"` 锚点；其他卡片输出含 `referenced_card_ids` 时渲染为可点击链接，点击 smooth scroll 到目标卡。

#### 7.7.4 `useReferenceParse.js`

```javascript
export function parseReferences(text) {
  const explicitIds = [...text.matchAll(/@card:([A-Za-z0-9-]+)/g)].map(m => `@card:${m[1]}`)
  const shorthands = [...text.matchAll(/@(last_error|last_visualize|...)/g)].map(m => `@${m[1]}`)
  return [...new Set([...explicitIds, ...shorthands])]
}
```

### 7.8 与 P1 / P2 的协同

| 场景 | P1 (Memory) | P2 (Visualize) | P3 (Unified Chat) |
|---|---|---|---|
| 学生 Chat 里 @last_error 让 AI 解释 | LearnerState 注入风格偏好 | 若 AI 决定可视化，调 Visualize Capability | last_cards 注入 + ChatComposer 解析 |
| 学生切到 VISUALIZE Mode 主动画 KC 雷达 | LearnerState 提供 mastery 数据 | KC_MASTERY_RADAR Intent | switchMode + 调用 Visualize |
| AC 后 Chat 问"你刚才画的复杂度图能再讲讲吗" | Memory 增量更新 | 不重画，引用旧 VISUALIZE 卡 | @last_visualize 引用注入 |

---

## 八、契约示例

### 8.1 Chat 节点 evidence

```json
{
  "learner_state": { ... },
  "last_cards": [
    {"card_id": "C-V-001", "card_type": "visualize", "mode_when_produced": "error_diagnosis",
     "short_text": "for i in range(5) 的 5 次迭代图（来自 Yoshino）",
     "created_at": "2026-04-25T10:30:00Z"},
    {"card_id": "C-E-001", "card_type": "error_diagnosis", "mode_when_produced": "error_diagnosis",
     "short_text": "你又在 range(n) 边界上漏掉了上界",
     "created_at": "2026-04-25T10:29:50Z"},
    {"card_id": "C-I-001", "card_type": "ideate_analysis", "mode_when_produced": "ideating",
     "short_text": "把问题拆为：读输入、算累加、输出",
     "created_at": "2026-04-25T10:25:00Z"}
  ],
  "references": [
    {"card_id": "C-V-001", "card_type": "visualize", "short_text": "for i in range(5) 的 5 次迭代图"}
  ]
}
```

### 8.2 用户消息

```
@card:C-V-001 那张图为什么 i 没有到 5？我是不是 range 用错了
```

### 8.3 Chat 节点输出

```json
{
  "content": "你看的那张图（C-V-001）画的是 for i in range(5)，i 实际取 0~4。range(5) 在 Python 里是【半开区间 [0, 5)】，所以 i 不会到 5。要让 i 到 5，应该写 range(6)。",
  "referenced_card_ids": ["C-V-001"],
  "history": [...]
}
```

### 8.4 前端渲染

Chat 卡片渲染时把 `C-V-001` 渲染为可点击链接，点击 smooth scroll 到 VISUALIZE 卡片。

---

## 九、全链路时序

### 9.1 学生在 Chat 引用历史卡片

```
学生在 ChatComposer 输入 "@card:C-V-001 那张图为什么..."
        │
        ▼
useReferenceParse 解析 references=["@card:C-V-001"]
        │
        ▼
POST /ai-tutor/run-event
   { event: "CHAT", event_data: { message, references, mode: "chat" } }
        │
        ▼
Java AITutorController.runEvent
        ├─ ConversationContextService.resolveReferences → CardSummary 列表
        └─ TutorGraphClient.startRun(state with references + user_mode="chat")
        │
        ▼
tutor_graph evidence.py
   ├─ learner_state ← Java
   ├─ last_cards    ← Java getLastCards(sessionId, 5)
   └─ references    ← 已在 state 里
        │
        ▼
nodes/chat.py
   ├─ system_prompt 已含规范
   ├─ user_msg 含 [用户明确引用的卡片] + [最近的会话卡片]
   └─ llm_client.generate_json
        │
        ▼
LLM 输出 (含 referenced_card_ids)
        │
        ▼
projection.py 写入 ai_tutor_card (mode_when_produced="chat")
        │
        ▼
SSE → 前端
        │
        ▼
UnifiedAgentPanel：
  ├─ 渲染新 Chat 卡（payload 里 C-V-001 渲染为可点击链接）
  └─ 学生点 C-V-001 → smooth scroll 到原 VISUALIZE 卡
```

### 9.2 学生主动切换 Mode

```
学生在 ModeBar 点 "VISUALIZE"
        │
        ▼
POST /ai-tutor/mode/switch
   { session_id, mode: "visualize" }
        │
        ▼
ConversationContextService.switchMode
        ├─ 校验 Mode 是否在 currentPhase 允许
        ├─ 更新 ai_tutor_session.active_mode = "visualize"
        └─ 返回 { ok: true, active_mode: "visualize" }
        │
        ▼
前端 ModeBar 高亮 VISUALIZE，提示 "请输入要可视化的内容或选择快捷模板"
        │
        ▼
学生选 KC 雷达 → POST /ai-tutor/visualize/inline (P2 流程)
```

---

## 十、工作量评估

> 单人工程师 + 已熟悉 Alethicode，单位人日。

| 模块 | 任务 | 人日 |
|---|---|:---:|
| **数据层** | Flyway V0040 迁移（active_mode + mode_when_produced + referenced_card_ids + 索引） | 0.5 |
| **服务层 (Java)** | `Mode` enum + 单测 | 0.5 |
|  | `ConversationContextService` + 单测 | 1.5 |
|  | `ReferenceResolver` + 单测 | 1 |
|  | `AITutorController.switchMode` + `getConversation` | 1 |
|  | `runEvent` 接受 references + mode 入参 | 0.5 |
| **tutor_graph (Python)** | `state.py` 加 user_mode / references / last_cards | 0.3 |
|  | `evidence.py` CHAT 增加 last_cards / references | 0.5 |
|  | `nodes/chat.py` 改造（消费上下文 + LLM prompt） | 1.5 |
|  | `projection.py` 写 mode_when_produced + referenced_card_ids | 0.5 |
|  | `clients/java_tools_client.py` 加 get_last_cards / resolve_references | 0.5 |
| **前端 (Vue)** | `ModeBar.vue` + 9 chips + 启用规则 | 1.5 |
|  | `ChatComposer.vue` 增强（@面板 + 解析） | 2 |
|  | `useReferenceParse.js` composable | 0.5 |
|  | `CardJumpAnchor.vue` + smooth scroll | 1 |
|  | UnifiedAgentPanel 集成 | 1 |
| **集成 / E2E** | @引用 / Mode 切换 / 跨模式上下文 3 个 happy path E2E | 1.5 |
| **合计** | | **13.8 人日** |

---

## 十一、验收标准

### 11.1 单元测试（必过）

| 测试 | 验证点 |
|---|---|
| `ConversationContextServiceTest#listLastCardsRespectsLimit` | 只返指定 sessionId 的 N 张 |
| `ConversationContextServiceTest#switchModeRejectsInvalid` | Mode 不在 Phase 允许集合时 throw |
| `ReferenceResolverTest#parsesExplicitCardIds` | "@card:X" 被识别 |
| `ReferenceResolverTest#parsesShorthands` | "@last_error" 被识别 |
| `ReferenceResolverTest#dedupes` | 同一引用出现多次不重复 |
| `ReferenceResolverTest#sessionScoped` | 不返其他 session 的卡 |
| `ModeTest#allowedPhasesMatrixComplete` | 9 Mode × 7 Phase 矩阵完整 |
| `services/tutor-graph/tests/nodes/test_chat.py::test_consumes_last_cards` | last_cards 出现在 user_msg |
| `services/tutor-graph/tests/nodes/test_chat.py::test_consumes_references` | references 优先于 last_cards |
| `services/tutor-graph/tests/nodes/test_chat.py::test_no_references_works_normally` | 空 references 仍正常 |

### 11.2 集成测试（必过）

| 测试 | 验证点 |
|---|---|
| `UnifiedChatIntegrationTest#chatReferencesRecognizedByLlm` | mock LLM 见到 references；输出含 referenced_card_ids |
| `UnifiedChatIntegrationTest#switchModePersists` | API 后 ai_tutor_session.active_mode 更新 |
| `UnifiedChatIntegrationTest#switchModeRespectsPhaseConstraint` | READING phase 切 VISUALIZE 返回 422 |
| `UnifiedChatIntegrationTest#cardCreatedWithModeWhenProduced` | 节点产卡时 mode_when_produced 正确 |

### 11.3 前端单测（必过）

| 测试 | 验证点 |
|---|---|
| `ModeBar.spec.js#disabledModesGreyOut` | 不允许 Mode 灰显 |
| `ChatComposer.spec.js#atTriggersAutocomplete` | 输入 @ 弹出面板 |
| `useReferenceParse.spec.js#parsesMixedReferences` | 同时含 @card + @last_xxx |
| `CardJumpAnchor.spec.js#scrollsToTarget` | 点击后滚动到 anchor |

### 11.4 E2E 验收（必过）

| 场景 | 期望 |
|---|---|
| 学生在 Chat 输入 `@card:C-V-001 这图哪里错了` | LLM 回答精确引用 C-V-001 内容；前端可点击跳转 |
| 学生在 CODING 切 VISUALIZE Mode | 切换成功，ModeBar 高亮 VISUALIZE |
| 学生在 READING 切 AC_REVIEW Mode | 切换失败，提示 "当前阶段不支持此模式" |
| 学生 AC 后 Chat 问"刚才那张图能再讲讲吗" + `@last_visualize` | LLM 引用 VISUALIZE 卡；不重画 |

### 11.5 教学指标（上线后 4 周内观察）

| 指标 | 目标 |
|---|---|
| 跨模式会话率（一次会话切过 ≥ 2 种 Mode） | ≥ 30% 活跃学生 |
| @引用使用率 | ≥ 10% Chat 消息含引用 |
| Chat 卡片 LLM 误引用率（事后审计抽样） | < 5% |
| 平均会话时长 | 提升 ≥ 15% |

---

## 十二、风险与缓解

| # | 风险 | 概率 | 影响 | 缓解 |
|---|---|---|---|---|
| R1 | last_cards 注入导致 prompt 过长 | 中 | 中 | limit=5；每张 short_text ≤ 200 字；总 ≤ 1500 字 |
| R2 | 学生 @ 引用了不存在 / 跨 session 的 card_id | 中 | 低 | resolveReferences 严格校验 sessionId；不存在时 LLM 收到空 references |
| R3 | Mode 与 Phase 状态不一致（前端/后端不同步） | 中 | 中 | 切 Mode 后立即 SSE 推 active_mode；前端以服务端为准 |
| R4 | LLM 编造 referenced_card_ids | 中 | 中 | 后端校验 referenced_card_ids 必须在 last_cards/references 集合内，否则忽略 |
| R5 | @ 自动补全弹窗影响输入体验 | 低 | 低 | 用户可关；只在输入 @ 后等待 200ms 弹 |
| R6 | active_mode 持久化失败导致后续节点产卡 mode 缺失 | 低 | 中 | failfast：写入失败立即抛异常；不写默认值 |

---

## 十三、第一性原理自检

| 自检项 | 通过 |
|---|---|
| 不引入兼容性 / 补丁性方案 | ✅ Mode 是新概念，与 Phase 显式分层；不并存两套状态机 |
| 不过度设计 | ✅ 不引入"会话回滚 / 多人会话"；不重写 FSM |
| 不擅自扩展业务目标 | ✅ 仅围绕"上下文连续性 + 用户主动权" |
| 不写防御性 / 兜底逻辑 | ✅ Mode 切换失败 failfast；引用不存在不补默认 |
| 全链路逻辑验证 | ✅ 九、全链路时序 已覆盖 |
| 重命名全链路同步 | N/A |
| 不做与当前需求无关的兜底 | ✅ 不做"教师旁观模式"等扩展 |

---

## 附录 A：Mode 与 Phase / Card 映射

### Mode × Phase 允许矩阵

| Mode \ Phase | READING | IDEATING | CODING | ERROR_FEEDBACK | AC_REVIEW | TRANSFER | KNOWLEDGE_REVIEW |
|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| READING（导读） | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| IDEATING（思路） | ❌ | ✅ | ✅ | ✅ | ❌ | ❌ | ✅ |
| CODING（陪练） | ❌ | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ |
| ERROR_DIAGNOSIS（纠错） | ❌ | ❌ | ✅ | ✅ | ❌ | ❌ | ❌ |
| VISUALIZE（可视化） | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| AC_REVIEW（复盘） | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ | ❌ |
| TRANSFER（迁移） | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ | ❌ |
| KNOWLEDGE_REVIEW（复习） | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| CHAT（自由提问） | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |

### Mode → Capability → Card 映射

| Mode | 默认 Capability（节点） | 产 Card |
|---|---|---|
| READING | problem_guide_node | PROBLEM_GUIDE |
| IDEATING | ideate_node | IDEATE_ANALYSIS |
| CODING | coding_node | （无独立 Card；由学生提交触发其他流程） |
| ERROR_DIAGNOSIS | error_feedback_node | ERROR_DIAGNOSIS |
| VISUALIZE | dispatch_visualize（P2） | VISUALIZE |
| AC_REVIEW | ac_review_node | POST_AC |
| TRANSFER | transfer_node | TRANSFER_PROBLEM |
| KNOWLEDGE_REVIEW | knowledge_review_node | KNOWLEDGE_REVIEW |
| CHAT | chat_node | AI_REPLY |

---

## 附录 B：@引用语法

### 显式引用

```
@card:<card_id>
```

例：`@card:C-V-001`

### 简写引用

| 简写 | 含义 |
|---|---|
| `@last_error` | 最近一张 ERROR_DIAGNOSIS 卡 |
| `@last_visualize` | 最近一张 VISUALIZE 卡 |
| `@last_ideate` | 最近一张 IDEATE_ANALYSIS 卡 |
| `@last_guide` | 最近一张 PROBLEM_GUIDE 卡 |
| `@last_ac` | 最近一张 POST_AC 卡 |
| `@last_transfer` | 最近一张 TRANSFER_PROBLEM 卡 |
| `@all` | 最近 5 张（无类型限制） |

### 解析规则

- 同一消息可混用多种引用；
- 后端去重；
- 跨 session 引用一律忽略（不报错，但 references 为空）；
- 引用不存在时 LLM 收到空 references，并被 SYSTEM_PROMPT 约束"不要凭空编造没看到的卡片内容"。

### 边界

- 不支持模糊匹配（"@刚才那张图"会被忽略）；
- 不支持嵌套引用（一张卡引用另一张卡，前端通过 referenced_card_ids 已能跳转，无需在文本里递归 @）；
- 不支持负向引用（"忽略 @C-V-001"），如需切换上下文请切 Mode 或开新会话。

---

**设计完。**
