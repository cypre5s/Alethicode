# ADR-0004: 中国大陆合规栈（PIPL / 生成式 AI 管理办法 / 等保 2.0）

- **Status**: Accepted
- **Date**: 2026-04-21

## 背景

Alethicode 的生产部署在中国大陆。相关法律法规：

- 《个人信息保护法》(PIPL, 2021-11-01)
- 《数据安全法》(DSL, 2021-09-01)
- 《网络安全法》(CSL, 2017-06-01)
- 《生成式人工智能服务管理暂行办法》(AIGC Interim Measures, 2023-08-15)
- 等保 2.0（GB/T 22239-2019）预期三级

## 约束

- 学生个人信息 / 学习行为不得跨境传输（PIPL 第 38 条）
- AI 生成内容必须可识别（AIGC 第 12 条）
- 使用生成式 AI 服务的记录需保存 ≥ 6 个月（AIGC 第 19 条）
- 数据处理审计 5 年保留（PIPL 第 55 条，DSL 第 27 条）
- 不使用在境内被阻挡或需要备案的 SaaS（Sentry 云版 / Google Analytics 等）

## 决策

### 数据本地化

- 所有 PII 表只在境内 Postgres 存储（阿里云 RDS / 腾讯云 CDB / 华为云 GaussDB 均可）
- LangGraph checkpointer Postgres 实例同样仅部署在境内
- 备份：OSS / COS 境内 region，禁止 Replica 到香港 / 海外

### LLM Provider

- **默认 chat**：**DeepSeek**（`deepseek-chat` 日常 / `deepseek-reasoner` 初始化 / 代码标答）
  - 2026-04-14 从 MiniMax 切过来，原因：MiniMax HTTP 529 过载频繁影响生产稳定性
- **Embedding**：阿里云 DashScope 兼容端点 `text-embedding-v4`
- **Failover 候选**（按使用频度）：通义千问（阿里云）/ 字节火山（豆包）/ MiniMax / 智谱
  - 所有备选都必须已在网信办完成算法备案 + 深度合成备案
- `spring.ai.openai.base-url` 默认 `https://api.deepseek.com/v1`；可通过 DB `sys_options.ai_provider_config` 或 env `LLM_BASE_URL` 切换
- Failover 顺序由 `ALETHICODE_AI_FALLBACK_PREFIXES` 环境变量控制（逗号分隔 profilePrefix）

### 数据主体权利（PIPL）

- 自助导出：`POST /api/privacy/data-exports`（返回 JSON 结构化数据）
- 删除请求：`DELETE /api/privacy/personal-data`（15 工作日响应 SLA）
- 访问审计：`pii_access_log` 表，5 年保留
- 管理员对学生数据的读 / 改 / 删都需经 `PiplDataSubjectService.recordAccess` 打点

### AI 内容标识 & 审计

- `AigcComplianceService.labelAiGeneratedContent(...)` 给所有前端可见的 AI 输出
  加"（以下内容由 AI 生成，仅供参考）"前缀
- `aigc_audit_log` 记录每次生成的 input/output 哈希 + 摘要 + 敏感标记，
  6 个月定期清理（`retention_expires_at`）
- 敏感内容扫描 `scanForSensitiveContent` 当前留接口；生产建议接阿里云内容安全、
  腾讯云天御或网易易盾（三者均有备案 + 国密算法支持）

### 供应链 / 等保

- 镜像：阿里云 ACR（`registry.cn-hangzhou.aliyuncs.com`）或腾讯云 TCR 托管
- pip / maven / npm 统一走阿里云镜像（见 `.github/workflows/ci.yml`）
- SBOM：`scripts/ops/generate_sbom.sh` 生成 CycloneDX JSON；每次发版归档 90 天以上
- Trivy fs / image 扫描在 CI 阻塞 CRITICAL / HIGH

### 网络 / 传输

- 境内 API 调用强制 TLS 1.2+（国密商密 SM2/SM3/SM4 作为后续升级项）
- 内部 Java ↔ tutor_graph 建议同 VPC 通信，长期路线是 mTLS（独立 ADR）

## 后果

**正面**
- PIPL 的"查阅权 / 删除权 / 审计"三项已代码化
- AIGC 的"标识 + 审计 + 扫描 hook"已代码化
- SBOM + 阿里云镜像 + dependabot 让软件供应链可审

**负面 / 绑定**
- LLM 调用绑定国产 provider；多云冗余需要各 provider 独立配置
- 6 个月 / 5 年的保留窗口消耗存储（预估每学生每月 ~5 KB）
- `scanForSensitiveContent` 接口是 no-op；生产必须选定一家内容安全供应商再上线

## 后续

- 选定内容安全供应商并实现 `AigcComplianceService.scanForSensitiveContent`
- 等保 2.0 三级备案启动（需定级 + 备案 + 测评）
- 网信办 AIGC 备案材料准备（模型供应商清单 + 算法备案）
