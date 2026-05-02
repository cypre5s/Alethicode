# Phase 1：Context Layering 与 Memory 升级

**适用界面**：以 AI 导学助手为主，AI 问答部分受益
**阶段属性**：业务能力建设（兼具 Harness 状态管理）

## 目标

把现有"最近会话 + learner memory"升级为统一的分层上下文系统，让导学与 QA 使用同一套上下文命名、装配和召回规则。

## 实现流程

1. 定义统一上下文层模型：
   - `runtime_context`
   - `session_context`
   - `retrieval_context`
   - `learner_memory`
   - `institutional_context`
   - `policy_context`
2. 扩展或重构上下文装配入口，以现有 `EvidencePackAssembler` 为主，不新起第二套并行系统。
3. 改造 QA 的 `ConversationContextService`：
   - 从纯文本拼接升级为结构化输出
   - 增加最近引用页缓存
   - 增加会话摘要字段
4. 改造 `LearnerMemoryService`：
   - 拆出 `MemoryCandidate`
   - 拆出 `MemorySaveDecision`
   - 拆出 `MemoryScope`
5. 将当前全量刷新写入逻辑改为事件驱动增量写入：
   - ERROR_FEEDBACK 完成后
   - AC_REVIEW 完成后
   - 用户显式纠正模型后
6. 新增记忆排序策略：
   - 相似度
   - 新鲜度
   - 置信度
   - 来源可靠性
   - 当前 phase 相关性
7. 对高价值记忆写入引入审批点：`confirm_memory_save`
8. 将最终上下文快照纳入 trace 日志。
9. 在 QA 模块试点 Spring AI 的 memory / advisor：
   - 优先用于会话级短期记忆
   - 不替代 learner long-term memory
   - 与现有 `ConversationContextService` 做并行对比
10. 明确记忆分层边界：
    - Spring AI `ChatMemory/Advisor` 只服务 QA 对话短期上下文
    - `LearnerMemoryService` 继续承载长期学习记忆和教学资产
11. 明确导学与 QA 的记忆使用边界：
    - 导学使用 learner long-term memory 作为教学核心上下文
    - QA 不得把 learner long-term memory 作为主回答证据

## 主要落点

- `backend/src/main/java/com/alethicode/service/aitutor/profile/LearnerMemoryService.java`
- `backend/src/main/java/com/alethicode/service/languagepack/impl/ConversationContextServiceImpl.java`
- `backend/src/main/java/com/alethicode/service/aitutor/evidence/EvidencePackAssembler.java`
- `backend/src/main/java/com/alethicode/service/aitutor/evidence/EvidencePack.java`

## 测试

- 新增记忆候选不会直接污染长期记忆。
- 同一用户追问时能正确继承最近证据页与摘要。
- 缺失关键上下文字段时直接失败。
- 记忆召回顺序可预测，不再只靠更新时间。
- Spring AI memory 试点与现有 QA 上下文机制在固定样本上可对比。

## 验收标准

- 导学与 QA 都能输出统一命名的 context snapshot。
- 记忆写入具有明确触发点与可解释状态。
- 用户纠正模型后，系统可以形成候选记忆而不是静默丢弃。
- 任何回答都能解释"用了哪些记忆层"。
- QA 短期记忆具备 Spring AI 试点结果，并能明确判断是否值得扩大范围。
