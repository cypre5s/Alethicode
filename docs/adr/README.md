# Architecture Decision Records (ADR)

本目录收录 Alethicode 的架构决策记录。每条 ADR 使用 `NNNN-短标题.md` 命名，编号永不复用。

## 为什么要写 ADR

大型重构（LangGraph 迁移、Spring AI 网关、AI Runtime 收口）会改变系统结构，
未来接手的工程师不能靠 commit message 理解"当时为什么这么选"。ADR 记录：

- **背景**（我们面对什么问题）
- **约束**（有哪些不能动的前提）
- **决策**（最终选了哪条路）
- **后果**（这个决策把我们绑定到什么）

## 何时写一条 ADR

- 新增或替换**跨模块的**第三方服务（LangGraph / Spring AI / Redis）
- 引入或废弃一类 API（例如 `/api/ai/workflow/*` 删除）
- 采用或放弃一个合规标准（PIPL、等保、GDPR）
- 放弃一条显然的设计路径（例如"不用 LangGraph4j"要写 ADR）

## 状态流转

```
Proposed → Accepted → (Deprecated | Superseded by NNNN)
```

Deprecated 的 ADR 保留原文，顶部加 `> Status: Deprecated — replaced by ADR-XXXX`。

## 格式

见 [`0000-template.md`](0000-template.md)。正文用中文，关键术语保留英文以便搜索。
