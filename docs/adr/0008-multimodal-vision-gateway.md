# ADR-0008: 暂缓接入多模态 Vision Gateway

- **Status**: Accepted
- **Date**: 2026-05-06
- **Authors**: Alethicode AI Agent
- **Stakeholders**: 前端 / 后端 / tutor-graph / model-gateway / SRE / 合规

## 背景

AI 导学助手与课件问答的对话输入区已经具备文本、`@` 引用、`/` 命令、草稿历史和上下文用量展示能力。学生粘贴报错截图、手算草稿、IDE 截图是高频诉求，但当前 Alethicode 的 LLM 链路仍以文本为主：

- `services/tutor-graph/app/clients/llm_client.py` 以文本消息为主要输入。
- Java 侧 model gateway / `AiModelGateway` 没有统一的 vision provider 配置。
- `tutor_session_message` / `language_pack_chat_message` 目前没有稳定的 attachments contract。

本 ADR 仅评估图片粘贴 / 拖拽接入，不在当前 sprint 实现。

## 约束

- 不能绕过现有鉴权与限流；图片上传必须按 user 维度计量。
- 不能把外部短期 URL、对象存储密钥或 provider 内部错误暴露给前端。
- 不能在没有统一模型能力抽象前，把 vision provider 选择写死到某个页面。
- 需要兼容现有文本-only tutor-graph 节点；图片能力不能破坏当前 `messages[*].content` 为 string 的路径。
- 上传内容需要 size / mime 白名单：仅 `jpeg` / `png` / `webp`，单张不超过 8MB，每用户每分钟最多 10 张。

## 选项分析

| 选项 | 优 | 劣 | 备注 |
|------|----|----|------|
| 第三方 vision API（如 GPT-4o / Claude Sonnet / Qwen-VL） | 图像语义理解最好，能直接处理截图 + 草稿 | 成本和延迟较高，provider 能力差异大，合规评审更复杂 | 需要 model-gateway 抽象 vision capabilities |
| 自建 OCR + 文本 LLM 两段式 | 成本低，能复用文本链路 | 丢失布局 / 图像语义，对手写草稿、复杂 IDE 截图效果差 | 可作为后续降级策略，但不应作为第一版主方案 |
| 暂不实现，仅保留接口形态 | 不扰动当前 Phase 1-3 对话能力收口 | 学生暂时无法贴图 | 当前 sprint 的选择 |

## 决策

暂缓实现图片粘贴 / 拖拽接入，先完成文本对话能力的闭环与稳定性修复。后续独立 sprint 再接入 vision gateway，并以以下接口形态作为约束：

1. 前端 `<el-input>` 监听 paste / drop。
2. 图片上传到 `POST /api/upload/inline-image`。
3. 后端返回短期可访问 URL 与元数据。
4. 消息体新增 `attachments` 数组：`[{ type: "image", url, mime, size, sha256 }]`。
5. model-gateway 将 `messages[*].content` 从 string 扩展为 text / image parts union。

## 后果

- 正面：当前 chat composer sprint 不引入 model-gateway、存储、合规、成本四条新风险线。
- 正面：后续实现时有明确接口形态，不需要倒推前端输入区设计。
- 负面：学生短期仍需手动描述截图内容，或把报错文本复制到输入框。
- 我们被绑定到：后续必须先完成 attachments 数据结构与 vision provider 抽象，再允许 UI 开启图片入口。

## 后续

- 新增 `attachments JSONB` 前，先完成数据库迁移 ADR / migration review。
- 在 model-gateway 中声明 provider capability：`text_only` / `vision_image`。
- 做一轮成本压测：单张 1MB / 4MB / 8MB 图片的平均延迟、失败率、token 账单。
- 上线前补充内容安全策略：敏感信息提示、文件生命周期、对象存储清理任务。
