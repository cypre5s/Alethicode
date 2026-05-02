# Phase 2：RAG 治理与 QA Harness 升级

**适用界面**：AI 问答（独立界面）
**阶段属性**：业务能力建设（兼具 Harness 评测与回放）

## 目标

把当前课件 QA 从"能回答"升级为"可治理、可回放、可量化"。

## 实现流程

1. 在 QA 链路加入检索前处理层：
   - query normalize
   - query rewrite
   - query decomposition
   - reference resolution
2. 固定 QA 执行流：
   - 规范化问题
   - 改写/拆解
   - 首轮检索
   - 可选 ReAct 补检索
   - synthesis
   - grounding critic
3. 改造 `PageRetrievalService` 返回结构，从"只给 hits"升级为"hits + retrieval trace"。
4. 改造 `AnswerSynthesisService` 返回结构，从"答案对象"升级为"答案 + synthesis trace + critic verdict"。
5. 扩充 `QaEvalHarness`：
   - retrieval_eval
   - grounding_eval
   - answer_eval
   - refusal_eval
6. 建立 QA dataset：
   - 单页可答
   - 多页整合
   - 应拒答
   - 指代追问
   - 错误页码引用
   - 容易误召回的概念题
7. 加入 failure bucket：
   - `insufficient_evidence`
   - `conflicting_evidence`
   - `citation_mismatch`
   - `query_rewrite_regression`
   - `out_of_scope`
8. 新增回放入口：
   - 指定 sample 重新跑 QA 全链路
   - 输出 retrieval/synthesis/critic 全 trace
9. 在 QA 模块试点 Spring AI 通用 RAG 基建：
   - 评估 `Advisors`
   - 评估 `VectorStore`
   - 评估 `PGVector` 集成
   - 评估 `Evaluator`
10. 明确试点策略：
    - 只迁通用 RAG 基建
    - 不迁 QA 业务拒答协议
    - 不迁 grounded answer 业务结构
11. 对比两套实现：
    - 当前自研检索链
    - Spring AI 试点检索链
    - 输出召回、引用、拒答和延迟对比报告

## 主要落点

- `backend/src/main/java/com/alethicode/service/languagepack/PageRetrievalService.java`
- `backend/src/main/java/com/alethicode/service/languagepack/impl/PageRetrievalServiceImpl.java`
- `backend/src/main/java/com/alethicode/service/languagepack/AnswerSynthesisService.java`
- `backend/src/main/java/com/alethicode/service/languagepack/impl/AnswerSynthesisServiceImpl.java`
- `backend/src/main/java/com/alethicode/service/aitutor/eval/QaEvalHarness.java`

## 测试

- query rewrite 开关前后做同一批样本对比。
- QA 应拒答样本不能被错误回答。
- grounding critic 的拒答原因必须结构化。
- 回放结果与线上一次执行的阶段顺序一致。
- Spring AI RAG 试点必须和当前实现做同批数据集对比。

## 验收标准

- QA 报告能输出 `retrieval recall / grounding accuracy / refusal accuracy / citation precision`。
- 任一失败样本都可一键回放。
- query rewrite 是否有效能被量化，而不是只看体感。
- 课件 QA 从"模型效果问题"转化为"具体哪一层出问题"的工程问题。
- 团队能明确判断哪些 QA 通用 RAG 环节适合迁入 Spring AI，哪些必须保留自研。
