# AI 导学助手 / AI 问答 边界澄清最小重构清单

## 目标

在 **不损失现有功能**、**不改业务路径**、**不引入兼容性分支** 的前提下，把项目中 `AI 导学助手` 与 `AI 问答` 的边界表达得更清楚。

本清单只做一件事：

- 让代码结构、命名、文档、评测维度更容易看出“两者不是一回事”

本清单明确 **不做**：

- 不重写现有 AI 主骨架
- 不改变 AI 导学助手的 FSM / phase / event 语义
- 不改变 AI 问答的 grounded QA 协议
- 不把两条链路重新抽象成一个统一业务流
- 不新增兜底和降级路径

---

## 当前判断

当前项目 **没有把两者真正混为一谈**，但存在少量“基础设施层看起来像混在一起”的点。

### 已经分开的部分

1. 前端入口分开
   - 做题页 AI 导学助手走题目页工作流
   - AI 问答走独立 `language-pack-qa` 页面

2. 后端接口分开
   - AI 导学助手走 `/api/ai/workflow/*`
   - AI 问答走 `/api/language-pack-qa/*`

3. 会话存储分开
   - AI 导学助手围绕 workflow session / checkpoint / interrupt
   - AI 问答围绕 language pack chat session / message

4. 业务目标分开
   - AI 导学助手负责做题过程中的教学干预
   - AI 问答负责课件证据问答与引用

### 当前最明显的不清晰点

1. `TutorToolRegistry` 同时承载导学工具和 QA 工具  
   这会让代码阅读者误以为 QA 是 Tutor 的子模块。

2. 评测、trace、文档中对“两者共用基础设施”和“两者业务隔离”的说明还不够硬

3. 部分命名仍偏向 `Tutor` 主视角，容易让 QA 看起来像附属能力而不是独立产品面

---

## 重构原则

1. 只重构边界表达，不重构业务逻辑
2. 只做最短路径拆分，不做抽象层过度设计
3. fail-fast，禁止“先兼容旧命名再慢慢迁”
4. 所有重命名必须全链路同步，不保留并行旧路径
5. 共用基础设施允许存在，但必须显式标注为“基础设施共用”，不能伪装成业务统一

---

## 最小重构清单

### 1. 拆分工具注册中心

### 目标

把“导学工具”和“QA 工具”从同一个注册中心中拆开，避免业务域混淆。

### 当前问题

当前 `TutorToolRegistry` 同时包含：

- `search_courseware`
- `search_similar_errors`
- `get_learner_history`
- `search_language_pack_pages`

其中前三个属于 AI 导学助手，最后一个属于 AI 问答。

### 实施动作

1. 新建 `TutorToolRegistry`
   - 只保留 AI 导学助手工具

2. 新建 `LanguagePackQaToolRegistry`
   - 只保留 `search_language_pack_pages`

3. 更新调用方
   - `DiagnosticsAgent`
   - `AITutorWorkflowAdminServiceImpl`
   - `AnswerSynthesisServiceImpl`

4. 删除旧的混合工具定义入口

### 验收标准

1. 任意读代码的人都能一眼看出：
   - 导学工具属于 Tutor 域
   - QA 工具属于 Language Pack QA 域

2. QA 代码不再从 `TutorToolRegistry` 取工具

3. 导学工具域与 QA 工具域不存在交叉注册

---

### 2. 显式声明两套上下文来源

### 目标

让上下文分层在代码和文档层面都清楚，不再依赖“读实现猜语义”。

### 实施动作

1. 在 `todo_agent.md` 和正式设计文档中固定两套主上下文：
   - AI 导学助手：
     - problem context
     - submission context
     - workflow context
     - learner memory
   - AI 问答：
     - language pack session context
     - retrieval context
     - citations / evidence pages

2. 明确禁止项：
   - AI 问答不得把 learner long-term memory 作为主回答依据
   - AI 导学助手不得把 grounded citation 作为主输出协议

3. 在 QA 相关类注释中强化说明：
   - `ConversationContextService`
   - `PageRetrievalService`
   - `AnswerSynthesisService`

### 验收标准

1. 导学和 QA 的主上下文来源在文档中分别独立列出
2. 新成员只看文档即可理解两者上下文边界
3. QA 代码注释里明确声明“这是 retrieval-grounded assistant，不是 tutor workflow”

---

### 3. 显式声明两套评测维度

### 目标

防止后续把两套系统用同一把尺子评估，导致边界再次变糊。

### 实施动作

1. 在文档中拆开两类评测：
   - AI 导学助手评测：
     - pedagogy fit
     - action appropriateness
     - scaffold quality
     - answer leakage
     - interruption safety
   - AI 问答评测：
     - retrieval recall
     - grounding accuracy
     - refusal accuracy
     - citation precision

2. 在 Harness 规划中标明：
   - 可以共用 trace 框架
   - 不可以共用核心 grading rubric

3. 明确 `TutorEvalHarness` 与 `QaEvalHarness` 是同层级并列模块，不是父子关系

### 验收标准

1. 文档里不再出现“统一质量指标”这种模糊说法
2. 导学评测和 QA 评测在结构上并列呈现
3. 评审能直接看出两者成功标准不同

---

### 4. 清理命名中的主从错觉

### 目标

避免命名让人形成“QA 是 Tutor 子能力”的误解。

### 实施动作

1. 保留共用基础设施层的中性命名：
   - `LlmClient`
   - trace / eval / observability adapter

2. 避免 QA 继续使用带 `Tutor` 语义的类名入口

3. 后续新增 QA 专属组件时统一放在：
   - `service.languagepack`
   - 或 `service.languagepack.qa`

4. 后续新增导学专属组件时统一放在：
   - `service.aitutor`

### 验收标准

1. QA 主链路上不再依赖 `Tutor` 语义命名的业务组件
2. 新增类的包路径能直接体现所属域
3. 共用层和业务层命名不再混淆

---

### 5. 固化“共用基础设施 vs 业务隔离”说明

### 目标

让老师、评审、后续开发者都能快速理解：
两者不是混在一起，而是“业务隔离 + 基础设施共用”。

### 实施动作

1. 在 `todo_agent.md` 中保留并强化以下结论：
   - AI 导学助手 与 AI 问答 是两个业务面
   - 它们共享模型调用、trace、observability、部分 tool/use 基建
   - 它们不共享业务协议、上下文来源、评测目标

2. 在后续正式文档中增加固定小节：
   - `业务边界`
   - `基础设施共用边界`
   - `禁止交叉污染`

3. 在答辩材料里固定一句话：
   - “两者不是一个 Agent 的两种模式，而是两个业务域，共享底层 AI 基础设施。”

### 验收标准

1. 文档、代码组织、答辩表达三处结论一致
2. 不再出现“QA 是 Tutor 的一个页面”这种误读
3. 评审能快速理解为什么两者要分开设计

---

## 推荐执行顺序

1. 拆分工具注册中心
2. 固定上下文边界与禁止项
3. 固定评测边界
4. 清理命名主从错觉
5. 同步到正式设计文档和答辩表达

---

## 最终验收口径

当以下问题都能被直接回答时，说明边界已经足够清晰：

1. AI 导学助手和 AI 问答是不是同一个业务流？
   - 不是

2. 它们有没有共用底层能力？
   - 有，但只共用基础设施

3. QA 为什么不能直接复用导学 workflow？
   - 因为目标是 grounded retrieval QA，不是教学 phase orchestration

4. 导学为什么不能退化成课件聊天？
   - 因为目标是做题教学干预，不是独立知识问答

5. 从代码结构上能不能直接看出这种区别？
   - 可以

---

## 一句话总结

这份清单的目标不是“把两个系统拆开重做”，而是 **把已经存在的业务边界，在代码结构、命名、文档和评测中显式写出来**。
